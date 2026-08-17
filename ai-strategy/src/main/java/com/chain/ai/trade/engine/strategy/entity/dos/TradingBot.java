package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易机器人实体 - 策略执行和交易管理
 * 职责：执行交易策略，管理交易订单，控制风险
 * 注意：不直接关联钱包，只使用交易账户的内部资金
 */
@Data
@TableName("trading_bot")
public class TradingBot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 机器人唯一标识
     */
    private String botId;

    /**
     * 机器人名称
     */
    private String botName;

    /**
     * 所属用户ID
     */
    private String userId;

    /**
     * 使用的交易账户ID
     */
    private String accountId;

    /**
     * 交易所（如 BINANCE, OKX, BYBIT 等）
     */
    private String exchange;

    /**
     * 使用的策略ID
     */
    private String strategyId;

    /**
     * 交易对
     */
    private String tradingPair;

    /**
     * 分配的资金额度
     */
    private BigDecimal allocatedCapital;

    /**
     * 当前剩余资金
     */
    private BigDecimal currentCapital;

    /**
     * 峰值资金（运行期间达到的最高资金）
     */
    private BigDecimal peakCapital;

    /**
     * 机器人状态
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 最后信号时间
     */
    private LocalDateTime lastSignalTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者ID
     */
    private String createdBy;

    /**
     * 更新者ID
     */
    private String updatedBy;

    /**
     * 配置信息（JSON格式存储）
     * 包含交易参数、风险控制参数等
     */
    private String configuration;

    /**
     * 统计信息（JSON格式存储）
     * 包含交易次数、胜率、收益等统计数据
     */
    private String statistics;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String remark;
}
