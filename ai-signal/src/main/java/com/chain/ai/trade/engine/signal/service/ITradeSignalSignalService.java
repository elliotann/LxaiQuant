package com.chain.ai.trade.engine.signal.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.KlineQueryParam;
import com.chain.ai.trade.engine.signal.entity.dto.TradeSignalSignalDto;
import com.chain.ai.trade.engine.signal.entity.vo.SignalSaveReqVO;
import com.chain.ai.trade.engine.signal.entity.vo.TradeSignalSignalVo;


import java.util.List;

public interface ITradeSignalSignalService extends IService<TradeSignalSignal> {

  /**
   * 创建交易信号
   *
   * @param createReqVO 创建信息
   * @return 编号
   */
  Long createSignal(SignalSaveReqVO createReqVO);



    /**
     * 获取指定 k 线是否有信号
     * @param weightsDto
     * @param candlestick
     * @return
     */
    TradeSignalSignal querySignal(TradeSignalSignalDto weightsDto, Candlestick candlestick);

  /**
     * 获取指定 k 线是否有信号
     * @param weightsDto
     * @param candlestick
     * @return
     */
    TradeSignalSignal querySignal(TradeSignalSignalDto weightsDto, Candlestick candlestick, List<CandlestickIntervalEnum> intervalEnumList);

    /**
     * 查询信号,不查平仓信号
     *
     * @param
     */
    TradeSignalSignal querySignal(String dataFrom, String dataInterval, String symbol, String indicatorType, String klineTime);

    /**
     * 查询信号
     *
     * @param
     */
    TradeSignalSignal querySignal(String dataFrom, String symbol, String indicatorType, String klineTime);


    /**
     * 查询指定时间之前的信号
     *
     * @param dataFrom
     * @param dataInterval
     * @param symbol
     * @param indicatorType
     * @param klineTime
     * @return
     */
    TradeSignalSignal queryBeforeSignal(String dataFrom, String dataInterval, String symbol, String indicatorType, String klineTime);

  /**
   * 查询最近的开仓信号
   *
   * @param dataFrom 数据来源
   * @param symbol 币种
   * @param indicatorType 指标类型
   * @return 最近的开仓信号（LB-开多 或 SB-开空）
   */
  TradeSignalSignal queryLatestOpenSignal(String dataFrom, String symbol, String indicatorType);

  /**
   * 查询指定机器人最近的开仓信号
   *
   * @param dataFrom 数据来源
   * @param symbol 币种
   * @param indicatorType 指标类型
   * @param robotId 机器人ID
   * @return 指定机器人的最近开仓信号（LB-开多 或 SB-开空）
   */
  TradeSignalSignal queryLatestOpenSignal(String dataFrom, String symbol, String indicatorType, String robotId);

  /**
   * 查询指定时间之前的最新开仓信号
   *
   * @param dataFrom 数据来源
   * @param symbol 币种
   * @param indicatorType 指标类型
   * @param robotId 机器人ID（目前未使用，用于保持接口一致性）
   * @param beforeTime 时间点（查询此时间之前的最新信号）
   * @return 指定时间之前的最新开仓信号（LB-开多 或 SB-开空）
   */
  TradeSignalSignal queryLatestOpenSignalBeforeTime(String dataFrom, String symbol, String indicatorType, String robotId, String beforeTime);

  /**
   * 查询最近N条该机器人的信号列表
   *
   * @param dataFrom 数据来源
   * @param dataInterval 数据周期
   * @param symbol 币种
   * @param indicatorType 指标类型
   * @param limit 查询数量限制
   * @return 最近的信号列表（按时间倒序）
   */
  List<TradeSignalSignal> queryRecentSignals(String dataFrom, String dataInterval, String symbol, String indicatorType, int limit);



    /**
     * 保存订单信号数据
     *
     * @param weightsDto
     * @param candlestick
     * @return
     */
    void saveOrderToSignal(TradeSignalSignalDto tradeOrder, Boolean buyFlag, String indicatorType);

  /**
   * 保存订单信号数据
   *
   * @param weightsDto
   * @param candlestick
   * @return
   */
  void saveOrderSignal(TradeSignalSignalDto tradeOrder, OrderAction orderAction, String indicatorType, Candlestick candlestick);

  /**
   * 条件查询信号
   * @param param
   * @return
   */
    List<TradeSignalSignalVo> getSignalByQry(KlineQueryParam param);

  /**
   * 获取当前时间K,y用于获取订单动作
   * @param klinetime
   * @return
   */
  public List<TradeSignalSignal> getOrderActionSignals(KlineQueryParam param);

}
