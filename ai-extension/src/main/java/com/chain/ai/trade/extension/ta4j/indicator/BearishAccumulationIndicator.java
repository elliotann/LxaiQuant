package com.chain.ai.trade.extension.ta4j.indicator;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * 形态2：强阴吞没 + 重心下移（看跌蓄势）
 * 返回 1.0：符合形态
 * 返回 0.0：不符合形态
 */
public class BearishAccumulationIndicator extends AbstractIndicator<Num> {

    public BearishAccumulationIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        if (index < 2) return getBarSeries().numFactory().numOf(0.0);

        Bar k1 = getBarSeries().getBar(index - 2); // 多头试探
        Bar k2 = getBarSeries().getBar(index - 1); // 强阴反包
        Bar k3 = getBarSeries().getBar(index);     // 确认延续

        // 1. K1为阳线
        boolean cond1 = k1.getClosePrice().isGreaterThan(k1.getOpenPrice());
        // 2. K2为强阴线
        boolean cond2 = k2.getClosePrice().isLessThan(k2.getOpenPrice());
        // 3. K2实体 > K1实体
        boolean cond3 = k2.getOpenPrice().minus(k2.getClosePrice()).isGreaterThan(k1.getClosePrice().minus(k1.getOpenPrice()));
        // 4. K2收盘价 < K1开盘价
        boolean cond4 = k2.getClosePrice().isLessThan(k1.getOpenPrice());
        // 5. K3为阴线
        boolean cond5 = k3.getClosePrice().isLessThan(k3.getOpenPrice());
        // 6. K3高低点同步下移
        boolean cond6 = k3.getHighPrice().isLessThan(k2.getHighPrice()) && k3.getLowPrice().isLessThan(k2.getLowPrice());

        return (cond1 && cond2 && cond3 && cond4 && cond5 && cond6)
                ? getBarSeries().numFactory().numOf(1.0)
                : getBarSeries().numFactory().numOf(0.0);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}