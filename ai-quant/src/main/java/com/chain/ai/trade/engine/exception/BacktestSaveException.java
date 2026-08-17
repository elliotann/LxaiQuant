package com.chain.ai.trade.engine.exception;

/**
 * 回测结果保存异常
 */
public class BacktestSaveException extends RuntimeException {

    public BacktestSaveException(String message) {
        super(message);
    }

    public BacktestSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}

