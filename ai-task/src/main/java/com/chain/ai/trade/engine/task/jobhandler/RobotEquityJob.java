package com.chain.ai.trade.engine.task.jobhandler;

import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotEquityJob {

    private final ITradingBotService tradingBotService;
    private final BacktestEquityCurveService equityCurveService;

    @XxlJob("robotEquityCollectJob")
    public void collectDailyEquity() {
        log.info("【机器人权益采集】开始执行...");
        List<TradingBot> bots = tradingBotService.list();
        if (bots == null || bots.isEmpty()) {
            log.info("【机器人权益采集】无机器人数据，跳过");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        int successCount = 0;
        for (TradingBot bot : bots) {
            try {
                String robotId = bot.getBotId();
                BigDecimal currentCapital = bot.getCurrentCapital();
                if (currentCapital == null) {
                    log.warn("【机器人权益采集】机器人 {} 当前权益为空，跳过", robotId);
                    continue;
                }

                List<BacktestEquityCurve> existing = equityCurveService.getEquitiesByRobotIds(
                        List.of(robotId), today, today);
                if (!existing.isEmpty()) {
                    log.info("【机器人权益采集】机器人 {} 今日数据已存在，跳过", robotId);
                    continue;
                }

                BacktestEquityCurve record = BacktestEquityCurve.builder()
                        .robotId(robotId)
                        .robotName(bot.getBotName())
                        .time(todayStart)
                        .equity(currentCapital)
                        .build();
                equityCurveService.save(record);
                successCount++;

                BigDecimal peakCapital = bot.getPeakCapital();
                if (peakCapital == null || currentCapital.compareTo(peakCapital) > 0) {
                    bot.setPeakCapital(currentCapital);
                    tradingBotService.updateById(bot);
                }

                log.info("【机器人权益采集】机器人 {} 采集成功，今日权益={}", robotId, currentCapital);
            } catch (Exception e) {
                log.error("【机器人权益采集】机器人 {} 采集失败", bot.getBotId(), e);
            }
        }
        log.info("【机器人权益采集】执行完成，成功={}/{}", successCount, bots.size());
    }
}
