package com.chain.ai.trade.engine.task.jobhandler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.provider.ExchangeKlineFetcher;
import com.chain.ai.trade.engine.data.provider.ExchangeKlineFetcherFactory;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.engine.xchange.factory.ExchangeWrapFactory;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class KlineCollectorTask {

    private static final int POOL_SIZE = 20;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE);

    @Autowired
    private ICandlestickService candlestickService;

    @Autowired
    private ExchangeKlineFetcherFactory klineFetcherFactory;

    @XxlJob("klineCollector")
    public void execute() {
        doExecute();
    }

    public void doExecute() {
        long startTime = System.currentTimeMillis();
        String param = XxlJobHelper.getJobParam();
        JSONObject params = null;
        if (StringUtils.isNotEmpty(param)) {
            params = JSONUtil.parseObj(param);
        }
        if (params == null || StringUtils.isBlank(params.getStr("symbols"))) {
            log.warn("缺少必要参数symbols，任务退出");
            return;
        }

        Exchange exchange = params.getEnum(Exchange.class, "exchange");
        if (exchange == null) {
            exchange = Exchange.OKX;
            log.debug("未指定exchange，使用默认值: {}", exchange);
        }
        final Exchange finalExchange = exchange;

        ExchangeTradeService exchangeService = null;
        Optional<ExchangeKlineFetcher> klineFetcher = null;
        try {
            exchangeService = ExchangeWrapFactory.createNoAuthExchangeTradeService(exchange);
            klineFetcher = Optional.empty();
        } catch (UnsupportedOperationException e) {
            // GATEIO 等交易所没有 ExchangeTradeService 实现，回退到 ExchangeKlineFetcher
            klineFetcher = klineFetcherFactory.getFetcher(exchange.name());
            if (klineFetcher.isEmpty()) {
                log.error("交易所 {} 没有可用的 Kline 数据获取器，任务退出", exchange);
                return;
            }
        }

        final boolean useFetcher = klineFetcher.isPresent();
        final ExchangeKlineFetcher fetcher = klineFetcher.orElse(null);
        final ExchangeTradeService finalExchangeService = exchangeService;
        CandlestickIntervalEnum interval = CandlestickIntervalEnum.fromCode(params.getStr("interval"));
        if (interval == null) {
            log.warn("缺少必要参数interval，任务退出");
            return;
        }
        Integer size = params.getInt("size");

        String[] rawSymbols = params.getStr("symbols").split(",");
        log.info("开始处理{} {} K线数据，共{}个交易对{}", exchange, interval, rawSymbols.length,
                useFetcher ? "（使用 KlineFetcher 模式）" : "");

        List<CompletableFuture<List<Candlestick>>> futures = new ArrayList<>(rawSymbols.length);
        for (String rawSymbol : rawSymbols) {
            String symbol = rawSymbol.trim();
            if (StringUtils.isBlank(symbol)) {
                continue;
            }
            if (useFetcher) {
                String exchangeName = finalExchange.name();
                futures.add(CompletableFuture.supplyAsync(
                        () -> processSymbolViaFetcher(exchangeName, symbol, interval, size, fetcher), EXECUTOR));
            } else {
                futures.add(CompletableFuture.supplyAsync(
                        () -> processSymbol(symbol, finalExchange, interval, size, finalExchangeService), EXECUTOR));
            }
        }

        List<Candlestick> allCandlesticks = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        int totalCandles = 0;
        for (CompletableFuture<List<Candlestick>> future : futures) {
            try {
                List<Candlestick> result = future.join();
                if (result == null || result.isEmpty()) {
                    continue;
                }
                allCandlesticks.addAll(result);
                successCount++;
                totalCandles += result.size();
            } catch (Exception e) {
                failCount++;
                log.error("处理K线数据异常: {}", e.getMessage());
            }
        }

        if (!allCandlesticks.isEmpty()) {
            candlestickService.batchSave(allCandlesticks);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("{} {} K线数据处理完成，成功{}个，失败{}个，共{}条，耗时{}ms",
                exchange, interval, successCount, failCount, totalCandles, cost);
    }

    private List<Candlestick> processSymbol(String symbol, Exchange exchange, CandlestickIntervalEnum interval,
                                            Integer size, ExchangeTradeService exchangeService) {
        CandlestickRequest request = CandlestickRequest.builder()
                .symbol(symbol)
                .marketType(exchange.getMarketType())
                .exchange(exchange)
                .interval(interval)
                .size(size)
                .build();

        List<Candlestick> result = fetchWithRetry(symbol, request, exchangeService, 2);
        if (result == null || result.isEmpty()) {
            log.warn("未获取到{}的K线数据", symbol);
            return null;
        }
        log.debug("{} 获取K线数据成功，共{}条", symbol, result.size());
        return result;
    }

    private List<Candlestick> fetchWithRetry(String symbol, CandlestickRequest request,
                                             ExchangeTradeService exchangeService, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return exchangeService.getCandlestick(request);
            } catch (Exception e) {
                if (i < maxRetries && isRetryable(e)) {
                    log.warn("获取{} K线数据失败(第{}次)，即将重试: {}", symbol, i + 1, e.getMessage());
                    try {
                        Thread.sleep(1000L * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    log.error("获取{} K线数据失败: {}", symbol, e.getMessage(), e);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 通过 ExchangeKlineFetcher 获取 K 线数据（用于 GATEIO 等不支持 ExchangeTradeService 的交易所）
     */
    private List<Candlestick> processSymbolViaFetcher(String exchangeName, String symbol, CandlestickIntervalEnum interval,
                                                      Integer size, ExchangeKlineFetcher fetcher) {
        try {
            long nowSec = System.currentTimeMillis() / 1000;
            int count = size != null ? size : 300;
            long intervalSec = interval.getMinNum() * 60L;
            long startSec = nowSec - (count * intervalSec);
            List<Candlestick> result = fetcher.fetchKlines(exchangeName, symbol, interval, startSec, nowSec, count);
            if (result == null || result.isEmpty()) {
                log.warn("未获取到{}的K线数据", symbol);
                return null;
            }
            log.debug("{} 获取K线数据成功，共{}条", symbol, result.size());
            return result;
        } catch (Exception e) {
            log.error("获取{} K线数据失败: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    private boolean isRetryable(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("rate limit") || lower.contains("too many requests")
                || lower.contains("connection reset") || lower.contains("connect")
                || lower.contains("read timed out") || lower.contains("socket");
    }
}
