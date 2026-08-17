package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.engine.strategy.enums.Frequency;
import com.chain.ai.trade.engine.strategy.enums.StrategyStatus;
import com.chain.ai.trade.engine.strategy.enums.StrategyType;
import com.chain.ai.trade.engine.strategy.enums.Visibility;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略定义实体类
 */
@Data
@TableName("strategy")
public class Strategy implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 策略唯一标识
     */
    private String strategyId;

    /**
     * 策略名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 策略描述
     */
    private String description;

    /**
     * 简要描述
     */
    private String briefDescription;

    /**
     * 策略类型
     */
    private String strategyType;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 子分类
     */
    private String subCategory;

    /**
     * 标签（JSON格式存储）
     */
    private String tags;

    /**
     * 策略代码内容（脚本类策略）
     */
    private String codeContent;

    /**
     * Java类名（Java类策略）
     */
    private String className;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 入口函数/方法
     */
    private String entryPoint;

    /**
     * 默认参数（JSON格式存储）
     */
    private String defaultParameters;

    /**
     * 参数JSON Schema（JSON格式存储）
     */
    private String parameterSchema;

    /**
     * 依赖配置（JSON格式存储）
     */
    private String dependencies;

    /**
     * AI 智能过滤配置（JSON格式存储）
     * {
     *   "enabled": false,
     *   "mode": "signal",
     *   "llmConfigId": null,
     *   "thresholds": {
     *     "directAllowThreshold": 50,
     *     "directRejectThreshold": 15,
     *     "lowConfidenceThreshold": 30
     *   },
     *   "scoringWeights": {
     *     "trendWeight": 0.35,
     *     "volatilityWeight": 0.20,
     *     "supportResistanceWeight": 0.25,
     *     "volumePriceWeight": 0.20
     *   }
     * }
     */
    @TableField("auto_signal")
    private String autoSignal;

    /**
     * 状态
     */
    private String status;

    /**
     * 可见性
     */
    private String visibility;

    /**
     * 是否为系统策略
     */
    private Boolean isSystem;

    /**
     * 是否为模板策略
     */
    private Boolean isTemplate;

    /**
     * 运行频率
     */
    private String frequency;

    /**
     * 市场类型
     */
    private String marketType;

    /**
     * 时间框架
     */
    private String timeFrame;

    /**
     * 支持做多
     */
    private Boolean supportsLong;

    /**
     * 支持做空
     */
    private Boolean supportsShort;

    /**
     * 支持杠杆
     */
    private Boolean supportsLeverage;

    /**
     * 最大持仓数量
     */
    private Integer maxPositionCount;

    /**
     * 最小资金要求
     */
    private BigDecimal minCapital;

    /**
     * 当前版本
     */
    private String currentVersion;

    /**
     * 最新版本ID
     */
    private Long latestVersionId;

    /**
     * 回测次数
     */
    private Integer backtestCount;

    /**
     * 平均夏普比率
     */
    private BigDecimal avgSharpeRatio;

    /**
     * 平均年化收益
     */
    private BigDecimal avgAnnualReturn;

    /**
     * 平均最大回撤
     */
    private BigDecimal avgMaxDrawdown;

    /**
     * 成功率
     */
    private BigDecimal successRate;

    /**
     * 所有者ID
     */
    private Long ownerId;

    /**
     * 所有者姓名
     */
    private String ownerName;

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 创建者姓名
     */
    private String createdByName;

    /**
     * 审核者ID
     */
    private Long approvedBy;

    /**
     * 审核时间
     */
    private LocalDateTime approvedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最后执行时间
     */
    private LocalDateTime lastExecutedAt;

    /**
     * 最后修改时间
     */
    private LocalDateTime lastModifiedAt;
}

