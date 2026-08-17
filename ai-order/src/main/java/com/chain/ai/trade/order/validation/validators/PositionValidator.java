package com.chain.ai.trade.order.validation.validators;

import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;

/**
 * 持仓校验器接口
 * 使用策略模式根据不同持仓模式选择不同的校验策略
 */
public interface PositionValidator {

    /**
     * 执行持仓校验
     * @param context 校验上下文
     * @return 校验结果
     */
    ValidationResult validate(OrderValidationContext context);

    /**
     * 判断是否支持当前校验上下文
     * @param context 校验上下文
     * @return 是否支持
     */
    boolean supports(OrderValidationContext context);

    /**
     * 获取校验器名称
     * @return 校验器名称
     */
    String getValidatorName();
}