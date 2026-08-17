package com.chain.ai.trade.engine.signal.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;

import java.util.List;

/**
 * 交易信号（业务信号）服务接口
 */
public interface ITradeSignalService extends IService<TradeSignal> {

    /**
     * 创建交易信号
     *
     * @param tradeSignal 交易信号对象
     * @return 信号ID
     */
    Long createTradeSignal(TradeSignal tradeSignal);

    /**
     * 根据技术信号ID查询交易信号
     *
     * @param technicalSignalId 技术信号ID
     * @return 交易信号列表
     */
    List<TradeSignal> queryTradeSignalsByTechnicalSignalId(Long technicalSignalId);

    /**
     * 根据订单号查询交易信号
     *
     * @param orderSn 订单号
     * @return 交易信号
     */
    TradeSignal queryTradeSignalByOrderSn(String orderSn);

    /**
     * 根据状态查询交易信号
     *
     * @param status 信号状态
     * @return 交易信号列表
     */
    List<TradeSignal> queryTradeSignalsByStatus(TradeStatus status);

    /**
     * 根据币种和状态查询交易信号
     *
     * @param symbol 币种
     * @param status 信号状态
     * @return 交易信号列表
     */
    List<TradeSignal> queryTradeSignalsBySymbolAndStatus(String symbol, TradeStatus status);

    /**
     * 更新交易信号状态
     *
     * @param signalId 信号ID
     * @param status 新状态
     * @return 更新是否成功
     */
    boolean updateTradeSignalStatus(Long signalId, TradeStatus status);

    /**
     * 执行交易信号
     *
     * @param signalId 信号ID
     * @return 执行是否成功
     */
    boolean executeTradeSignal(Long signalId);

    /**
     * 取消交易信号
     *
     * @param signalId 信号ID
     * @param reason 取消原因
     * @return 取消是否成功
     */
    boolean cancelTradeSignal(Long signalId, String reason);

    /**
     * 查询待执行的交易信号
     *
     * @return 待执行的交易信号列表
     */
    List<TradeSignal> queryPendingTradeSignals();

    /**
     * 查询执行中的交易信号
     *
     * @return 执行中的交易信号列表
     */
    List<TradeSignal> queryExecutingTradeSignals();

    /**
     * 根据时间范围查询交易信号
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 交易信号列表
     */
    List<TradeSignal> queryTradeSignalsByTimeRange(String startTime, String endTime);

    /**
     * 根据交易对和时间范围查询交易信号
     *
     * @param symbol    交易对
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 交易信号列表
     */
    List<TradeSignal> queryTradeSignalsBySymbolAndTimeRange(String symbol, String startTime, String endTime);
}
