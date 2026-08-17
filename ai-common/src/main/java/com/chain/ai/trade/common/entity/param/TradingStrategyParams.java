package com.chain.ai.trade.common.entity.param;

import com.chain.ai.trade.common.entity.constants.Exchange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.knowm.xchange.dto.Order;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 交易策略参数
 * 用于传递策略执行时的参数信息给订单服务
 */

/**
 * 交易策略参数
 * 用于传递策略执行时的参数信息给订单服务
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TradingStrategyParams {
    private String apiKey;

    private String secretKey;

    private String passphrase;
    private String strategyId;
    /**
     * 是否模拟账户（沙箱环境）
     * true: 模拟账户，使用沙箱环境进行交易操作
     * false: 真实账户，使用真实环境进行交易操作
     */
    private Boolean simulated;
    /**
     * 交易对符号，如 BTC-USDT, ETH-USDT
     */
    private String symbol;

    /**
     * 策略Bean名称，用于从Spring容器中获取策略实例
     */
    @Builder.Default
    private String strategyBeanName = "RSI";

    /**
     * 时间间隔，如 1h, 4h, 1d
     */
    @Builder.Default
    private String interval = "1h";

    /**
     * 账户ID
     */
    private String accountId;

    /**
     * 交易方向: BUY, SELL
     */
    private String side;

    /**
     * 交易数量
     */
    private BigDecimal amount;

    /**
     * 交易价格
     */
    private BigDecimal price;

    /**
     * 止盈价格
     */
    private BigDecimal takeProfitPrice;

    /**
     * 止损价格
     */
    private BigDecimal stopLossPrice;

    /**
     * 止盈百分比 (e.g., 0.1 for 10%)
     */
    private BigDecimal takeProfitPercentage;

    /**
     * 止损百分比 (e.g., 0.05 for 5%)
     */
    private BigDecimal stopLossPercentage;

    /**
     * 杠杆倍数
     */
    private Integer leverage;

    /**
     * 机器人ID
     */
    private String robotId;
    private String testReportId;

    /**
     * 仓位id（用于补仓时指定具体订单）
     */
    private String positionId;

    /**
     * V2引擎客户端订单ID — 新开仓时作为 DB orderSn，同时作为 MemoryPosition 的 positionId
     */
    private String clientOrderId;

    /**
     * 是否启用双向持仓模式
     * true: 允许同时持有多头和空头仓位（由引擎决定）
     * false: 只允许单向持仓（传统模式）
     */
    @Builder.Default
    private Boolean bidirectionalEnabled = true;

    /**
     * 是否允许补仓操作（由引擎决定）
     * true: 允许对现有持仓进行补仓
     * false: 严格禁止补仓操作
     */
    @Builder.Default
    private Boolean allowAddPosition = true;

    /**
     * 初次盈利加仓幅度（策略阈值，内部使用 0~1 小数；例如 0.05 表示 5%）
     * 前端通常以 0~100 的百分比输入（例如 5 表示 5%），进入引擎前会归一化为 0.05
     */
    private Double addPosOnProfitPct;

    /**
     * 初次亏损补仓幅度（策略阈值，内部使用 0~1 小数；例如 0.05 表示 5%）
     * 前端通常以 0~100 的百分比输入（例如 5 表示 5%），进入引擎前会归一化为 0.05
     */
    private Double addPosOnLossPct;

    /**
     * 盈利加仓间隔（相对上一次入场价的额外幅度，内部使用 0~1 小数；例如 0.01 表示 1%）
     * 前端通常以 0~100 的百分比输入（例如 1 表示 1%），进入引擎前会归一化为 0.01
     */
    private Double addPosOnProfitGapPct;

    /**
     * 亏损补仓间隔（相对上一次入场价的额外幅度，内部使用 0~1 小数；例如 0.01 表示 1%）
     * 前端通常以 0~100 的百分比输入（例如 1 表示 1%），进入引擎前会归一化为 0.01
     */
    private Double addPosOnLossGapPct;

    /**
     * 其他可选参数
     */
    private Map<String, Object> additionalParams;

    /**
     * 开始时间（时间戳，毫秒）
     * 用于测试模式，指定历史数据的开始时间
     * 如果为空，实盘模式从当前时间开始
     */
    private Long startTime;

    /**
     * 结束时间（时间戳，毫秒）
     * 用于测试模式，指定历史数据的结束时间
     * 如果为空，实盘模式持续运行
     */
    private Long endTime;

    /**
     * 是否测试模式
     * true: 测试模式，不发送真实请求到交易所，仅做系统内部订单操作
     * false: 实盘模式，正常发送请求到交易所
     */
    @Builder.Default
    private Boolean testMode = false;

    /**
     * 手续费率（回测模式使用，前端传入；为空时使用平台默认费率）
     */
    private BigDecimal commissionRate;

    /**
     * 订单时间（用于测试模式，从K线数据获取）
     * 如果为空，使用系统当前时间
     */
    private java.util.Date orderTime;

    private Order.OrderType orderType;

    private Exchange memberPlatform;

    /**
     * 入场类型：MARKET(市价) / LIMIT(限价)
     */
    private com.chain.ai.trade.common.entity.constants.OrderPriceType entryType;

    /**
     * 限价单价格
     */
    private BigDecimal limitPrice;

    /**
     * 技术信号ID
     */
    private Long technicalSignalId;

    /**
     * 技术信号哈希
     */
    private String technicalSignalHash;

    /**
     * 技术信号摘要（如MACD金叉）
     */
    private String technicalSignalBrief;
}
