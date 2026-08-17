package com.chain.ai.trade.order.entity.constants;


import lombok.Getter;

/**
 * 订单项枚举
 */
@Getter
public enum TradeOrderEnum {


    /**
     * 订单项类型：OPEN（开仓），COVER（补仓）
     */
    ORDER_TYPE_OPEN("OPEN", "ORDER_TYPE", "开仓"),
    ORDER_TYPE_COVER("COVER", "ORDER_TYPE", "补仓"),


    /**
     * 主订单状态：运行中：RUNNING、已完成：COMPLETED
     */
    MAIN_ORDER_STATUS_RUNNING("RUNNING", "MAIN_ORDER_STATUS", "运行中"),
    MAIN_ORDER_STATUS_COMPLETED("COMPLETED", "MAIN_ORDER_STATUS", "已完成"),

    /**
     * 回测报告状态：待测试：WAIT_TEST、待生成报告：WAIT_REPORT、生成报告中：REPORT_GENERATING、已生成报告：REPORT_GENERATED
     */
    TEST_REPORT_STATUS_WAIT_TEST("WAIT_TEST", "TEST_REPORT_STATUS", "待测试"),
    TEST_REPORT_STATUS_TESTING("TESTING", "TEST_REPORT_STATUS", "回测中"),
    TEST_REPORT_STATUS_WAIT_REPORT("WAIT_REPORT", "TEST_REPORT_STATUS", "待生成报告"),
    TEST_REPORT_STATUS_REPORT_GENERATING("REPORT_GENERATING", "TEST_REPORT_STATUS", "生成报告中"),
    TEST_REPORT_STATUS_REPORT_GENERATED("REPORT_GENERATED", "TEST_REPORT_STATUS", "已生成报告"),

    /**
     * 周期类型（MONTH 月、YEAR 年）
     */
    TEST_REPORT_DETAIL_PERIOD_TYPE_MONTH("MONTH", "TEST_REPORT_DETAIL_PERIOD_TYPE", "月"),
    TEST_REPORT_DETAIL_PERIOD_TYPE_YEAR("YEAR", "TEST_REPORT_DETAIL_PERIOD_TYPE", "年"),

    /**
     * 交易所手续费率
     */
    PLATFORM_FEE_RATE_OKX("OKX", "PLATFORM_FEE_RATE", "0.0009"),
    PLATFORM_FEE_RATE_HUOBI("HUOBI", "PLATFORM_FEE_RATE", "0.0009"),

    /**
     * 开仓信号渠道
     */
    OPEN_SIGNAL_CHANNEL_TV("TV", "OPEN_SIGNAL_CHANNEL", "tradeview"),

    /**
     * 平仓订单 同步状态，WAIT_SYNC 待同步、SYNCED 已同步、DEAL 已成交
     */
    CLOSE_ORDER_STATUS_WAIT_SYNC("WAIT_SYNC", "CLOSE_ORDER_STATUS", "待同步"),
    CLOSE_ORDER_STATUS_SYNCED("SYNCED", "CLOSE_ORDER_STATUS", "已同步"),
    CLOSE_ORDER_STATUS_DEAL("DEAL", "CLOSE_ORDER_STATUS", "已成交"),

    /**
     * 平仓方式，AUTO 自动、MANUAL 手动
     */
    CLOSE_METHOD_AUTO("AUTO", "CLOSE_METHOD", "自动"),
    CLOSE_METHOD_MANUAL("MANUAL", "CLOSE_METHOD", "手动"),

    /**
     * 平仓类型，GAIN 止盈、LOSS 止损
     */
    CLOSE_TYPE_GAIN("GAIN", "CLOSE_TYPE", "止盈"),
    CLOSE_TYPE_LOSS("LOSS", "CLOSE_TYPE", "止损"),

    /**
     * 邮件模板
     *
     */
    // 同步订单完成
    EMAIL_TEMPLATE_SYNC_COMPLETE("订单：%s同步完成，总收益：%s，总手续费：%s，机器人：%s，账户：%s，方向：%s", "EMAIL_TEMPLATE", "同步订单完成"),

    ;

    private String code;
    private String type;
    private String name;

    TradeOrderEnum(String code, String type, String name) {
        this.code = code;
        this.type = type;
        this.name = name;
    }


    /**
     * 根据code获取名称
     */
    public static String getNameByCode(String code) {
        for (TradeOrderEnum value : TradeOrderEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getName();
            }
        }
        return "";
    }

    /**
     * 根据名称获取code
     */
    public static String getCodeByName(String name) {
        for (TradeOrderEnum value : TradeOrderEnum.values()) {
            if (value.getName().equals(name)) {
                return value.getCode();
            }
        }
        return "";
    }

    /**
     * 根据code获取名称
     */
    public static TradeOrderEnum getByCodeAndType(String code, String type) {
        for (TradeOrderEnum value : TradeOrderEnum.values()) {
            if (value.getCode().equals(code) && value.getType().equals(type)) {
                return value;
            }
        }
        return null;
    }

}