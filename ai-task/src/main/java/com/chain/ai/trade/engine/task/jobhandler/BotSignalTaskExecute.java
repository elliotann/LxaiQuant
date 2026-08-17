package com.chain.ai.trade.engine.task.jobhandler;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.factory.SignFactory;
import com.chain.ai.trade.engine.signal.service.ISignService;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BotSignalTaskExecute {

    private static final Map<String, SignFactory.SignType> AI_TYPE_MAP = Map.of(
            "TREND", SignFactory.SignType.AI_TREND,
            "GRID", SignFactory.SignType.AI_GRID,
            "MEAN_REVERSION", SignFactory.SignType.AI_MEAN_REVERSION,
            "BREAKOUT", SignFactory.SignType.AI_BREAKOUT,
            "SCALPING", SignFactory.SignType.AI_SCALPING
    );

    private static final Map<String, CandlestickIntervalEnum> TIMEFRAME_MAP = Map.of(
            "1m", CandlestickIntervalEnum.OKXMIN1,
            "5m", CandlestickIntervalEnum.OKXMIN5,
            "15m", CandlestickIntervalEnum.OKXMIN15,
            "30m", CandlestickIntervalEnum.OKXMIN30,
            "1h", CandlestickIntervalEnum.OKXMIN60,
            "4h", CandlestickIntervalEnum.OKX4HOUR,
            "1d", CandlestickIntervalEnum.OKX1D,
            "1H", CandlestickIntervalEnum.OKXMIN60,
            "4H", CandlestickIntervalEnum.OKX4HOUR,
            "1D", CandlestickIntervalEnum.OKX1D
    );

    private final ITradingBotService tradingBotService;
    private final IStrategyService strategyService;
    private final ICandlestickService candlestickService;

    public BotSignalTaskExecute(ITradingBotService tradingBotService,
                                IStrategyService strategyService,
                                ICandlestickService candlestickService) {
        this.tradingBotService = tradingBotService;
        this.strategyService = strategyService;
        this.candlestickService = candlestickService;
    }

    @XxlJob("botSignalTaskExecute")
    public void execute() {
        log.info("BotSignalTaskExecute started");

        try {
            List<TradingBot> runningBots = tradingBotService.lambdaQuery()
                    .eq(TradingBot::getStatus, "RUNNING")
                    .list();

            if (runningBots.isEmpty()) {
                return;
            }

            log.info("Found {} running bots", runningBots.size());

            List<String> strategyIds = runningBots.stream()
                    .map(TradingBot::getStrategyId)
                    .distinct()
                    .collect(Collectors.toList());

            List<Strategy> strategies = strategyService.lambdaQuery()
                    .in(Strategy::getStrategyId, strategyIds)
                    .list();
            Map<String, Strategy> strategyMap = strategies.stream()
                    .collect(Collectors.toMap(Strategy::getStrategyId, s -> s));

            for (TradingBot bot : runningBots) {
                try {
                    processBot(bot, strategyMap.get(bot.getStrategyId()));
                } catch (Exception e) {
                    log.error("Error processing bot {}: {}", bot.getBotId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("BotSignalTaskExecute error", e);
        }

        log.info("BotSignalTaskExecute completed");
    }

    private void processBot(TradingBot bot, Strategy strategy) {
        if (strategy == null) {
            log.warn("Strategy not found for bot {}, strategyId={}", bot.getBotId(), bot.getStrategyId());
            return;
        }

        SignFactory.SignType signType = AI_TYPE_MAP.get(strategy.getStrategyType());
        if (signType == null) {
            log.warn("Unsupported strategy type '{}' for bot {}", strategy.getStrategyType(), bot.getBotId());
            return;
        }

        String timeFrame = strategy.getTimeFrame();
        CandlestickIntervalEnum interval = TIMEFRAME_MAP.get(timeFrame);
        if (interval == null) {
            log.warn("Unsupported timeframe '{}' for bot {}", timeFrame, bot.getBotId());
            return;
        }

        KlineParam klineParam = KlineParam.builder()
                .symbol(bot.getTradingPair())
                .klineInterval(interval)
                .size(300)
                .build();

        List<Candlestick> kLines = candlestickService.getLastKlines(klineParam);
        if (kLines == null || kLines.isEmpty()) {
            log.warn("No kline data for {} at {}", bot.getTradingPair(), timeFrame);
            return;
        }

        IndicatorCalcDto calcDto = new IndicatorCalcDto();
        calcDto.setRobotId(String.valueOf(bot.getBotId()));
        calcDto.setRobotName(bot.getStrategyId());
        calcDto.setSymbol(bot.getTradingPair());
        calcDto.setCandlestickIntervalEnum(interval);
        calcDto.setKLines(kLines);

        BarSeries series = new BaseBarSeriesBuilder().withName(bot.getTradingPair()).build();
        Duration duration = Duration.ofMinutes(interval.getMinNum().longValue());
        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
        for (Candlestick k : kLines) {
            ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(k.getId()), shanghaiZone);
            Instant endInstant = startTime.plus(duration).toInstant();
            var bar = series.barBuilder()
                    .timePeriod(duration)
                    .endTime(endInstant)
                    .openPrice(series.numFactory().numOf(k.getOpenPrice()))
                    .highPrice(series.numFactory().numOf(k.getHighPrice()))
                    .lowPrice(series.numFactory().numOf(k.getLowPrice()))
                    .closePrice(series.numFactory().numOf(k.getClosePrice()))
                    .volume(series.numFactory().numOf(k.getVolume()))
                    .build();
            series.addBar(bar);
        }
        calcDto.setSeries(series);

        calcDto.setStrategyType(strategy.getStrategyType());
        calcDto.setConfiguration(bot.getConfiguration());

        ISignService signService = SignFactory.getInstance(signType);
        BuyAndSellWeightDto result = signService.execute(calcDto);

        if (result != null) {
            log.info("Bot [{}] ({}) {} signal: {}", bot.getBotName(), bot.getTradingPair(),
                    strategy.getStrategyType(), result.getSignalType());
        } else {
            log.info("Bot [{}] ({}) {} signal: HOLD", bot.getBotName(), bot.getTradingPair(),
                    strategy.getStrategyType());
        }
    }
}
