package com.chain.ai.trade.engine.strategy;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态风控引擎配置 DTO（防守线移动止损 / 进攻线移动止盈，结构追踪）。
 * <p>
 * 存储于 StrategyParameter（group=dynamic_risk_engine, key=config）的单条 JSON 记录。
 * 百分比字段按「百分比数值」存储（如 0.05 = 0.05%），装配到规则时再转小数。
 * </p>
 */
@Data
@NoArgsConstructor
public class DynamicRiskEngineDTO {

    /** 防守线（移动止损 · 结构追踪） */
    private TrailingStopConfig trailingStop = new TrailingStopConfig();

    /** 进攻线（移动止盈 · 结构追踪） */
    private TrailingTakeProfitConfig trailingTakeProfit = new TrailingTakeProfitConfig();

    @Data
    @NoArgsConstructor
    public static class TrailingStopConfig {
        /** 是否启用 */
        private boolean enabled = false;
        /** 移动算法（当前仅 structure 可用） */
        private String algorithm = "structure";
        /** 参考周期（分钟，如 15/60/240） */
        private String period = "15";
        /** 结构类型 */
        private StructureTypes structureTypes = new StructureTypes();
        /** 结构边缘缓冲（百分比，0.05 = 0.05%） */
        private double offsetBuffer = 0.05;
        /** 破坏判定：wick=影线刺穿，close=收盘破位 */
        private String breakMode = "wick";
        /** 激活时机：open=开仓即动，break=突破前高/前低后动 */
        private String activation = "open";
    }

    @Data
    @NoArgsConstructor
    public static class TrailingTakeProfitConfig {
        /** 是否启用 */
        private boolean enabled = false;
        /** 移动算法（当前仅 structure 可用） */
        private String algorithm = "structure";
        /** 参考周期（分钟） */
        private String period = "60";
        /** 结构类型 */
        private StructureTypes structureTypes = new StructureTypes();
        /** 结构边缘缓冲（百分比） */
        private double offsetBuffer = 0.05;
        /** 触发条件：wick=影线刺穿，close=收盘突破 */
        private String triggerMode = "wick";
        /** 激活时机：open=开仓即动，break=突破前高/前低后动 */
        private String activation = "open";
        /** 离场方式：all=全仓，half=平50%剩余追 */
        private String exitMode = "all";
        /** 是否启用最小步进 */
        private boolean minStepEnabled = false;
        /** 最小步进（百分比，低于此不更新止盈目标） */
        private double minStep = 0.1;
    }

    @Data
    @NoArgsConstructor
    public static class StructureTypes {
        /** 订单块（OB） */
        private boolean ob = true;
        /** 前高/前低（摆动点） */
        private boolean swing = true;
    }
}
