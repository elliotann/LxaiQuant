package com.chain.ai.trade.order.validation;

import lombok.extern.slf4j.Slf4j;

/**
 * 抽象订单校验处理器
 * 实现责任链模式的模板方法
 */
@Slf4j
public abstract class AbstractOrderValidationHandler implements OrderValidationHandler {

    protected OrderValidationHandler nextHandler;

    @Override
    public void setNextHandler(OrderValidationHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    @Override
    public final ValidationResult validate(OrderValidationContext context) {
        try {
            // 1. 前置校验
            ValidationResult preResult = preValidate(context);
            if (!preResult.isValid()) {
                return preResult;
            }

            // 2. 核心校验逻辑（由子类实现）
            ValidationResult result = doValidate(context);

            // 3. 后置处理
            postValidate(context, result);

            // 如果校验失败，直接返回，不继续责任链
            if (!result.isValid()) {
                log.warn("校验失败 [处理器={}, 错误码={}, 消息={}]",
                        getHandlerName(), result.getErrorCode(), result.getErrorMessage());
                return result;
            }

            // 校验成功，继续责任链
            if (nextHandler != null) {
                return nextHandler.validate(context);
            }

            return result;

        } catch (Exception e) {
            log.error("校验处理器执行异常 [处理器={}]", getHandlerName(), e);
            return ValidationResult.failure("VALIDATION_ERROR",
                    String.format("%s校验异常: %s", getHandlerName(), e.getMessage()));
        }
    }

    /**
     * 前置校验
     * @param context 校验上下文
     * @return 校验结果
     */
    protected ValidationResult preValidate(OrderValidationContext context) {
        return ValidationResult.success();
    }

    /**
     * 核心校验逻辑（由子类实现）
     * @param context 校验上下文
     * @return 校验结果
     */
    protected abstract ValidationResult doValidate(OrderValidationContext context);

    /**
     * 后置处理
     * @param context 校验上下文
     * @param result 校验结果
     */
    protected void postValidate(OrderValidationContext context, ValidationResult result) {
        // 默认空实现，子类可重写
    }
}