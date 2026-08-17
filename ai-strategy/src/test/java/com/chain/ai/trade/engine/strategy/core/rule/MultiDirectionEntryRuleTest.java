package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Trade;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MultiDirectionEntryRuleTest {

    @Test
    public void shouldBlockShortWhen15mBullishBothTrendsAndInDemandBlock() {
        BarSeries series = new BaseBarSeriesBuilder().withName("t").build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofMinutes(3))
                .endTime(Instant.parse("2025-01-01T00:03:00Z"))
                .openPrice(100)
                .highPrice(102)
                .lowPrice(98)
                .closePrice(99)
                .volume(1)
                .build());

        SignalCacheManager cache = new SignalCacheManager((ITechnicalSignalService) null);
        String timeKey = "2025-01-01 00:00:00";
        String extra = "{\"smc\":{\"15m\":{\"internalTrend\":1,\"swingTrend\":1,\"internalOrderBlocks\":[{\"high\":100,\"low\":90,\"bias\":1}]},\"1h\":{\"internalTrend\":0,\"swingTrend\":0,\"internalOrderBlocks\":[]}}}";
        cache.updateSignal(timeKey, "SHORT", 1.0, extra);

        MultiDirectionEntryRule rule = new MultiDirectionEntryRule(series, cache);
        assertNull(rule.getDirection(0, null));
    }

    @Test
    public void shouldBlockLongWhen15mBearishBothTrendsAndInSupplyBlock() {
        BarSeries series = new BaseBarSeriesBuilder().withName("t").build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofMinutes(3))
                .endTime(Instant.parse("2025-01-01T00:03:00Z"))
                .openPrice(100)
                .highPrice(105)
                .lowPrice(99)
                .closePrice(104)
                .volume(1)
                .build());

        SignalCacheManager cache = new SignalCacheManager((ITechnicalSignalService) null);
        String timeKey = "2025-01-01 00:00:00";
        String extra = "{\"smc\":{\"15m\":{\"internalTrend\":-1,\"swingTrend\":-1,\"internalOrderBlocks\":[{\"high\":110,\"low\":103,\"bias\":-1}]},\"1h\":{\"internalTrend\":0,\"swingTrend\":0,\"internalOrderBlocks\":[]}}}";
        cache.updateSignal(timeKey, "LONG", 1.0, extra);

        MultiDirectionEntryRule rule = new MultiDirectionEntryRule(series, cache);
        assertNull(rule.getDirection(0, null));
    }

    @Test
    public void shouldBlockShortWhenBothBullishAndInSupport() {
        BarSeries series = new BaseBarSeriesBuilder().withName("t").build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofMinutes(3))
                .endTime(Instant.parse("2025-01-01T00:03:00Z"))
                .openPrice(100)
                .highPrice(102)
                .lowPrice(98)
                .closePrice(99)
                .volume(1)
                .build());

        SignalCacheManager cache = new SignalCacheManager((ITechnicalSignalService) null);
        String timeKey = "2025-01-01 00:00:00";
        String extra = "{\"smc\":{\"15m\":{\"internalTrend\":1,\"internalOrderBlocks\":[{\"high\":100,\"low\":90,\"bias\":1}]},\"1h\":{\"internalTrend\":1,\"internalOrderBlocks\":[{\"high\":101,\"low\":91,\"bias\":1}]}}}";
        cache.updateSignal(timeKey, "SHORT", 1.0, extra);

        MultiDirectionEntryRule rule = new MultiDirectionEntryRule(series, cache);
        assertNull(rule.getDirection(0, null));
    }

    @Test
    public void shouldAllowShortWhenExtraMissing() {
        BarSeries series = new BaseBarSeriesBuilder().withName("t").build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofMinutes(3))
                .endTime(Instant.parse("2025-01-01T00:03:00Z"))
                .openPrice(100)
                .highPrice(102)
                .lowPrice(98)
                .closePrice(99)
                .volume(1)
                .build());

        SignalCacheManager cache = new SignalCacheManager((ITechnicalSignalService) null);
        String timeKey = "2025-01-01 00:00:00";
        cache.updateSignal(timeKey, "SHORT", 1.0);

        MultiDirectionEntryRule rule = new MultiDirectionEntryRule(series, cache);
        assertEquals(Trade.TradeType.SELL, rule.getDirection(0, null));
    }

    @Test
    public void shouldBlockLongWhenBothBearishAndInResistance() {
        BarSeries series = new BaseBarSeriesBuilder().withName("t").build();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofMinutes(3))
                .endTime(Instant.parse("2025-01-01T00:03:00Z"))
                .openPrice(100)
                .highPrice(105)
                .lowPrice(99)
                .closePrice(104)
                .volume(1)
                .build());

        SignalCacheManager cache = new SignalCacheManager((ITechnicalSignalService) null);
        String timeKey = "2025-01-01 00:00:00";
        String extra = "{\"smc\":{\"15m\":{\"internalTrend\":-1,\"internalOrderBlocks\":[{\"high\":110,\"low\":103,\"bias\":-1}]},\"1h\":{\"internalTrend\":-1,\"internalOrderBlocks\":[{\"high\":111,\"low\":104,\"bias\":-1}]}}}";
        cache.updateSignal(timeKey, "LONG", 1.0, extra);

        MultiDirectionEntryRule rule = new MultiDirectionEntryRule(series, cache);
        assertNull(rule.getDirection(0, null));
    }
}
