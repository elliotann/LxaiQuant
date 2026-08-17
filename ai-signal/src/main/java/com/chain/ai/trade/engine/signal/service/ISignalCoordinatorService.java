package com.chain.ai.trade.engine.signal.service;

import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.GenerateTradeSignalRequest;
import com.chain.ai.trade.engine.signal.entity.dto.GenerateTradeSignalResponse;
import com.chain.ai.trade.engine.signal.enums.TechSignal;

/**
 * 信号协调服务接口
 * 负责技术信号到业务信号的转换和协调
 */
public interface ISignalCoordinatorService {

    /**
     * 生成交易信号
     *
     * @param request 生成交易信号请求
     * @return 生成结果响应
     */
    GenerateTradeSignalResponse generateTradeSignal(GenerateTradeSignalRequest request);

    /**
     * 处理技术信号，生成业务信号
     *
     * @param technicalSignal 技术信号
     * @return 生成的业务信号，如果不需要生成则返回null
     */
    TradeSignal processTechnicalSignal(TechnicalSignal technicalSignal);

    /**
     * 根据技术信号类型和业务逻辑判断是否需要生成交易信号
     *
     * @param techSignal 技术信号类型
     * @param symbol 币种
     * @param currentPrice 当前价格
     * @return 是否需要生成交易信号
     */
    boolean shouldGenerateTradeSignal(TechSignal techSignal, String symbol, Double currentPrice);

    /**
     * 计算订单数量
     *
     * @param technicalSignal 技术信号
     * @param availableBalance 可用余额
     * @param currentPrice 当前价格
     * @return 订单数量
     */
    Double calculateOrderAmount(TechnicalSignal technicalSignal, Double availableBalance, Double currentPrice);

    /**
     * 应用风控规则
     *
     * @param tradeSignal 待处理的交易信号
     * @return 处理后的交易信号，如果被风控拒绝则返回null
     */
    TradeSignal applyRiskControl(TradeSignal tradeSignal);

    /**
     * 检查仓位管理规则
     *
     * @param tradeSignal 待处理的交易信号
     * @return 处理后的交易信号
     */
    TradeSignal applyPositionManagement(TradeSignal tradeSignal);

    /**
     * 生成订单号
     *
     * @param symbol 币种
     * @param techSignal 技术信号类型
     * @return 订单号
     */
    String generateOrderSn(String symbol, TechSignal techSignal);

    /**
     * 生成订单号（基于订单操作）
     *
     * @param symbol 币种
     * @param orderAction 订单操作
     * @return 订单号
     */
    String generateOrderSn(String symbol, OrderAction orderAction);

    /**
     * 计算预期收益
     *
     * @param tradeSignal 交易信号
     * @return 预期收益
     */
    Double calculateExpectedIncome(TradeSignal tradeSignal);
}
