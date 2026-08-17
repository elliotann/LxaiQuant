package com.chain.ai.trade.extension.ta4j.indicator;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * 形态1：强阳吞没 + 重心上移（看涨蓄势）
 * 返回 1.0：符合形态
 * 返回 0.0：不符合形态
 */
public class BullishAccumulationIndicator extends AbstractIndicator<Num> {

    public BullishAccumulationIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        // 历史数据不足3根，直接返回 0
        if (index < 2) return getBarSeries().numFactory().numOf(0.0);

        Bar k1 = getBarSeries().getBar(index - 2); // 空头试探
        Bar k2 = getBarSeries().getBar(index - 1); // 强阳反包
        Bar k3 = getBarSeries().getBar(index);     // 确认延续

        // 1. K1为阴线
        boolean cond1 = k1.getClosePrice().isLessThan(k1.getOpenPrice());
        // 2. K2为强阳线
        boolean cond2 = k2.getClosePrice().isGreaterThan(k2.getOpenPrice());
        // 3. K2实体 > K1实体
        boolean cond3 = k2.getClosePrice().minus(k2.getOpenPrice()).isGreaterThan(k1.getOpenPrice().minus(k1.getClosePrice()));
        // 4. K2收盘价 > K1开盘价
        boolean cond4 = k2.getClosePrice().isGreaterThan(k1.getOpenPrice());
        // 5. K3为阳线
        boolean cond5 = k3.getClosePrice().isGreaterThan(k3.getOpenPrice());
        // 6. K3高低点同步抬高
        boolean cond6 = k3.getHighPrice().isGreaterThan(k2.getHighPrice()) && k3.getLowPrice().isGreaterThan(k2.getLowPrice());

        // 使用 series.numFactory().numOf() 确保类型安全
        return (cond1 && cond2 && cond3 && cond4 && cond5 && cond6)
                ? getBarSeries().numFactory().numOf(1.0)
                : getBarSeries().numFactory().numOf(0.0);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}