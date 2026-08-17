package com.chain.ai.trade.engine.xchange;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.xchange.dto.MarketDepth;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易所交易服务接口
 * 基于Xchange实现与各大交易所的交互
 */
public interface ExchangeTradeService {

    /**
     * 创建订单
     *
     * @param params 交易参数
     * @return 订单ID
     */
    String createOrder(TradingStrategyParams params);

    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @param symbol  交易对
     * @return 是否成功
     */
    boolean cancelOrder(String orderId, String symbol);

    /**
     * 取消所有订单
     *
     * @param symbol 交易对，为空则取消所有交易对的订单
     * @return 取消的订单ID列表
     */
    List<String> cancelAllOrders(String symbol);

    /**
     * 获取订单状态
     *
     * @param orderId 订单ID
     * @return 订单状态
     */
    String getOrderStatus(String orderId);

    /**
     * 获取账户余额
     *
     * @param currency 货币类型，为空则返回所有余额
     * @return 余额
     */
    BigDecimal getAccountBalance(String currency);

    /**
     * 获取当前市场价格
     *
     * @param symbol 交易对
     * @return 当前价格
     */
    BigDecimal getCurrentPrice(String symbol);

    /**
     * 获取市场深度
     *
     * @param symbol 交易对
     * @param depth  深度数量
     * @return 市场深度数据
     */
    MarketDepth getMarketDepth(String symbol, int depth);

    /**
     * 设置杠杆倍数
     *
     * @param symbol   交易对
     * @param leverage 杠杆倍数
     * @return 是否成功
     */
    boolean setLeverage(String symbol, int leverage);

    /**
     * 平仓所有持仓
     *
     * @param symbol 交易对
     * @return 是否成功
     */
    boolean closeAllPositions(String symbol);

    /**
     * 获取K线数据（最近/实时，或带时间范围时由实现决定走 candles 或 history）
     *
     * @param request K线请求参数
     * @return K线数据列表
     */
    List<Candlestick> getCandlestick(CandlestickRequest request);

    /**
     * 专门获取历史K线数据（走交易所 history-candles 等历史接口，单次通常最多 100 条）
     * 请求中 from/to 为秒级时间戳，必填
     *
     * @param request K线请求参数，from、to 必填（秒）
     * @return K线数据列表，时间从旧到新
     */
    List<Candlestick> getHistoryCandlestick(CandlestickRequest request);

    /**
     * 修改止盈止损价格
     *
     * @param orderSn   订单编号（用于匹配条件单的 algoClOrdId）
     * @param symbol    交易对
     * @param gainPrice 止盈价格，null 表示不修改
     * @param lossPrice 止损价格，null 表示不修改
     * @return 是否成功
     */
    boolean amendTpSl(String orderSn, String symbol, BigDecimal gainPrice, BigDecimal lossPrice);
}

