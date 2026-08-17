package com.chain.ai.trade.engine.service;

import cn.hutool.core.collection.CollUtil;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.entity.dto.ClosePositionResult;
import com.chain.ai.trade.order.entity.dto.PartialCloseResult;
import com.chain.ai.trade.order.service.ITradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 订单服务适配器
 * 用于ai-engine调用ai-order模块的服务
 */
@Service
@RequiredArgsConstructor
public class TradeOrderServiceAdapter {

    private final ITradeOrderService tradeOrderService;

    /**
     * 创建订单
     */
    public String createOrder(TradingStrategyParams params) {
        return tradeOrderService.createOrder(params);
    }

    /**
     * 止盈订单
     */
    public boolean stopWinOrder(TradingStrategyParams params) {
        return tradeOrderService.stopWinOrder(params);
    }

    /**
     * 止损订单
     */
    public boolean stopLossOrder(TradingStrategyParams params) {
        return tradeOrderService.stopLossOrder(params);
    }

    /**
     * 补仓订单
     */
    public boolean suppOrder(TradingStrategyParams params) {
        return tradeOrderService.suppOrder(params);
    }

    /**
     * 根据订单号查询订单项列表
     */
    public java.util.List<TradeEntry> listOrderItemsByOrderSn(String orderSn) {
        return tradeOrderService.listOrderItemsByOrderSn(orderSn);
    }



    /**
     * 获取持仓大小
     */
    public double getPositionSize(Long accountId, String symbol) {
        return tradeOrderService.getPositionSize(accountId, symbol);
    }

    /**
     * 获取可用余额
     * 上游（策略引擎）仍然使用 Long 类型的 accountId，这里适配为字符串传给订单服务
     */
    public double getAvailableBalance(Long accountId) {
        return tradeOrderService.getAvailableBalance(
                accountId != null ? String.valueOf(accountId) : null
        );
    }

    /**
     * 查询持仓中的订单列表
     */
    public java.util.List<com.chain.ai.trade.order.entity.vo.OrderVO> getPositionOrders(String accountId, String symbol) {
        return tradeOrderService.getPositionOrders(accountId, symbol);
    }

    /**
     * 查询挂单列表
     */
    public java.util.List<com.chain.ai.trade.order.entity.vo.OrderVO> getPendingOrders(String accountId, String symbol) {
        return tradeOrderService.getPendingOrders(accountId, symbol);
    }


    /**
     * 根据数量平仓（部分平仓），指定平仓价格和平仓时间
     */
    public boolean closeOrderByVolume(String orderSn, java.math.BigDecimal closeVolume, java.math.BigDecimal currentPrice, java.util.Date closeTime, ExitType exitType) {
        PartialCloseResult result = tradeOrderService.closePartialPosition(orderSn, closeVolume, currentPrice, closeTime, exitType);
        return result != null && result.isSuccess();
    }

    public boolean closePartialPosition(String orderSn, java.math.BigDecimal closeVolume, java.math.BigDecimal currentPrice, java.util.Date closeTime, ExitType exitType) {
        PartialCloseResult result = tradeOrderService.closePartialPosition(orderSn, closeVolume, currentPrice, closeTime, exitType);
        return result != null && result.isSuccess();
    }

    public boolean closeOrderByVolumeExact(String orderSn, java.math.BigDecimal closeVolume, java.math.BigDecimal currentPrice, java.util.Date closeTime, ExitType exitType) {
        return tradeOrderService.closeOrderByVolume(orderSn, closeVolume, currentPrice, closeTime, exitType);
    }

    public boolean updateOrderStatus(String orderSn, TradePosition.TradeOrderStatus status, java.util.Date fillTime) {
        return tradeOrderService.updateOrderStatus(orderSn, status, fillTime);
    }

    /**
     * 根据条件查询订单
     * 对应IDealStrategyTrade.createOrder()
     *
     * @param params 交易策略参数
     * @return 订单ID
     */
    public boolean getOrdersByQry(String rootId, String accountId, String symbol, OrderSideEnum orderSideEnum, Date orderTime){
        return CollUtil.isNotEmpty(tradeOrderService.getOrdersByQry(rootId,accountId,symbol,orderSideEnum,orderTime));
    }

    /**
     * 根据订单号查询订单（用于限价单成交检测）
     *
     * @param orderSn 订单编号
     * @return TradeOrder 对象，如果不存在返回 null
     */
    public TradePosition getOrderByOrderSn(String orderSn) {
        return tradeOrderService.getOrderByOrderSn(orderSn);
    }

}
