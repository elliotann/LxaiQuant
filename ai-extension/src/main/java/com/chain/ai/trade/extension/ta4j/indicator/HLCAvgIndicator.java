package com.chain.ai.trade.extension.ta4j.indicator;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * 计算HLC平均价格指标：(最高价 + 最低价 + 收盘价) / 3
 */
public class HLCAvgIndicator extends CachedIndicator<Num> {
    public HLCAvgIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        // 使用正确的方法名：getHigh() 和 getLow()
        return bar.getHighPrice()
                .plus(bar.getLowPrice())
                .plus(bar.getClosePrice())
                .dividedBy(getBarSeries().numFactory().numOf(3));
    }



    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
