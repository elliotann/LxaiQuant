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
public class CandlestickRequest {
    private String apiKey;

    private String secretKey;

    private String passphrase;


    private Boolean simulated;

    private String symbol;

    private MarketType marketType;

    private Exchange exchange;

    private CandlestickIntervalEnum interval;

    private Integer size;

    /**
     * 查询时间断，10位，单位为s
     */
    private long from;

    /**
     * 查询时间断，10位，单位为s
     */
    private long to;

    /**
     * 会员ID
     */
    private String memberId;

    /**
     * API id
     */
    private String accountId;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public CandlestickIntervalEnum getInterval() {
        return interval;
    }

    public void setInterval(CandlestickIntervalEnum interval) {
        this.interval = interval;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

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
}
