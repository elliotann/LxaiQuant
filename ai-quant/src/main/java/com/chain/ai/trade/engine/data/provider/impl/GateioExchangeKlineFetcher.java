package com.chain.ai.trade.engine.data.provider.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.provider.ExchangeKlineFetcher;
import com.chain.ai.trade.common.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Gate.io 公开 API 拉取历史K线并转换为 Candlestick
 * 同时支持 Spot 现货、Futures 合约和 TradFi(外汇/CFD) K线 API，通过 symbol 格式自动识别：
 * - 纯字母（如 EURUSD、XAGUSD）→ TradFi API
 * - 含合约后缀（如 BTC-USDT-SWAP）→ Futures API
 * - 其他含分隔符（如 BTC-USDT）→ Spot API
 */
@Slf4j
@Component
public class GateioExchangeKlineFetcher implements ExchangeKlineFetcher {

    /** Gate.io Spot 现货 K 线 API */
    private static final String GATEIO_CANDLESTICKS_URL = "https://api.gateio.ws/api/v4/spot/candlesticks";
    /** Gate.io TradFi(外汇/CFD) K 线 API */
    private static final String GATEIO_TRADFI_KLINES_URL = "https://api.gateio.ws/api/v4/tradfi/symbols";
    /** Gate.io Futures 合约 K 线 API（{settle} 为结算币种，如 usdt、btc） */
    private static final String GATEIO_FUTURES_CANDLESTICKS_URL = "https://api.gateio.ws/api/v4/futures";

    /** TradFi 品种正则：纯字母交易对，如 EURUSD、XAGUSD */
    private static final String TRADFI_SYMBOL_PATTERN = "^[A-Za-z]{4,12}$";
    /** 合约品种正则：含 -SWAP/-PERP/-FUTURES/-PERPETUAL 后缀 */
    private static final String CONTRACT_SYMBOL_PATTERN = ".*-(SWAP|PERP|FUTURES|PERPETUAL)(\\b.*)?$";

    /** 通用interval → OKX前缀interval映射，入库统一用OKX前缀格式，与OKX数据保持一致 */
    private static final Map<Integer, CandlestickIntervalEnum> GENERIC_TO_OKX_INTERVAL;

    static {
        Map<Integer, CandlestickIntervalEnum> map = new HashMap<>();
        map.put(1, CandlestickIntervalEnum.OKXMIN1);
        map.put(3, CandlestickIntervalEnum.OKXMIN3);
        map.put(5, CandlestickIntervalEnum.OKXMIN5);
        map.put(15, CandlestickIntervalEnum.OKXMIN15);
        map.put(30, CandlestickIntervalEnum.OKXMIN30);
        map.put(60, CandlestickIntervalEnum.OKXMIN60);
        map.put(240, CandlestickIntervalEnum.OKX4HOUR);
        map.put(1440, CandlestickIntervalEnum.OKX1D);
        GENERIC_TO_OKX_INTERVAL = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean supports(String exchange) {
        return Exchange.GATEIO.name().equalsIgnoreCase(exchange);
    }

    @Override
    public List<Candlestick> fetchKlines(String exchange, String symbol, CandlestickIntervalEnum interval,
                                         long startTimeSec, long endTimeSec, int limit) {
        if (!Exchange.GATEIO.name().equalsIgnoreCase(exchange)) {
            return Collections.emptyList();
        }
        // 根据 symbol 格式自动识别走 Futures / TradFi / Spot API
        if (isContractSymbol(symbol)) {
            return fetchFuturesKlines(symbol, interval, startTimeSec, endTimeSec, limit);
        }
        if (isTradfiSymbol(symbol)) {
            return fetchTradfiKlines(symbol, interval, startTimeSec, endTimeSec, limit);
        }
        return fetchSpotKlines(symbol, interval, startTimeSec, endTimeSec, limit);
    }

    // ========== Spot 现货 API ==========

    private List<Candlestick> fetchSpotKlines(String symbol, CandlestickIntervalEnum interval,
                                               long startTimeSec, long endTimeSec, int limit) {
        String currencyPair = symbol.replace("-", "_");
        String bar = mapIntervalToGateioBar(interval);
        int requestLimit = Math.min(limit, 1000);
        long intervalSec = interval != null && interval.getMinNum() != null ? interval.getMinNum() * 60L : 60L;
        long maxRangeSec = requestLimit * intervalSec;
        long actualFrom = Math.max(startTimeSec, endTimeSec - maxRangeSec);
        StringBuilder url = new StringBuilder(GATEIO_CANDLESTICKS_URL)
                .append("?currency_pair=").append(currencyPair)
                .append("&interval=").append(bar)
                .append("&limit=").append(requestLimit)
                .append("&from=").append(actualFrom)
                .append("&to=").append(endTimeSec);
        try {
            log.debug("Gate.io spot candlesticks: currency_pair={}, interval={}, from={}, to={}", currencyPair, bar, actualFrom, endTimeSec);
            String response = HttpUtil.get(url.toString());
            JSONArray data = JSONUtil.parseArray(response);
            if (data == null || data.isEmpty()) {
                return Collections.emptyList();
            }
            List<Candlestick> list = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JSONArray row = data.getJSONArray(i);
                Candlestick c = parseGateioSpotRow(row, symbol, interval);
                if (c != null) {
                    list.add(c);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("拉取 Gate.io Spot K线失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Gate.io Spot 返回: [timestamp(seconds), volume, close, high, low, open, quote_volume]
     */
    private static Candlestick parseGateioSpotRow(JSONArray row, String symbol, CandlestickIntervalEnum interval) {
        if (row == null || row.size() < 7) {
            return null;
        }
        String tsStr = row.getStr(0);
        long tsSec = Long.parseLong(tsStr);
        long tsMs = tsSec * 1000;
        String timeStr = DateUtil.longConvertDateTime(tsMs);
        BigDecimal volume = new BigDecimal(row.getStr(1));
        BigDecimal close = new BigDecimal(row.getStr(2));
        BigDecimal high = new BigDecimal(row.getStr(3));
        BigDecimal low = new BigDecimal(row.getStr(4));
        BigDecimal open = new BigDecimal(row.getStr(5));
        BigDecimal quoteVolume = new BigDecimal(row.getStr(6));
        return Candlestick.builder()
                .id(tsMs)
                .symbol(symbol)
                .marketType(MarketType.CRYPTO)
                .exchange(Exchange.GATEIO)
                .candlestickIntervalEnum(interval)
                .timeStr(timeStr)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .amount(quoteVolume)
                .count(BigDecimal.ZERO)
                .confirm("1")
                .build();
    }

    // ========== TradFi 外汇/CFD API ==========

    private List<Candlestick> fetchTradfiKlines(String symbol, CandlestickIntervalEnum interval,
                                                 long startTimeSec, long endTimeSec, int limit) {
        // 统一转成OKX前缀interval入库，与OKX数据格式一致，前端查询无需额外适配
        CandlestickIntervalEnum okxInterval = toOkxInterval(interval);
        String klineType = mapIntervalToTradfiBar(interval);
        int requestLimit = Math.min(limit, 500);
        StringBuilder url = new StringBuilder(GATEIO_TRADFI_KLINES_URL)
                .append("/").append(symbol)
                .append("/klines")
                .append("?kline_type=").append(klineType);
        // limit 和 from/to 互斥，不能同时出现
        if (startTimeSec <= 0) {
            url.append("&limit=").append(requestLimit);
        } else {
            url.append("&from=").append(startTimeSec);
            if (endTimeSec > 0) {
                url.append("&to=").append(endTimeSec);
            }
        }
        try {
            log.debug("Gate.io tradfi klines: symbol={}, kline_type={}, begin={}, end={}, limit={}",
                    symbol, klineType, startTimeSec, endTimeSec, requestLimit);
            String response = HttpUtil.get(url.toString());
            JSONObject json = JSONUtil.parseObj(response);
            JSONObject data = json.getJSONObject("data");
            if (data == null) {
                log.warn("Gate.io TradFi K线返回空: url={}, response={}", url, response);
                return Collections.emptyList();
            }
            JSONArray list = data.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                log.warn("Gate.io TradFi K线返回空列表: url={}, response={}", url, response);
                return Collections.emptyList();
            }
            List<Candlestick> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                Candlestick c = parseTradfiRow(item, symbol, okxInterval);
                if (c != null) {
                    result.add(c);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("拉取 Gate.io TradFi K线失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Gate.io TradFi 返回: { "o": open, "c": close, "h": high, "l": low, "t": timestamp(seconds) }
     * TradFi API 不返回 volume/amount，设为零
     */
    private static Candlestick parseTradfiRow(JSONObject item, String symbol, CandlestickIntervalEnum interval) {
        if (item == null) {
            return null;
        }
        Long tsSec = item.getLong("t");
        if (tsSec == null) {
            return null;
        }
        long tsMs = tsSec * 1000;
        String timeStr = DateUtil.longConvertDateTime(tsMs);
        BigDecimal open = new BigDecimal(item.getStr("o"));
        BigDecimal close = new BigDecimal(item.getStr("c"));
        BigDecimal high = new BigDecimal(item.getStr("h"));
        BigDecimal low = new BigDecimal(item.getStr("l"));
        return Candlestick.builder()
                .id(tsMs)
                .symbol(symbol)
                .marketType(MarketType.FOREX)
                .exchange(Exchange.GATEIO)
                .candlestickIntervalEnum(interval)
                .timeStr(timeStr)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .count(BigDecimal.ZERO)
                .confirm("1")
                .build();
    }

    // ========== Futures 合约 API ==========

    private List<Candlestick> fetchFuturesKlines(String symbol, CandlestickIntervalEnum interval,
                                                  long startTimeSec, long endTimeSec, int limit) {
        String settle = extractSettle(symbol);
        String contract = toGateioContractName(symbol);
        String bar = mapIntervalToGateioBar(interval);
        int requestLimit = Math.min(limit, 1000);
        // Futures API 不允许 limit 与 from/to 同时存在，用 to(游标) + limit 实现分页（类似 OKX after 方式）
        StringBuilder url = new StringBuilder(GATEIO_FUTURES_CANDLESTICKS_URL)
                .append("/").append(settle).append("/candlesticks")
                .append("?contract=").append(contract)
                .append("&interval=").append(bar)
                .append("&limit=").append(requestLimit);
        if (endTimeSec > 0) {
            url.append("&to=").append(endTimeSec);
        }
        try {
            log.debug("Gate.io futures candlesticks: contract={}, settle={}, interval={}, to={}",
                    symbol, settle, bar, endTimeSec);
            String response = HttpUtil.get(url.toString());
            // Futures API 成功返回 JSONArray，错误返回 JSONObject（含 label/message）
            Object parsed = JSONUtil.parse(response);
            if (parsed instanceof JSONObject) {
                JSONObject err = (JSONObject) parsed;
                log.warn("Gate.io Futures API 错误: {} - {}", err.getStr("label"), err.getStr("message"));
                return Collections.emptyList();
            }
            JSONArray data = (JSONArray) parsed;
            if (data.isEmpty()) {
                return Collections.emptyList();
            }
            List<Candlestick> list = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JSONObject row = data.getJSONObject(i);
                Candlestick c = parseGateioFuturesRow(row, symbol, interval);
                if (c != null) {
                    // 过滤掉比 startTimeSec 更早的数据（OKX 同样方式）
                    Long id = c.getId();
                    if (startTimeSec > 0 && id != null && (id / 1000) < startTimeSec) {
                        continue;
                    }
                    list.add(c);
                }
            }
            return list;
        } catch (Exception e) {
            log.error("拉取 Gate.io Futures K线失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Gate.io Futures 返回: {"t":timestamp(秒),"o":open,"c":close,"h":high,"l":low,"v":volume,"sum":...}
     */
    private static Candlestick parseGateioFuturesRow(JSONObject row, String symbol, CandlestickIntervalEnum interval) {
        if (row == null) {
            return null;
        }
        long tsSec = row.getLong("t", 0L);
        long tsMs = tsSec * 1000;
        String timeStr = DateUtil.longConvertDateTime(tsMs);
        BigDecimal open = new BigDecimal(row.getStr("o"));
        BigDecimal high = new BigDecimal(row.getStr("h"));
        BigDecimal low = new BigDecimal(row.getStr("l"));
        BigDecimal close = new BigDecimal(row.getStr("c"));
        BigDecimal volume = new BigDecimal(row.getStr("v"));
        return Candlestick.builder()
                .id(tsMs)
                .symbol(symbol)
                .marketType(MarketType.CRYPTO)
                .exchange(Exchange.GATEIO)
                .candlestickIntervalEnum(interval)
                .timeStr(timeStr)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .amount(BigDecimal.ZERO)
                .count(BigDecimal.ZERO)
                .confirm("1")
                .build();
    }

    // ========== 工具方法 ==========

    /**
     * 判断是否为 TradFi 品种（纯字母，不含分隔符）
     */
    private static boolean isTradfiSymbol(String symbol) {
        return symbol != null && symbol.matches(TRADFI_SYMBOL_PATTERN);
    }

    /**
     * 判断是否为合约品种（含 -SWAP/-PERP/-FUTURES/-PERPETUAL 后缀）
     */
    private static boolean isContractSymbol(String symbol) {
        return symbol != null && symbol.matches(CONTRACT_SYMBOL_PATTERN);
    }

    /**
     * 从合约 symbol 中提取结算币种（settle），如 BTC-USDT-SWAP → usdt
     */
    private static String extractSettle(String symbol) {
        if (symbol == null) return "usdt";
        String[] parts = symbol.split("-");
        if (parts.length >= 3) {
            // 第二位为结算币种，如 BTC-USDT-SWAP 中的 USDT
            return parts[1].toLowerCase();
        }
        return "usdt";
    }

    /**
     * 将通用 symbol 转为 Gate.io 合约名（去除 -SWAP 后缀，分隔符转 _）
     * 如 BTC-USDT-SWAP → BTC_USDT
     */
    private static String toGateioContractName(String symbol) {
        if (symbol == null) return null;
        String cleaned = symbol.replaceAll("-(SWAP|PERP|FUTURES|PERPETUAL)(\\b.*)?$", "");
        return cleaned.replace("-", "_");
    }

    /**
     * 通用interval → OKX前缀interval，入库统一格式
     */
    private static CandlestickIntervalEnum toOkxInterval(CandlestickIntervalEnum interval) {
        if (interval == null) return null;
        CandlestickIntervalEnum okx = GENERIC_TO_OKX_INTERVAL.get(interval.getMinNum());
        return okx != null ? okx : interval;
    }

    private static String mapIntervalToGateioBar(CandlestickIntervalEnum interval) {
        if (interval == null) {
            return "1m";
        }
        Integer minNum = interval.getMinNum();
        if (minNum == null) {
            return "1m";
        }
        if (minNum < 60) {
            return minNum + "m";
        }
        if (minNum == 60) {
            return "1h";
        }
        if (minNum < 1440) {
            return (minNum / 60) + "h";
        }
        if (minNum == 1440) {
            return "1d";
        }
        return "1m";
    }

    /**
     * TradFi 支持的 kline_type: 1m, 15m, 1h, 4h, 1d, 7d, 30d
     */
    private static String mapIntervalToTradfiBar(CandlestickIntervalEnum interval) {
        if (interval == null) {
            return "1m";
        }
        Integer minNum = interval.getMinNum();
        if (minNum == null) {
            return "1m";
        }
        if (minNum <= 1) return "1m";
        if (minNum <= 15) return "15m";
        if (minNum <= 60) return "1h";
        if (minNum <= 240) return "4h";
        if (minNum <= 1440) return "1d";
        if (minNum <= 10080) return "7d";
        return "30d";
    }
}
