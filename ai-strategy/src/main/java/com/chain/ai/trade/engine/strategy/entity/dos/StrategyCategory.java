package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略分类实体类
 */
@Data
@TableName("strategy_category")
public class StrategyCategory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类代码
     */
    private String code;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 分类图标
     */
    private String icon;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 分类路径
     */
    private String path;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否为系统分类
     */
    private Boolean isSystem;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 策略数量
     */
    private Integer strategyCount;

    /**
     * 回测次数
     */
    private Integer backtestCount;

    /**
     * 平均夏普比率
     */
    private BigDecimal avgSharpeRatio;

    /**
     * 标签（JSON格式存储）
     */
    private String tags;

    /**
     * 元数据（JSON格式存储）
     */
    private String metadata;

    /**
     * 所有者ID
     */
    private Long ownerId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

