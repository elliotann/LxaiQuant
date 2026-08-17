package com.chain.ai.trade.extension.ta4j.indicator;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import java.util.stream.Stream;

/**
 * 自定义指标差值计算（替代SubtractIndicator）
 */
public class DifferenceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;

    public DifferenceIndicator(Indicator<Num> first, Indicator<Num> second) {
        super(first);
        this.first = first;
        this.second = second;
    }

    @Override
    protected Num calculate(int index) {
        return first.getValue(index).minus(second.getValue(index));
    }


    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public Stream<Num> stream() {
        return super.stream();
    }
}