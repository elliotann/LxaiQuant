package com.chain.ai.trade.engine.entity.dto;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.risk.common.TimeFrame;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 交易信号
 */
@Data
@Builder
public class TradingSignalDto {
    private String id;                    // 信号ID
    private String sourceId;              // 信号源ID（虽然不实现信号源，但保留字段）
    private String symbol;                // 交易对
    private SignalType type;              // 信号类型（BUY/SELL）
    private TimeFrame timeFrame;          // 时间框架
    private double triggerPrice;          // 触发价格
    private LocalDateTime timestamp;      // 信号时间
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>(); // 元数据（如指标值、形态等）


    private Double takeProfitPrice;

    private Double stopLossPrice;

    private Double signalStrength;       // 信号强度 0~1

    private String direction;            // 交易方向 LONG/SHORT


}

