package com.chain.ai.trade.engine.signal.feature;

import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureSnapshot;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 滑动窗口 —— 特征工程核心数据结构
 * <p>
 * 职责：
 * 1. 维护最近 maxSize 笔交替记录的 space 和 direction
 * 2. 提供基础统计值：avgSpace、cumRatio、directionSeq 等
 * 3. 提供动态分位数计算（替代固定阈值）
 * 4. 实盘和回测使用完全相同的逻辑
 */
@Getter
public class SlidingWindow {

    private final Queue<Double> spaceQueue = new LinkedList<>();
    private final Queue<Double> absSpaceQueue = new LinkedList<>();
    private final Queue<String> directionQueue = new LinkedList<>();
    private final int maxSize;

    private double cumSum = 0.0;
    private double absSum = 0.0;
    private long lastSignalTime = 0L;
    private double latestSpace = 0.0;
    private String lastDirection = null;
    private int directionSeq = 0;

    public SlidingWindow(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * 添加一笔交替记录，更新滑动窗口（实盘与回测逻辑一致）
     */
    public void add(SignalAlternateLog record) {
        double space = record.getSpacePct().doubleValue();
        double absSpace = Math.abs(space);
        String direction = record.getEntryDirection();

        spaceQueue.add(space);
        absSpaceQueue.add(absSpace);
        directionQueue.add(direction);
        cumSum += space;
        absSum += absSpace;
        lastSignalTime = record.getEntryTime();
        latestSpace = space;

        if (direction.equals(lastDirection)) {
            directionSeq++;
        } else {
            directionSeq = 1;
        }
        lastDirection = direction;

        // 超出窗口大小则移除最旧记录并重算累计值
        if (spaceQueue.size() > maxSize) {
            spaceQueue.poll();
            absSpaceQueue.poll();
            directionQueue.poll();
            recalcFromQueue();
        }
    }

    /**
     * 从队列重算累计和与连续同向序列（窗口移除最旧记录后调用）
     */
    private void recalcFromQueue() {
        cumSum = spaceQueue.stream().mapToDouble(Double::doubleValue).sum();
        absSum = absSpaceQueue.stream().mapToDouble(Double::doubleValue).sum();
        directionSeq = recalcDirectionSeq();
    }

    /**
     * 从队列尾部往前统计连续同向笔数
     */
    private int recalcDirectionSeq() {
        if (directionQueue.isEmpty()) {
            return 0;
        }
        int count = 1;
        String[] dirs = directionQueue.toArray(new String[0]);
        for (int i = dirs.length - 2; i >= 0; i--) {
            if (dirs[i].equals(dirs[i + 1])) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 平均绝对空间（%）
     */
    public double getAvgSpace() {
        return absSum / Math.max(1, spaceQueue.size());
    }

    /**
     * 累积比：SUM(space) / SUM(abs_space)，判断趋势/震荡
     */
    public double getCumRatio() {
        return absSum == 0 ? 0 : cumSum / absSum;
    }

    /**
     * 当前窗口大小
     */
    public int getSize() {
        return spaceQueue.size();
    }

    /**
     * 计算 abs_space 的指定分位数（percentile 取值 0~1）
     */
    public double getAbsSpacePercentile(double percentile) {
        if (absSpaceQueue.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(absSpaceQueue);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /**
     * 计算 space 的指定分位数（percentile 取值 0~1）
     */
    public double getSpacePercentile(double percentile) {
        if (spaceQueue.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(spaceQueue);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /**
     * 获取基础特征快照
     */
    public FeatureSnapshot getSnapshot() {
        return new FeatureSnapshot(getAvgSpace(), getCumRatio(), directionSeq,
                lastSignalTime, latestSpace, lastDirection);
    }

    /**
     * 获取完整统计信息（含动态分位数）
     */
    public FeatureStatistics getFullStatistics() {
        FeatureStatistics stats = new FeatureStatistics();
        stats.setAvgSpace(getAvgSpace());
        stats.setCumRatio(getCumRatio());
        stats.setDirectionSeq(directionSeq);
        stats.setLastSignalTime(lastSignalTime);
        stats.setLatestSpace(latestSpace);
        stats.setLastDirection(lastDirection);
        stats.setWindowSize(getSize());
        stats.setPercentile_20(getAbsSpacePercentile(0.20));
        stats.setPercentile_40(getAbsSpacePercentile(0.40));
        stats.setPercentile_70(getAbsSpacePercentile(0.70));
        stats.setPercentile_85(getAbsSpacePercentile(0.85));
        stats.setPercentile_95(getAbsSpacePercentile(0.95));
        stats.setCumRatioPercentile_40(getSpacePercentile(0.40));
        stats.setCumRatioPercentile_60(getSpacePercentile(0.60));
        return stats;
    }
}
