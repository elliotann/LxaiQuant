package com.chain.ai.trade.order.validation.handlers;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.mapper.TradeOrderMapper;
import com.chain.ai.trade.order.validation.AbstractOrderValidationHandler;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;
import com.chain.ai.trade.order.validation.validators.PositionValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持仓状态校验处理器
 * 使用策略模式根据持仓模式选择不同的校验策略
 */
@Component
@Order(2) // 执行优先级
@RequiredArgsConstructor
public class PositionStatusValidationHandler extends AbstractOrderValidationHandler {

    private final TradeOrderMapper tradeOrderMapper;
    private final List<PositionValidator> positionValidators;

    @Override
    protected ValidationResult doValidate(OrderValidationContext context) {
        // 查询当前持仓状态（使用悲观锁）
        List<TradePosition> existingPositions = queryExistingPositions(context.getParams());
        context.setExistingPositions(existingPositions);

        // 根据场景执行不同校验策略
        if (context.isAddPosition()) {
            return validateAddPosition(context);
        } else {
            return validateMainPosition(context);
        }
    }

    /**
     * 校验主仓开单
     */
    private ValidationResult validateMainPosition(OrderValidationContext context) {
        // 选择合适的持仓校验器
        PositionValidator validator = selectPositionValidator(context);
        if (validator == null) {
            return ValidationResult.failure("NO_VALIDATOR_FOUND", "未找到适用的持仓校验器");
        }

        // 执行校验
        return validator.validate(context);
    }

    /**
     * 校验补仓操作
     */
    private ValidationResult validateAddPosition(OrderValidationContext context) {
        TradingStrategyParams params = context.getParams();

        // 1. 验证目标订单存在
        TradePosition targetOrder = tradeOrderMapper.selectById(params.getPositionId());
        if (targetOrder == null) {
            return ValidationResult.failure("ORDER_NOT_FOUND",
                String.format("补仓失败：订单不存在 orderSn=%s", params.getPositionId()));
        }

        // 2. 验证订单状态
        if (!"DEAL".equals(targetOrder.getTradeOrderStatus().name())) {
            return ValidationResult.failure("ORDER_STATUS_INVALID",
                String.format("补仓失败：订单状态不正确 orderSn=%s, status=%s",
                    params.getPositionId(), targetOrder.getTradeOrderStatus()));
        }

        // 3. 验证权限
        if (!targetOrder.getAccountId().equals(params.getAccountId())) {
            return ValidationResult.failure("ORDER_ACCESS_DENIED",
                String.format("补仓失败：账户权限不足 orderSn=%s", params.getPositionId()));
        }

        // 4. 验证是否允许补仓
        if (!Boolean.TRUE.equals(params.getAllowAddPosition())) {
            return ValidationResult.failure("ADD_POSITION_NOT_ALLOWED",
                "当前策略不允许补仓操作");
        }

        // 将目标订单存入上下文，供后续处理器使用
        context.getValidationData().put("targetOrder", targetOrder);

        return ValidationResult.success();
    }

    /**
     * 查询现有持仓（使用悲观锁确保数据一致性）
     */
    private List<TradePosition> queryExistingPositions(TradingStrategyParams params) {
        LambdaQueryWrapper<TradePosition> query = new LambdaQueryWrapper<>();
        query.eq(TradePosition::getAccountId, params.getAccountId())
             .eq(TradePosition::getSymbol, params.getSymbol())
             .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL); // 悲观锁

        return tradeOrderMapper.selectList(query);
    }

    /**
     * 选择合适的持仓校验器
     */
    private PositionValidator selectPositionValidator(OrderValidationContext context) {
        return positionValidators.stream()
            .filter(validator -> validator.supports(context))
            .findFirst()
            .orElse(null);
    }

    @Override
    public String getHandlerName() {
        return "PositionStatusValidation";
    }

    @Override
    public int getPriority() {
        return 2;
    }
}