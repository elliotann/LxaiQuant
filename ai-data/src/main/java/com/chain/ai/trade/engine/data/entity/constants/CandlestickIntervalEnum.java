package com.chain.ai.trade.engine.data.entity.constants;

import lombok.Getter;

@Getter
public enum CandlestickIntervalEnum {

    MIN1("1min", 1),
    MIN3("3min", 3),
    MIN5("5min", 5),
    MIN15("15min", 15),
    MIN30("30min", 30),
    MIN60("60min", 60),
    HOUR4("4hour", 240),
    DAY1("1day", 1440),
    MON1("1mon", 43200), // 30天的分钟数
    WEEK1("1week", 10080), // 7天的分钟数
    YEAR1("1year", 525600),// 365天的分钟数
    OKXMIN30("30m", 30),
    OKXMIN15("15m", 15),
    OKXMIN5("5m", 5),
    OKXMIN3("3m", 3),
    OKXMIN1("1m" ,1),
    OKXMIN60("1H", 60),
    OKX4HOUR("4H", 240),
    OKX1D("1D", 1440)

    ;

    private final String code;

    /**
     * 分钟数
     */
    private final Integer minNum;

    CandlestickIntervalEnum(String code, Integer minNum) {
        this.code = code;
        this.minNum = minNum;
    }

    public static Integer getMinNumByCode(String code) {
        for (CandlestickIntervalEnum interval : CandlestickIntervalEnum.values()) {
            if (interval.getCode().equals(code)) {
                return interval.getMinNum();
            }
        }
        return null;
    }

    public static CandlestickIntervalEnum fromCode(String code) {
        if (code == null) return null;
        for (CandlestickIntervalEnum interval : CandlestickIntervalEnum.values()) {
            if (interval.name().equals(code)) {
                return interval;
            }
        }
        return null;
    }

    /**
     * 根据code值（如 3m, 15m, 1h, 4h, 1d）模糊查找，忽略大小写
     */
    public static CandlestickIntervalEnum fromCodeValue(String codeValue) {
        if (codeValue == null || codeValue.isBlank()) return null;
        for (CandlestickIntervalEnum interval : CandlestickIntervalEnum.values()) {
            if (interval.getCode().equalsIgnoreCase(codeValue.trim())) {
                return interval;
            }
        }
        return null;
    }

    /**
     * 转换为 OKX 前缀的周期枚举（DB K线数据统一用 OKX 周期存储）
     */
    public CandlestickIntervalEnum toOkxInterval() {
        for (CandlestickIntervalEnum e : values()) {
            if (e.name().startsWith("OKX") && e.getMinNum().equals(this.minNum)) {
                return e;
            }
        }
        return this;
    }
}

