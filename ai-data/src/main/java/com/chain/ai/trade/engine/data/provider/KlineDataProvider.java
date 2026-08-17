package com.chain.ai.trade.engine.data.provider;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * K线数据提供者接口
 * 封装K线数据获取逻辑，支持实盘和测试两种模式
 */
public interface KlineDataProvider {

    /**
     * 获取初始历史K线数据（用于策略指标计算）
     * 在构建策略之前调用，加载指定数量的历史K线
     *
     * @param params 交易策略参数
     * @param count 需要获取的K线数量（默认1000根）
     * @return BarSeries对象，包含历史K线数据
     */
    BarSeries fetchInitialKlines(TradingStrategyParams params, int count);

    /**
     * 获取下一根K线数据
     *
     * @param params 交易策略参数
     * @return K线数据（Bar对象），如果没有更多数据则返回null
     */
    Bar fetchNextKline(TradingStrategyParams params);

    /**
     * 是否支持测试模式
     *
     * @return true if this provider supports test mode
     */
    boolean isTestMode();

    /**
     * 重置提供者状态（用于测试模式重新开始）
     */
    default void reset() {
        // 默认实现为空，子类可以重写
    }

    /**
     * 批量获取所有K线数据（用于回测批量处理，提升性能）
     * 仅在测试模式下使用，实盘模式返回null
     *
     * @param params 交易策略参数（必须包含startTime和endTime）
     * @return BarSeries对象，包含从startTime到endTime的所有K线数据，如果不可用则返回null
     */
    default BarSeries fetchAllKlines(TradingStrategyParams params) {
        // 默认实现返回null，表示不支持批量获取（实盘模式）
        // 测试模式的实现类应该重写此方法
        return null;
    }

    /**
     * 获取小于等于指定ID的指定数量K线数据
     * @param param 数量
     * @return K线数据列表
     */
    List<Candlestick> listByLeId(KlineParam param);
}
