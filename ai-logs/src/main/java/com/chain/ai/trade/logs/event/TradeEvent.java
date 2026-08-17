package com.chain.ai.trade.logs.event;

/**
 * 订单成交事件
 */
public class TradeEvent extends BusinessEvent {
    
    private String tradeId;
    private String orderId;
    private String symbol;
    private String side;
    private double price;
    private double quantity;
    private double fee;
    
    public TradeEvent() {
        super("TRADE", null);
    }
    
    public TradeEvent(String traceId, String tradeId, String orderId, String symbol, 
                     String side, double price, double quantity, double fee) {
        super("TRADE", traceId);
        this.tradeId = tradeId;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.fee = fee;
    }
    
    // Getters and Setters
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