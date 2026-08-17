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
import java.util.List;

/**
 * 从 OKX 公开 API 拉取历史K线并转换为 Candlestick
 * 数据导入统一走 history-candles，不按时间判断
 */
@Slf4j
@Component
public class OkxExchangeKlineFetcher implements ExchangeKlineFetcher {

    /** 历史 K 线，limit 最多 100，after/before 毫秒时间戳 */
    private static final String OKX_HISTORY_CANDLES_URL = "https://www.okx.com/api/v5/market/history-candles";

    @Override
    public boolean supports(String exchange) {
        return "OKX".equalsIgnoreCase(exchange);
    }

    @Override
    public List<Candlestick> fetchKlines(String exchange, String symbol, CandlestickIntervalEnum interval,
                                         long startTimeSec, long endTimeSec, int limit) {
        if (!"OKX".equalsIgnoreCase(exchange)) {
            return Collections.emptyList();
        }
        String bar = mapIntervalToOkxBar(interval);
        // OKX after=ts 表示返回 ts 之前(更早)的数据；这里将 endTimeSec 作为分页游标（cursor）
        long afterMs = endTimeSec > 0 ? (endTimeSec * 1000L) : 0;
        int requestLimit = Math.min(limit, 100);
        StringBuilder url = new StringBuilder(OKX_HISTORY_CANDLES_URL)
                .append("?instId=").append(symbol)
                .append("&bar=").append(bar)
                .append("&limit=").append(requestLimit);
        if (afterMs > 0) {
            url.append("&after=").append(afterMs);
        }
        try {
            log.debug("OKX history-candles: instId={}, bar={}, after={}（after/before 不同时传）", symbol, bar, afterMs);
            String response = HttpUtil.get(url.toString());
            JSONObject json = JSONUtil.parseObj(response);
            if (!"0".equals(json.getStr("code"))) {
                log.warn("OKX API 错误: {}", json.getStr("msg"));
                return Collections.emptyList();
            }
            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return Collections.emptyList();
            }
            List<Candlestick> list = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JSONArray row = data.getJSONArray(i);
                Candlestick c = parseOkxRow(row, symbol, interval);
                if (c != null) {
                    if (startTimeSec > 0) {
                        Long id = c.getId();
                        if (id != null && (id / 1000) < startTimeSec) {
                            continue;
                        }
                    }
                    list.add(c);
                }
            }
            Collections.reverse(list);
            return list;
        } catch (Exception e) {
            log.error("拉取 OKX K线失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private static String mapIntervalToOkxBar(CandlestickIntervalEnum interval) {
        if (interval == null) {
            return "3m";
        }
        switch (interval) {
            case OKXMIN1:
            case OKXMIN3:
            case OKXMIN5:
            case OKXMIN15:
            case OKXMIN30:
            case OKXMIN60:
            case OKX4HOUR:
            case OKX1D:
                return interval.getCode();
            default:
                Integer minNum = interval.getMinNum();
                if (minNum != null) {
                    if (minNum < 60) {
                        return minNum + "m";
                    }
                    if (minNum == 60) {
                        return "1H";
                    }
                    if (minNum < 1440) {
                        return (minNum / 60) + "H";
                    }
                    if (minNum == 1440) {
                        return "1D";
                    }
                }
                return "3m";
        }
    }

    /** OKX 返回: [ts, open, high, low, close, vol, volCcy] 均为字符串 */
    private static Candlestick parseOkxRow(JSONArray row, String symbol, CandlestickIntervalEnum interval) {
        if (row == null || row.size() < 6) {
            return null;
        }
        String tsStr = row.getStr(0);
        long tsMs = Long.parseLong(tsStr);
        String timeStr = DateUtil.longConvertDateTime(tsMs);
        BigDecimal open = new BigDecimal(row.getStr(1));
        BigDecimal high = new BigDecimal(row.getStr(2));
        BigDecimal low = new BigDecimal(row.getStr(3));
        BigDecimal close = new BigDecimal(row.getStr(4));
        BigDecimal vol = new BigDecimal(row.getStr(5));
        String volCcyStr = row.size() > 6 ? row.getStr(6) : null;
        BigDecimal amount = volCcyStr != null && !volCcyStr.isEmpty()
                ? new BigDecimal(volCcyStr)
                : vol.multiply(close);
        return Candlestick.builder()
                .id(tsMs)
                .symbol(symbol)
                .marketType(MarketType.CRYPTO)
                .exchange(Exchange.OKX)
                .candlestickIntervalEnum(interval)
                .timeStr(timeStr)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(vol)
                .amount(amount)
                .count(BigDecimal.ZERO)
                .confirm("1")
                .build();
    }
}
