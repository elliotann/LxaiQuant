package com.chain.ai.trade.order.validation;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单校验上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValidationContext {

    /** 交易策略参数 */
    private TradingStrategyParams params;

    /** 是否为补仓操作 */
    private boolean isAddPosition;

    /** 现有持仓列表 */
    private List<TradePosition> existingPositions;

    /** 校验数据上下文 */
    @Builder.Default
    private Map<String, Object> validationData = new HashMap<>();
}