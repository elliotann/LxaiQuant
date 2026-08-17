package com.chain.ai.trade.order.service;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.dto.BacktestTradeRecord;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.entity.dos.TradeExitBatch;
import com.chain.ai.trade.order.entity.dos.TradeExitItem;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.entity.dto.ClosePositionResult;
import com.chain.ai.trade.order.entity.dto.OrderQueryDTO;
import com.chain.ai.trade.order.entity.dto.PartialCloseResult;
import com.chain.ai.trade.order.entity.dto.SmartCloseResult;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.entity.vo.PageVO;
import com.chain.ai.trade.order.entity.vo.RobotOrderReportVO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 交易订单服务接口
 * 与ai-engine模块的IDealStrategyTrade对接
 * 增强版：新增精确平仓接口
 */
public interface ITradeOrderService {

    // ==================== 原有基础接口 ====================

    /**
     * 创建订单
     * 对应IDealStrategyTrade.createOrder()
     *
     * @param params 交易策略参数
     * @return 订单ID
     */
    String createOrder(TradingStrategyParams params);

    /**
     * 止盈订单
     * 对应IDealStrategyTrade.stopWinOrder()
     *
     * @param params 交易策略参数
     * @return 是否成功
     */
    boolean stopWinOrder(TradingStrategyParams params);

    /**
     * 止损订单
     * 对应IDealStrategyTrade.stopLossOrder()
     *
     * @param params 交易策略参数
     * @return 是否成功
     */
    boolean stopLossOrder(TradingStrategyParams params);

    /**
     * 补仓订单
     * 对应IDealStrategyTrade.suppOrder()
     *
     * @param params 交易策略参数
     * @return 是否成功
     */
    boolean suppOrder(TradingStrategyParams params);

    /**
     * 根据订单ID查询订单状态
     *
     * @param orderId 订单ID
     * @return 订单状态
     */
    String getOrderStatus(String orderId);

    /**
     * 根据订单号查询订单
     *
     * @param orderSn 订单编号
     * @return 订单对象，如果不存在返回null
     */
    TradePosition getOrderByOrderSn(String orderSn);

    /**
     * 取消订单
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(String orderId);

    /**
     * 获取账户持仓信息
     *
     * @param accountId 账户ID
     * @param symbol 交易对
     * @return 持仓数量
     */
    double getPositionSize(Long accountId, String symbol);

    /**
     * 获取账户可用余额
     *
     * @param accountId 账户ID
     * @return 可用余额
     */
    double getAvailableBalance(String accountId);

    /**
     * 根据数量平仓（部分平仓）
     *
     * @param orderSn 订单编号
     * @param closeVolume 平仓数量
     * @return 是否成功
     */
    boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume);

    /**
     * 根据数量平仓（部分平仓），指定平仓价格
     *
     * @param orderSn 订单编号
     * @param closeVolume 平仓数量
     * @param currentPrice 当前价格（用于测试模式，从K线数据获取）
     * @return 是否成功
     */
    boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume, BigDecimal currentPrice);

    /**
     * 根据数量平仓（部分平仓），指定平仓价格和平仓时间
     *
     * @param orderSn 订单编号
     * @param closeVolume 平仓数量
     * @param currentPrice 当前价格（用于测试模式，从K线数据获取）
     * @param closeTime 平仓时间（用于测试模式，从K线数据获取；如果为null，则使用当前时间）
     * @return 是否成功
     */
    boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume, BigDecimal currentPrice, Date closeTime, ExitType exitType);

    /**
     * 根据订单号平仓（整个订单全部平仓）
     *
     * @param orderSn 订单编号
     * @return 是否成功
     */
    boolean closeOrderByOrderSn(String orderSn);

    /**
     * 根据订单项号平仓（单个订单项全部平仓）
     *
     * @param orderItemSn 订单项编号
     * @return 是否成功
     */
    boolean closeOrderByOrderItemSn(String orderItemSn);

    /**
     * 保存回测交易记录
     * 将回测中的开仓和平仓记录分别保存到TradeOrder、TradeOrderItem和TradeOrderClose中
     *
     * @param backtestTradeRecords 回测交易记录列表
     * @param memberId 会员ID
     * @param accountId 账户ID
     * @param robotId 机器人ID
     * @param symbol 交易对
     * @return 保存成功的记录数量
     */
    int saveBacktestTradeRecords(List<BacktestTradeRecord> backtestTradeRecords,
                                 String memberId, Long accountId, String robotId, String symbol,
                                 Integer leverage, Exchange exchange);

    /**
     * 分页查询订单列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageVO<OrderVO> queryOrders(OrderQueryDTO queryDTO);

    /**
     * 查询持仓中的订单列表
     *
     * @param accountId 账户ID
     * @param symbol 交易对
     * @return 持仓订单列表
     */
    List<OrderVO> getPositionOrders(String accountId, String symbol);

    /**
     * 查询待成交的订单列表
     */
    List<OrderVO> getPendingOrders(String accountId, String symbol);

    /**
     * 根据订单号查询订单项列表
     *
     * @param orderSn 订单编号
     * @return 订单项列表
     */
    java.util.List<TradeEntry> listOrderItemsByOrderSn(String orderSn);

    /**
     * 根据订单号查询平仓记录列表
     *
     * @param orderSn 订单编号
     * @return 平仓记录列表
     */
    java.util.List<TradeExitBatch> listOrderClosesByOrderSn(String orderSn);

    /**
     * 根据订单号查询平仓明细列表（带 orderItemSn 关联）
     *
     * @param orderSn 订单编号
     * @return 平仓明细列表
     */
    java.util.List<TradeExitItem> listOrderCloseItemsByOrderSn(String orderSn);

    /**
     * 更新订单状态
     */
    boolean updateOrderStatus(String orderSn, TradePosition.TradeOrderStatus status, java.util.Date fillTime);

    /**
     * 根据条件查询订单
     * @return 对应IDealStrategyTrade.createOrder()
     *
     * @param params 交易策略参数
     * @return 订单ID
     */
    List<TradePosition> getOrdersByQry(String rootId, String accountId, String symbol, OrderSideEnum orderSideEnum, Date orderTime);

    /**
     * 机器人订单收益报表（按日或月聚合）
     *
     * @param robotId 机器人ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param granularity 粒度：day 按日，month 按月
     * @return 报表数据（汇总 + 按期明细）
     */
    RobotOrderReportVO getRobotOrderReport(String robotId, Date startTime,
                                           Date endTime, String granularity);

    /**
     * 获取指定订单的剩余持仓数量
     *
     * @param orderSn 订单编号
     * @return 剩余持仓数量（张）
     */
    java.math.BigDecimal getRemainingPositionByOrderSn(String orderSn);

    // ==================== 新增精确平仓接口 ====================

    /**
     * 全仓平仓 - 平掉指定订单（仓位）的全部数量
     * 会精确计算持仓数量，处理小数精度问题，确保全部平仓
     *
     * @param orderSn 订单编号
     * @param currentPrice 平仓价格（从K线数据获取）
     * @param closeTime 平仓时间（从K线数据获取；如果为null，则使用当前时间）
     * @return 平仓结果
     */
    ClosePositionResult closeFullPosition(String orderSn, BigDecimal currentPrice, Date closeTime, ExitType exitType);

    /**
     * 全仓平仓 - 平掉指定订单（仓位）的全部数量（使用当前时间）
     *
     * @param orderSn 订单编号
     * @param currentPrice 平仓价格（从K线数据获取）
     * @return 平仓结果
     */
    default ClosePositionResult closeFullPosition(String orderSn, BigDecimal currentPrice) {
        return closeFullPosition(orderSn, currentPrice, null,null);
    }

    /**
     * 按数量平仓 - 平掉指定订单（仓位）的部分数量
     * 自动处理浮点数精度，如果平仓数量超过持仓数量，则平掉全部
     * 自动处理小尾巴问题
     *
     * @param orderSn 订单编号
     * @param targetCloseAmount 目标平仓数量
     * @param currentPrice 平仓价格（从K线数据获取）
     * @param closeTime 平仓时间（从K线数据获取；如果为null，则使用当前时间）
     * @return 平仓结果
     */
    PartialCloseResult closePartialPosition(String orderSn, BigDecimal targetCloseAmount,
                                            BigDecimal currentPrice, Date closeTime, ExitType exitType);

    /**
     * 按数量平仓 - 平掉指定订单（仓位）的部分数量（使用当前时间）
     *
     * @param orderSn 订单编号
     * @param targetCloseAmount 目标平仓数量
     * @param currentPrice 平仓价格（从K线数据获取）
     * @return 平仓结果
     */
    default PartialCloseResult closePartialPosition(String orderSn, BigDecimal targetCloseAmount,
                                                    BigDecimal currentPrice) {
        return closePartialPosition(orderSn, targetCloseAmount, currentPrice, null, null);
    }

    /**
     * 智能平仓 - 按数量平仓，自动处理尾数问题
     * 如果平仓后剩余数量小于最小交易单位或尾数阈值，则自动全平
     *
     * @param orderSn 订单编号
     * @param targetCloseAmount 目标平仓数量
     * @param currentPrice 平仓价格（从K线数据获取）
     * @param closeTime 平仓时间（从K线数据获取；如果为null，则使用当前时间）
     * @param minTradeAmount 最小交易数量（可空，为空则自动获取合约规格）
     * @param tailThreshold 尾数阈值（可空，默认0.000001）
     * @return 平仓结果
     */
    SmartCloseResult smartClosePosition(String orderSn, BigDecimal targetCloseAmount,
                                        BigDecimal currentPrice, Date closeTime,
                                        BigDecimal minTradeAmount, BigDecimal tailThreshold);

    /**
     * 智能平仓 - 简版（使用当前时间，默认参数）
     *
     * @param orderSn 订单编号
     * @param targetCloseAmount 目标平仓数量
     * @param currentPrice 平仓价格（从K线数据获取）
     * @return 平仓结果
     */
    default SmartCloseResult smartClosePosition(String orderSn, BigDecimal targetCloseAmount,
                                                BigDecimal currentPrice) {
        return smartClosePosition(orderSn, targetCloseAmount, currentPrice, null, null, null);
    }

    java.math.BigDecimal getNetProfitByRobotId(String robotId, Date startTime, Date endTime);
    java.math.BigDecimal getNetProfitByAccountId(String accountId, Date startTime, Date endTime);
    java.math.BigDecimal getCumulativeNetProfitByRobotId(String robotId);
    java.math.BigDecimal getCumulativeNetProfitByAccountId(String accountId);
}



