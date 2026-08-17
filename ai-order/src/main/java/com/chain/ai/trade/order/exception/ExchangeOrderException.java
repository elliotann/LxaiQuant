package com.chain.ai.trade.order.exception;

public class ExchangeOrderException extends RuntimeException {
    public ExchangeOrderException(String message) {
        super(message);
    }

    public ExchangeOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}

