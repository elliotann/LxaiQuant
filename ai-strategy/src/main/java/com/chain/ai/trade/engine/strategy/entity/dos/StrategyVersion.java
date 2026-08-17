package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略版本实体类
 */
@Data
@TableName("strategy_version")
public class StrategyVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本唯一标识
     */
    private String versionId;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 版本名称
     */
    private String versionName;

    /**
     * 版本号
     */
    private String versionCode;

    /**
     * 策略代码内容
     */
    private String codeContent;

    /**
     * Java类名
     */
    private String className;

    /**
     * 文件哈希值
     */
    private String fileHash;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 参数配置（JSON格式存储）
     */
    private String parameters;

    /**
     * 参数JSON Schema（JSON格式存储）
     */
    private String parameterSchema;

    /**
     * 依赖配置（JSON格式存储）
     */
    private String dependencies;

    /**
     * 变更类型
     */
    private String changeType;

    /**
     * 变更日志
     */
    private String changeLog;

    /**
     * 主要变更
     */
    private String majorChanges;

    /**
     * 是否为当前版本
     */
    private Boolean isCurrent;

    /**
     * 是否为稳定版本
     */
    private Boolean isStable;

    /**
     * 是否已废弃
     */
    private Boolean isDeprecated;

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
     * 测试结果汇总（JSON格式存储）
     */
    private String testResult;

    /**
     * 标签（JSON格式存储）
     */
    private String tags;

    /**
     * 元数据（JSON格式存储）
     */
    private String metadata;

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
    private Long reviewedBy;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

