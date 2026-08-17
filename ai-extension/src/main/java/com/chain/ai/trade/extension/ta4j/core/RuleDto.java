package com.chain.ai.trade.extension.ta4j.core;

import org.ta4j.core.num.Num;

public class RuleDto {
    /** true: 买入; false: 卖出 */
    private final boolean isBuy;

    /** 入场价格（使用 ta4j Num 类型） */
    private final Num entryPrice;

    /** 交易数量（使用 ta4j Num 类型） */
    private final Num amount;

    /**
     * 构造业务订单。
     *
     * @param isBuy      方向：true 为买入，false 为卖出
     * @param entryPrice 入场价格（已转换为 Num）
     * @param amount     交易数量（已转换为 Num）
     */
    public RuleDto(boolean isBuy, Num entryPrice, Num amount) {
        this.isBuy = isBuy;
        this.entryPrice = entryPrice;
        this.amount = amount;
    }

    /**
     * 便捷构造方法：接收 double 价格和数量，自动使用默认 NumFactory 转换。
     * <p>
     * 注意：默认使用 DoubleNumFactory，如使用其他 Num 实现，请自行转换后调用主构造器。
     */
    public RuleDto(boolean isBuy, double entryPrice, double amount) {
        this(isBuy,
                org.ta4j.core.num.DecimalNum.valueOf(entryPrice),
                org.ta4j.core.num.DecimalNum.valueOf(amount));
    }

    public boolean isBuy() {
        return isBuy;
    }

    public Num getEntryPrice() {
        return entryPrice;
    }

    public Num getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return String.format("BusinessOrder{isBuy=%s, entryPrice=%s, amount=%s}",
                isBuy, entryPrice, amount);
    }
}
