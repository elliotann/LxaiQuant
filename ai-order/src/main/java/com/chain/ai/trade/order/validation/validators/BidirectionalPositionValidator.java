package com.chain.ai.trade.order.validation.validators;

import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 双向持仓校验器
 * 双向持仓模式下，允许同时持有多头和空头，但不允许同方向重复开仓
 */
@Component
public class BidirectionalPositionValidator implements PositionValidator {

    @Override
    public ValidationResult validate(OrderValidationContext context) {
        // 补仓场景走 validateAddPosition，此处仅处理新开仓场景
        // 新开仓时不允许同方向重复开仓，allowAddPosition 不影响此校验
        if (context.isAddPosition()) {
            return ValidationResult.success();
        }

        List<TradePosition> positions = context.getExistingPositions();
        String side = context.getParams().getSide();

        List<TradePosition> sameSidePositions = positions.stream()
            .filter(p -> side.equals(p.getOrderSideEnum().name()))
            .collect(Collectors.toList());

        if (!sameSidePositions.isEmpty()) {
            BigDecimal sameSideAmount = sameSidePositions.stream()
                .map(TradePosition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return ValidationResult.failure("SAME_SIDE_POSITION_EXISTS",
                String.format("双向持仓模式下已有%s持仓，禁止同方向重复开仓。现有持仓量: %s",
                    side, sameSideAmount));
        }

        return ValidationResult.success();
    }

    @Override
    public boolean supports(OrderValidationContext context) {
        return Boolean.TRUE.equals(context.getParams().getBidirectionalEnabled());
    }

    @Override
    public String getValidatorName() {
        return "BidirectionalPositionValidator";
    }
}
