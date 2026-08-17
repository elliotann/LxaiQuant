package com.chain.ai.trade.engine.data.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SMC 摆动点标签（HH/HL/LL/LH）
 */
@Data
@AllArgsConstructor
public class SmcSwingPoint {
    private long time;       // 毫秒时间戳
    private double price;    // 标签价格
    private String label;    // "HH" / "LH" / "LL" / "HL"
}
