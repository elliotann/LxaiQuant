package com.chain.ai.trade.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于表示业务逻辑错误，由全局异常处理器统一处理
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误代码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造业务异常（默认 code 500）
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.message = message;
    }

    /**
     * 构造业务异常（指定 code）
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造业务异常（带原因）
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.message = message;
    }

    /**
     * 构造业务异常（指定 code 和原因）
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
}

