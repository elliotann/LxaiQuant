package com.chain.ai.trade.engine2.core;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.backtest.BacktestResult;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;

/**
 * 交易引擎抽象模板 — 定义引擎执行的生命周期骨架。
 * <p>
 * 子类只需重写 {@link #executeLoop()} 实现核心循环逻辑，
 * 并通过 {@link #beforeRun()} / {@link #afterRun()} 等钩子方法插入差异化行为。
 */
@Slf4j
public abstract class AbstractEngine {

    /** K 线数据序列 */
    protected final BarSeries series;

    /** 交易策略 */
    protected final ScriptStrategy strategy;

    // ========== 信号频率控制状态 ==========
    private boolean signalFreqEnabled;
    private String signalFreqGranularity = "15min";
    private String signalFreqMode = "structure_upgrade_exempt";
    private SignalType lastSignalDirection;
    private long lastSignalTimestamp;

    protected AbstractEngine(BarSeries series, ScriptStrategy strategy) {
        this.series = series;
        this.strategy = strategy;
    }

    /**
     * 初始化信号频率控制参数（由外部调用，传入配置值）。
     */
    public void initSignalFrequency(boolean enabled, String granularity, String mode) {
        this.signalFreqEnabled = enabled;
        this.signalFreqGranularity = granularity != null ? granularity : "15min";
        this.signalFreqMode = mode != null ? mode : "structure_upgrade_exempt";
    }

    /**
     * 检查同向信号是否允许入场。入场成功时自动记录本次信号。
     *
     * @return true=允许入场, false=频率拦截
     */
    protected boolean allowSignal(SignalType direction, long barTimestamp) {
        if (!signalFreqEnabled || "unlimited".equals(signalFreqMode)) {
            return true;
        }
        if (lastSignalDirection == null) {
            recordSignal(direction, barTimestamp);
            return true;
        }
        long elapsed = barTimestamp - lastSignalTimestamp;
        if (direction == lastSignalDirection && elapsed < getSignalMinIntervalMs()) {
            log.debug("信号频率拦截: direction={}, elapsed={}ms", direction, elapsed);
            return false;
        }
        recordSignal(direction, barTimestamp);
        return true;
    }

    private void recordSignal(SignalType direction, long timestamp) {
        this.lastSignalDirection = direction;
        this.lastSignalTimestamp = timestamp;
    }

    private long getSignalMinIntervalMs() {
        switch (signalFreqGranularity) {
            case "3min":  return 3 * 60 * 1000L;
            case "1hour": return 60 * 60 * 1000L;
            default:      return 15 * 60 * 1000L;
        }
    }

    /**
     * 模板方法 — 定义引擎执行的生命周期骨架，子类不可重写。
     * <ol>
     *   <li>策略初始化 — {@link #initStrategy()}</li>
     *   <li>前置处理 — {@link #beforeRun()}</li>
     *   <li>主循环 — {@link #executeLoop()}（抽象方法，子类实现）</li>
     *   <li>后置处理 — {@link #afterRun()}（默认强制平仓）</li>
     *   <li>策略销毁 — {@link #destroyStrategy()}</li>
     *   <li>构建结果 — {@link #buildResult()}（抽象方法，子类实现）</li>
     * </ol>
     *
     * @return 回测/运行结果
     */
    public final BacktestResult run() {
        // 1. 策略初始化
        initStrategy();

        // 2. 前置处理
        beforeRun();

        // 3. 主循环（子类实现差异化逻辑：回测为 for 循环，实盘为事件驱动）
        executeLoop();

        // 4. 后置处理
        afterRun();

        // 5. 策略销毁
        destroyStrategy();

        // 6. 构建结果
        return buildResult();
    }

    /**
     * 策略初始化 — 子类可覆盖以传入自定义 StrategyContext。
     */
    protected void initStrategy() {
        strategy.init(null);
    }

    /**
     * 前置处理（子类可选覆盖）。
     */
    protected void beforeRun() {
        // 子类可按需实现
    }

    /**
     * 主循环 — 子类必须实现。
     * <p>
     * BacktestEngine：for 循环遍历每根 K 线，调用 strategy.shouldEntry / shouldExit<br>
     * LiveEngine：订阅 K 线事件，逐 Bar 触发策略（待实现）
     */
    protected abstract void executeLoop();

    /**
     * 后置处理（子类可选覆盖），默认执行强制平仓、持久化 flush 等收尾操作。
     */
    protected void afterRun() {
        // 子类可按需实现
    }

    /**
     * 策略销毁（子类可选覆盖）。
     */
    protected void destroyStrategy() {
        strategy.destroy();
    }

    /**
     * 构建结果 — 子类必须实现。
     *
     * @return 回测/运行结果
     */
    protected abstract BacktestResult buildResult();
}
