package com.chain.ai.trade.order.validation.handlers;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.order.validation.AbstractOrderValidationHandler;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 基础参数校验处理器
 * 校验交易策略参数的基本有效性
 */
@Component
@Order(1) // 执行优先级
public class BasicParameterValidationHandler extends AbstractOrderValidationHandler {

    @Override
    protected ValidationResult doValidate(OrderValidationContext context) {
        TradingStrategyParams params = context.getParams();

        // 基础参数校验
        if (params.getAccountId() == null) {
            return ValidationResult.failure("INVALID_ACCOUNT", "账户ID不能为空");
        }

        if (params.getSymbol() == null || params.getSymbol().trim().isEmpty()) {
            return ValidationResult.failure("INVALID_SYMBOL", "交易对不能为空");
        }

        if (params.getSide() == null || params.getSide().trim().isEmpty()) {
            return ValidationResult.failure("INVALID_SIDE", "交易方向不能为空");
        }

        if (params.getAmount() == null || params.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("INVALID_AMOUNT", "交易数量必须大于0");
        }

        return ValidationResult.success();
    }

    @Override
    public String getHandlerName() {
        return "BasicParameterValidation";
    }

    @Override
    public int getPriority() {
        return 1;
    }
}