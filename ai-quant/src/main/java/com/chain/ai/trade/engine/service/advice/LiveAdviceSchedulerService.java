package com.chain.ai.trade.engine.service.advice;

import com.chain.ai.trade.engine.config.ScheduledLiveAdviceProperties;
import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAdviceSchedulerService {

    private final ScheduledLiveAdviceProperties properties;
    private final ScheduledAdviceService scheduledAdviceService;

    public ScheduledResult runScheduledAnalysis() {
        if (!properties.isEnabled()) {
            log.info("定时AI分析已禁用，跳过执行");
            return new ScheduledResult(false, "定时AI分析已禁用");
        }

        List<ScheduledLiveAdviceProperties.SymbolConfig> symbols = properties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.warn("定时AI分析未配置交易对，跳过执行");
            return new ScheduledResult(false, "未配置交易对");
        }

        List<String> successSymbols = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();

        for (ScheduledLiveAdviceProperties.SymbolConfig config : symbols) {
            String symbol = config.getSymbol();
            String accountId = config.getAccountId();
            String interval = config.getInterval() != null ? config.getInterval() : "3m";

            log.info("开始定时AI分析: symbol={}, accountId={}, interval={}, leverage={}",
                    symbol, accountId, interval, config.getLeverage());
            try {
                TechnicalSignalDTO signal = scheduledAdviceService.generateForSymbol(
                        symbol, accountId, interval, config.getLeverage());

                log.info("定时AI分析成功: symbol={}", symbol);
                successSymbols.add(symbol);
            } catch (Exception e) {
                log.error("定时AI分析异常: symbol={}", symbol, e);
                failedSymbols.add(symbol);
            }

            sleepBetweenSymbols();
        }

        String summary = String.format("定时AI分析完成: 成功=%d, 失败=%d, 总=%d",
                successSymbols.size(), failedSymbols.size(), symbols.size());
        log.info(summary);

        return new ScheduledResult(true, summary, successSymbols, failedSymbols);
    }

    private void sleepBetweenSymbols() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ScheduledResult(boolean executed, String message,
                                  List<String> successSymbols, List<String> failedSymbols) {
        public ScheduledResult(boolean executed, String message) {
            this(executed, message, List.of(), List.of());
        }

        public int getSuccessCount() {
            return successSymbols != null ? successSymbols.size() : 0;
        }

        public int getFailedCount() {
            return failedSymbols != null ? failedSymbols.size() : 0;
        }
    }
}
