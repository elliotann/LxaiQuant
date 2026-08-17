package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 订单日志实现类
 */
public class OrderLog implements BusinessLog, Traceable {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private long timestamp;
    private String logType = "ORDER";
    private String level = "INFO";
    private String traceId;
    private String orderId;
    private String strategyId;
    private String symbol;
    private String side;
    private double price;
    private double quantity;
    private String status;

    public OrderLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public OrderLog(String level, String traceId, String orderId, String strategyId, String symbol, 
                    String side, double price, double quantity, String status) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.orderId = orderId;
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
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
            throw new RuntimeException("Failed to serialize OrderLog to JSON", e);
        }
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
