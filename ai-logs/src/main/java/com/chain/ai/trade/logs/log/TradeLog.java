package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 成交日志实现类
 */
public class TradeLog implements BusinessLog, Traceable {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private long timestamp;
    private String logType = "TRADE";
    private String level = "INFO";
    private String traceId;
    private String tradeId;
    private String orderId;
    private String strategyId;
    private String symbol;
    private String side;
    private double price;
    private double quantity;
    private double fee;

    public TradeLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public TradeLog(String level, String traceId, String tradeId, String orderId, String strategyId, 
                    String symbol, String side, double price, double quantity, double fee) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.tradeId = tradeId;
        this.orderId = orderId;
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.fee = fee;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getLogType() {
        return logType;
    }

    @Override
    public String getLevel() {
        return level;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TradeLog to JSON", e);
        }
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }
}
