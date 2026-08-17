package com.chain.ai.trade.engine.data.entity.dto.smc;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 混沌特例判定结果
 */
@Data
@AllArgsConstructor
public class ChaosExceptionResult {
    /** 是否触发混沌特例 */
    private boolean triggered;
    /** 触发原因描述 */
    private String reason;
    /** 强制乘数（触发时为 0.2，未触发时为 1.0） */
    private double forcedMultiplier;
}
