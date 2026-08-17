package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略参数实体类
 */
@Data
@TableName("strategy_parameter")
public class StrategyParameter implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 参数唯一标识
     */
    private String paramId;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 参数分组
     */
    private String groupName;

    /**
     * 参数类型
     */
    private String paramType;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 最小值
     */
    private String minValue;

    /**
     * 最大值
     */
    private String maxValue;

    /**
     * 步长
     */
    private BigDecimal stepValue;

    /**
     * 选项列表（JSON格式存储，SELECT类型）
     */
    private String options;

    /**
     * 格式模式
     */
    private String formatPattern;

    /**
     * 是否必填
     */
    private Boolean isRequired;

    /**
     * 是否为高级参数
     */
    private Boolean isAdvanced;

    /**
     * 是否为数组
     */
    private Boolean isArray;

    /**
     * 最小长度
     */
    private Integer minLength;

    /**
     * 最大长度
     */
    private Integer maxLength;

    /**
     * 正则表达式
     */
    private String regexPattern;

    /**
     * 验证提示信息
     */
    private String validationMessage;

    /**
     * 显示顺序
     */
    private Integer displayOrder;

    /**
     * 是否可见
     */
    private Boolean isVisible;

    /**
     * UI组件类型
     */
    private String uiComponent;

    /**
     * 占位符
     */
    private String placeholder;

    /**
     * 提示信息
     */
    private String tooltip;

    /**
     * 元数据（JSON格式存储）
     */
    private String metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

