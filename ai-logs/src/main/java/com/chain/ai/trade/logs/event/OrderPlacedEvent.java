package com.chain.ai.trade.logs.event;

/**
 * 订单下单事件
 */
public class OrderPlacedEvent extends BusinessEvent {
    
    private String orderId;
    private String symbol;
    private String side;
    private double price;
    private double quantity;
    private String status;
    
    public OrderPlacedEvent() {
        super("ORDER_PLACED", null);
    }
    
    public OrderPlacedEvent(String traceId, String orderId, String symbol, String side, 
                           double price, double quantity, String status) {
        super("ORDER_PLACED", traceId);
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
    }
    
    // Getters and Setters
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}