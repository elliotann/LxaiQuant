package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 缠论算法配置参数
 */
@Data
@Component
public class ChanLunConfig {
    private double atrThreshold = 0.3;
    private double atrFilterThreshold = 0.5;
    private int minKlineBetween = 1;
    private boolean useNewBiRule = true;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private boolean enableFirstPoint = true;
    private boolean enableSecondPoint = true;
    private boolean enableThirdPoint = true;

    /** 分型强度过滤阈值，设为0表示不过滤 */
    private double minFenXingStrength = 0.0;
}
