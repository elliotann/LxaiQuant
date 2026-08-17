package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine.ChanLunMultiPeriodEngine;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine.Period;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.*;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 缠论分析服务
 */
@Service
@RequiredArgsConstructor
public class ChanLunService {

    private final ICandlestickService candlestickService;
    private final ChanLunMultiPeriodEngine multiEngine;

    /**
     * 获取指定周期的缠论分析结果
     */
    public ChanLunResult getData(String symbol, String interval, Integer limit) {
        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        if (intervalEnum == null) return null;

        Period period = toPeriod(intervalEnum);
        if (period == null) return null;

        int take = limit == null ? 500 : Math.min(2000, Math.max(100, limit));
        List<Candlestick> klines = loadKlines(symbol, intervalEnum, take);
        if (klines == null || klines.isEmpty()) return null;

        List<StdKLine> stdKlines = new ArrayList<>();
        for (int i = 0; i < klines.size(); i++) {
            StdKLine s = toStdKLine(klines.get(i));
            s.setOriginalIndex(i);
            stdKlines.add(s);
        }

        return multiEngine.compute(period, stdKlines);
    }

    public ChanLunConfig getConfig() {
        return new ChanLunConfig();
    }

    private List<Candlestick> loadKlines(String symbol, CandlestickIntervalEnum interval, int limit) {
        KlineParam param = KlineParam.builder()
                .symbol(symbol)
                .klineInterval(interval)
                .size(limit)
                .build();
        return candlestickService.getLastKlines(param);
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) return null;
        // 前端可能传 OKXMIN15 或 15m 格式
        try {
            return CandlestickIntervalEnum.valueOf(interval);
        } catch (IllegalArgumentException e) {
            // 尝试匹配 code（如 "15m" → OKXMIN15）
            for (CandlestickIntervalEnum v : CandlestickIntervalEnum.values()) {
                if (v.getCode().equalsIgnoreCase(interval)) return v;
            }
            return null;
        }
    }

    private StdKLine toStdKLine(Candlestick k) {
        StdKLine s = new StdKLine();
        s.setTime(parseCandlestickTime(k.getTimeStr()));
        s.setOpen(k.getOpenPrice().doubleValue());
        s.setHigh(k.getHighPrice().doubleValue());
        s.setLow(k.getLowPrice().doubleValue());
        s.setClose(k.getClosePrice().doubleValue());
        s.setVolume(k.getVolume().longValue());
        s.setAtr(0);
        return s;
    }

    private LocalDateTime parseCandlestickTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return LocalDateTime.now();
        try {
            if (timeStr.matches("\\d{10}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(timeStr)), ZoneId.systemDefault());
            } else if (timeStr.matches("\\d{13}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(timeStr)), ZoneId.systemDefault());
            } else {
                return LocalDateTime.parse(timeStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private Period toPeriod(CandlestickIntervalEnum e) {
        switch (e) {
            case MIN3:    case OKXMIN3:  return Period.M3;
            case MIN5:    case OKXMIN5:  return Period.M5;
            case MIN15:   case OKXMIN15: return Period.M15;
            case MIN30:   case OKXMIN30: return Period.M30;
            case MIN60:   case OKXMIN60: return Period.M60;
            case HOUR4:   case OKX4HOUR: return Period.H4;
            case DAY1:    case OKX1D:    return Period.DAILY;
            default:                      return null;
        }
    }
}
