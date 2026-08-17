package com.chain.ai.trade.order.validation.validators;

import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单向持仓校验器
 * 单向持仓模式下，不允许存在任何持仓
 */
@Component
public class SingleDirectionPositionValidator implements PositionValidator {

    @Override
    public ValidationResult validate(OrderValidationContext context) {
        List<TradePosition> positions = context.getExistingPositions();

        if (!positions.isEmpty()) {
            BigDecimal totalAmount = positions.stream()
                .map(TradePosition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return ValidationResult.failure("POSITION_EXISTS_SINGLE_MODE",
                String.format("单向持仓模式下已有持仓，禁止重复开仓。现有持仓量: %s", totalAmount));
        }

        return ValidationResult.success();
    }

    @Override
    public boolean supports(OrderValidationContext context) {
        return !Boolean.TRUE.equals(context.getParams().getBidirectionalEnabled());
    }

    @Override
    public String getValidatorName() {
        return "SingleDirectionPositionValidator";
    }
}