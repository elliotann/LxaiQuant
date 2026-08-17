package com.chain.ai.trade.extension.ta4j.indicator.consolidation;

import org.ta4j.core.num.Num;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 动态横盘箱体实体类。
 * <p>
 * 封装了一次横盘识别的完整结果，包含：
 * <ul>
 *   <li><b>价值区间（Value Range）：</b>由收盘价极值构成，代表多空最终达成的“共识价格区”，过滤盘中噪音。</li>
 *   <li><b>物理边界（Physical Boundary）：</b>由最高/最低价极值构成，代表价格实际触及过的“流动性极端区”，用于突破判定。</li>
 *   <li><b>横盘等级：</b>基于振幅细分为超窄、窄幅、宽幅三级。</li>
 * </ul>
 * </p>
 */
public class ConsolidationZone {

    // ==================== 箱体时间范围 ====================

    /** 箱体起始K线索引（动态回溯后的最终起始位） */
    private final int startIndex;

    /** 箱体结束K线索引（即调用 getValue() 时传入的索引） */
    private final int endIndex;

    // 【新增】箱体的起止时间
    private final Instant startTime;
    private final Instant endTime;

    // ==================== 价值区间（收盘价共识区） ====================

    /** 价值区间上沿：观察窗口内所有K线收盘价的最高值 */
    private final Num valueHigh;

    /** 价值区间下沿：观察窗口内所有K线收盘价的最低值 */
    private final Num valueLow;

    // ==================== 物理边界（影线流动性区） ====================

    /** 物理边界上沿：观察窗口内所有K线最高价的最高值（真正的阻力位） */
    private final Num physicalHigh;

    /** 物理边界下沿：观察窗口内所有K线最低价的最低值（真正的支撑位） */
    private final Num physicalLow;

    // ==================== 横盘质量评估 ====================

    /** 横盘强度等级（超窄/窄幅/宽幅） */
    private final ConsolidationLevel level;

    /** 实际计算出的振幅百分比（按所选归一化策略） */
    private final double amplitudePercent;

    // ==================== 构造方法 ====================

    // 构造函数更新，增加 startTime 和 endTime 参数
    public ConsolidationZone(int startIndex, int endIndex,
                             Instant startTime, Instant endTime,  // 新增两个参数
                             Num valueHigh, Num valueLow,
                             Num physicalHigh, Num physicalLow,
                             ConsolidationLevel level, double amplitudePercent) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.startTime = startTime;
        this.endTime = endTime;
        this.valueHigh = valueHigh;
        this.valueLow = valueLow;
        this.physicalHigh = physicalHigh;
        this.physicalLow = physicalLow;
        this.level = level;
        this.amplitudePercent = amplitudePercent;

    }

    // ==================== 核心业务方法 ====================

    /**
     * 判断横盘箱体是否被突破。
     * <p>
     * <b>判定逻辑：</b>新K线的最高价 > 物理边界上沿 <b>或</b> 新K线的最低价 < 物理边界下沿。
     * </p>
     * <p>
     * <b>为什么用物理边界（影线）而非价值区间？</b>
     * 因为影线触及的位置是真实存在的流动性堆叠区（止损单/挂单），
     * 一旦被刺穿，意味着这些流动性被吸收，趋势大概率启动。
     * </p>
     *
     * @param newHigh 新K线的最高价
     * @param newLow  新K线的最低价
     * @return true 表示横盘被有效突破，false 表示仍在箱体内部
     */
    public boolean isBroken(Num newHigh, Num newLow) {
        return newHigh.isGreaterThan(physicalHigh) || newLow.isLessThan(physicalLow);
    }

    /**
     * 判断是否为窄幅横盘（超窄 或 标准窄幅）。
     * <p>
     * <b>用途：</b>窄幅横盘适合采用突破跟随策略。
     * </p>
     *
     * @return true 表示窄幅，false 表示宽幅或无效
     */
    public boolean isNarrow() {
        return level == ConsolidationLevel.ULTRA_TIGHT || level == ConsolidationLevel.TIGHT;
    }

    /**
     * 判断是否为宽幅横盘（震荡）。
     * <p>
     * <b>用途：</b>宽幅横盘适合采用高抛低吸的均值回归策略。
     * </p>
     *
     * @return true 表示宽幅横盘
     */
    public boolean isWide() {
        return level == ConsolidationLevel.WIDE;
    }

    // ==================== Getters ====================

    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public Num getValueHigh() { return valueHigh; }
    public Num getValueLow() { return valueLow; }
    public Num getPhysicalHigh() { return physicalHigh; }
    public Num getPhysicalLow() { return physicalLow; }
    public ConsolidationLevel getLevel() { return level; }
    public double getAmplitudePercent() { return amplitudePercent; }

    // 新增 Getters
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }

    /**
     * 格式化输出时间范围（转为本地时区，便于阅读）
     */
    public String getTimeRange() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());  // 自动转为系统时区
        return formatter.format(startTime) + " ~ " + formatter.format(endTime);
    }

    @Override
    public String toString() {
        return String.format("ConsolidationZone [%s], startIdx=%d, endIdx=%d, valueRange=[%.2f, %.2f], level=%s, amp=%.2f%%",
                getTimeRange(),
                startIndex, endIndex,
                valueLow.doubleValue(), valueHigh.doubleValue(),
                level, amplitudePercent);
    }
}
