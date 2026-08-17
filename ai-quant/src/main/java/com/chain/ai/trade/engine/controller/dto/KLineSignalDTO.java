package com.chain.ai.trade.engine.controller.dto;

import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * K线信号标注DTO
 */
@Data
@Builder
public class KLineSignalDTO {
    
    /**
     * 信号ID
     */
    private Long id;
    
    /**
     * K线时间戳（UTC时间戳，秒）
     */
    private Long time;
    
    /**
     * 信号类型（LB: 多头开仓, SB: 空头开仓, LS: 多头平仓, SS: 空头平仓等）
     */
    private String signalType;
    
    /**
     * 信号价格
     */
    private java.math.BigDecimal price;
    
    /**
     * 信号描述
     */
    private String description;
    
    /**
     * 信号强度（0-100）
     */
    private java.math.BigDecimal signalStrength;
    
    /**
     * 信号来源（technical: 技术信号, trade: 交易信号）
     */
    private String signalSource;
    
    /**
     * 机器人ID
     */
    private String robotId;
    
    /**
     * 订单号（如果是交易信号）
     */
    private String orderSn;
    
    /**
     * 状态（如果是交易信号）
     */
    private String status;

    /**
     * 入场类型：MARKET(市价) / LIMIT(限价)
     */
    private String entryType;

    /**
     * 限价单价格
     */
    private java.math.BigDecimal limitPrice;

    /**
     * 额外参数（JSON，技术信号可包含 SMC 15m/1h 等快照信息）
     */
    private String extraParams;

    /**
     * 关键点位（基于 SMC 多周期算法生成，按信号方向提取入场/止损/止盈1/止盈2）
     */
    private List<CriticalLevel> criticalLevels;

    /**
     * 市场趋势/状态（如 STRONG_BULLISH_HEALTHY, RANGING_NO_DIRECTION 等）
     */
    private String marketTrend;
}
