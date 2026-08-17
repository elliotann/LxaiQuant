package com.chain.ai.trade.order.validation;

/**
 * 订单校验处理器接口
 */
public interface OrderValidationHandler {

    /**
     * 执行校验
     * @param context 校验上下文
     * @return 校验结果
     */
    ValidationResult validate(OrderValidationContext context);

    /**
     * 获取处理器名称
     * @return 处理器名称
     */
    String getHandlerName();

    /**
     * 获取执行优先级（数字越小优先级越高）
     * @return 优先级
     */
    int getPriority();

    /**
     * 设置下一个处理器
     * @param nextHandler 下一个处理器
     */
    default void setNextHandler(OrderValidationHandler nextHandler) {
        // 默认空实现，责任链模式由具体实现类管理
    }
}