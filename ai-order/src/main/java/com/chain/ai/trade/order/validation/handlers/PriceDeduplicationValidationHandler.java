package com.chain.ai.trade.order.validation.handlers;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.mapper.TradeOrderItemMapper;
import com.chain.ai.trade.order.validation.AbstractOrderValidationHandler;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.ValidationResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 价格去重校验处理器
 * 防止在相近价格区间内重复补仓
 */
@Component
@Order(3) // 执行优先级
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.order-validation.price-deduplication.enabled", havingValue = "true")
public class PriceDeduplicationValidationHandler extends AbstractOrderValidationHandler {

    private final TradeOrderItemMapper tradeOrderItemMapper;

    @Value("${trading.order-validation.price-deduplication.threshold-percent.high-price:2.0}")
    private BigDecimal highPriceThreshold;

    @Value("${trading.order-validation.price-deduplication.threshold-percent.mid-price:3.0}")
    private BigDecimal midPriceThreshold;

    @Value("${trading.order-validation.price-deduplication.threshold-percent.low-price:5.0}")
    private BigDecimal lowPriceThreshold;

    @Override
    protected ValidationResult doValidate(OrderValidationContext context) {
        if (!context.isAddPosition()) {
            return ValidationResult.success(); // 主仓不需要价格去重
        }

        TradingStrategyParams params = context.getParams();
        TradePosition targetOrder = (TradePosition) context.getValidationData().get("targetOrder");

        if (targetOrder == null) {
            return ValidationResult.failure("TARGET_ORDER_MISSING", "目标订单信息缺失");
        }

        // 查询该订单下的所有补仓记录
        List<TradeEntry> existingItems = queryExistingItems(params.getPositionId(), params.getSide());

        BigDecimal newPrice = params.getPrice();
        BigDecimal priceThreshold = getPriceDeduplicationThreshold(newPrice);

        for (TradeEntry item : existingItems) {
            BigDecimal existingPrice = item.getBuyPrice();
            if (existingPrice == null) {
                continue; // 跳过没有价格信息的记录
            }

            BigDecimal priceDiff = existingPrice.subtract(newPrice).abs();
            BigDecimal priceDiffPercent = priceDiff.divide(existingPrice, 4, RoundingMode.HALF_UP);

            if (priceDiffPercent.compareTo(priceThreshold) <= 0) {
                return ValidationResult.failure("PRICE_TOO_CLOSE",
                    String.format("补仓失败：该价格区间内已存在补仓记录 (价格差异: %.2f%%)",
                        priceDiffPercent.multiply(new BigDecimal("100")).doubleValue()));
            }
        }

        return ValidationResult.success();
    }

    /**
     * 查询现有补仓记录
     */
    private List<TradeEntry> queryExistingItems(String orderSn, String side) {
        LambdaQueryWrapper<TradeEntry> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.eq(TradeEntry::getPositionId, orderSn)
                 .eq(TradeEntry::getOrderSideEnum, side)
                 .last("FOR UPDATE"); // 悲观锁

        return tradeOrderItemMapper.selectList(itemQuery);
    }

    /**
     * 根据价格确定去重阈值
     */
    private BigDecimal getPriceDeduplicationThreshold(BigDecimal price) {
        if (price.compareTo(new BigDecimal("100000")) >= 0) {
            return highPriceThreshold.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else if (price.compareTo(new BigDecimal("10000")) >= 0) {
            return midPriceThreshold.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else {
            return lowPriceThreshold.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }
    }

    @Override
    public String getHandlerName() {
        return "PriceDeduplicationValidation";
    }

    @Override
    public int getPriority() {
        return 3;
    }
}