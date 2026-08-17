package com.chain.ai.trade.engine.signal.feature;

import org.springframework.stereotype.Component;

/**
 * 回测时间上下文持有器（ThreadLocal）
 * <p>
 * 回测离线重算时，由回测引擎在重放每笔历史信号前调用 {@link #setCurrentTime(long)} 注入当前回测时间戳；
 * 实盘场景不注入，{@code DefaultSignService} 通过 {@code isBacktest()} 判断后回退 {@code System.currentTimeMillis()}。
 */
@Component
public class BacktestContextHolder {

    private final ThreadLocal<Long> currentTime = new ThreadLocal<>();

    /**
     * 设置当前线程的回测时间戳（毫秒）
     */
    public void setCurrentTime(long currentTimeMs) {
        currentTime.set(currentTimeMs);
    }

    /**
     * 获取当前线程的回测时间戳（毫秒），未注入时返回 null
     */
    public Long getCurrentTime() {
        return currentTime.get();
    }

    /**
     * 清除当前线程的回测时间戳，防止线程复用污染
     */
    public void clear() {
        currentTime.remove();
    }
}
