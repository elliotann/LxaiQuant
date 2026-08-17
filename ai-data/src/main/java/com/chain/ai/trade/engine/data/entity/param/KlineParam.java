package com.chain.ai.trade.engine.data.entity.param;


import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class KlineParam {
    private String memberId;
    private String accountId;

    private MarketType marketType;

    private Exchange exchange;

    /**
     * 市场交易对名称
     */
    private String symbol;

    /**
     * 获取K线周期
     */
    private CandlestickIntervalEnum klineInterval;

    @Builder.Default
    private Integer size = 1000;

    /**
     * 用于测试,获取K线开始时间
     */
    private Long startTime;

    /**
     * 用于测试,获取K线结束时间
     */
    private Long endTime;

    /**
     * 是否测试
     */
    private boolean isTest;

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public MarketType getMarketType() {
        return marketType;
    }

    public void setMarketType(MarketType marketType) {
        this.marketType = marketType;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public CandlestickIntervalEnum getKlineInterval() {
        return klineInterval;
    }

    public void setKlineInterval(CandlestickIntervalEnum klineInterval) {
        this.klineInterval = klineInterval;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public boolean isTest() {
        return isTest;
    }

    public void setTest(boolean test) {
        isTest = test;
    }
}
