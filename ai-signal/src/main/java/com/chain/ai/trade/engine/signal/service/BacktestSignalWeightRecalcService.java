package com.chain.ai.trade.engine.signal.service;

import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;
import com.chain.ai.trade.engine.signal.factory.SignFactory;
import com.chain.ai.trade.engine.signal.feature.BacktestContextHolder;
import com.chain.ai.trade.engine.signal.feature.SlidingWindow;
import com.chain.ai.trade.engine.signal.mapper.SignalAlternateLogMapper;
import com.chain.ai.trade.engine.signal.rule.IndicatorProviderRegistry;
import com.chain.ai.trade.engine.signal.rule.RuleEvaluationResult;
import com.chain.ai.trade.engine.signal.rule.WeightRuleConfig;
import com.chain.ai.trade.engine.signal.rule.WeightRuleContext;
import com.chain.ai.trade.engine.signal.rule.WeightRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测信号权重离线重算器。
 * <p>
 * 现状：回测策略（SignalScriptStrategy）直接读取 technical_signal 表中已落库的 signal_strength，
 * 并不经过 WeightRuleEngine。为了让回测反映新增的 L2 特征 + 权重规则，回测前离线重放历史信号：
 * 1. 用 signal_alternate_log 重放滑动窗口，预计算每个信号时间点对应的 L2 特征；
 * 2. 逐笔按时间注入 BacktestContextHolder，用 WeightRuleEngine 重算 signal_strength；
 * 3. 结果写回内存 SignalCacheManager（不回写 technical_signal，避免污染实盘数据）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestSignalWeightRecalcService {

    private static final int WINDOW_SIZE = 20;

    private final ITechnicalSignalService technicalSignalService;
    private final SignalAlternateLogMapper signalAlternateLogMapper;
    private final SignalServiceConfigService signalServiceConfigService;
    private final IndicatorProviderRegistry indicatorProviderRegistry;
    private final BacktestContextHolder backtestContextHolder;

    /**
     * 离线重算指定策略在时间段内的开仓信号权重。
     *
     * @param strategyName 策略标识（technical_signal.indicator，即 SignType 名）
     * @param symbol       交易对
     * @param timeframe    周期（CandlestickIntervalEnum.name()，如 OKXMIN5）
     * @param start        开始时间
     * @param end          结束时间
     * @return timeKey（klineTime）-> 重算后的 SignalInfo；无规则配置时返回空 Map
     */
    public Map<String, SignalInfo> recalcWeights(String strategyName, String symbol, String timeframe,
                                                 LocalDateTime start, LocalDateTime end) {
        Map<String, SignalInfo> result = new LinkedHashMap<>();

        String serviceKey = resolveServiceKey(strategyName);
        WeightRuleConfig config = serviceKey == null ? null : signalServiceConfigService.getWeightRules(serviceKey);
        if (config == null || !config.isEnabled() || config.getRules() == null || config.getRules().isEmpty()) {
            log.info("离线重算跳过：无可用权重规则, strategyName={}, serviceKey={}", strategyName, serviceKey);
            return result;
        }

        Map<Long, FeatureStatistics> statsMap = buildFeatureStats(strategyName, symbol, timeframe);
        List<TechnicalSignal> signals = technicalSignalService.getOpenSignalsByStrategy(symbol, strategyName, start, end);

        WeightRuleEngine engine = new WeightRuleEngine();
        engine.setIndicatorProviderRegistry(indicatorProviderRegistry);

        int recalculated = 0;
        int skipped = 0;
        try {
            for (TechnicalSignal signal : signals) {
                Long klineTs = signal.getKlineTimestamp();
                if (klineTs == null || signal.getKlineTime() == null) {
                    skipped++;
                    continue;
                }
                FeatureStatistics stats = statsMap.get(klineTs);
                if (stats == null) {
                    skipped++;
                    continue;
                }

                backtestContextHolder.setCurrentTime(klineTs);
                WeightRuleContext ctx = buildContext(symbol, signal.getTechnicalDirection(), klineTs, stats);
                RuleEvaluationResult eval = engine.evaluateWithTrace(config, ctx);

                double weight = (eval != null && !eval.isVetoed()) ? eval.getFinalWeight() : 0.0;
                result.put(signal.getKlineTime(),
                        new SignalInfo(signal.getId(), signal.getTechnicalDirection(), weight, signal.getExtraParams()));
                recalculated++;
            }
        } finally {
            backtestContextHolder.clear();
        }

        log.info("离线重算完成: strategyName={}, symbol={}, timeframe={}, 重算={}, 跳过={}",
                strategyName, symbol, timeframe, recalculated, skipped);
        return result;
    }

    /**
     * 将 strategyName（SignType）映射为规则配置的 serviceKey
     */
    private String resolveServiceKey(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return null;
        }
        try {
            SignFactory.SignType signType = SignFactory.SignType.valueOf(strategyName);
            ISignService service = SignFactory.getInstance(signType);
            if (service instanceof DefaultSignService) {
                return ((DefaultSignService) service).getSignalServiceKey();
            }
        } catch (Exception e) {
            log.warn("解析 serviceKey 失败: strategyName={}, error={}", strategyName, e.getMessage());
        }
        return null;
    }

    /**
     * 重放 signal_alternate_log 构建每个信号时间点对应的 L2 特征。
     * <p>
     * 记录先存储当前窗口统计（不含当前信号），再滑入当前信号，保证当前信号的特征窗口只包含历史信号。
     */
    private Map<Long, FeatureStatistics> buildFeatureStats(String strategyName, String symbol, String timeframe) {
        List<SignalAlternateLog> logs = signalAlternateLogMapper.selectAll(strategyName, symbol, timeframe);
        logs.sort(Comparator.comparing(SignalAlternateLog::getEntryTime, Comparator.nullsFirst(Comparator.naturalOrder())));

        Map<Long, FeatureStatistics> statsMap = new LinkedHashMap<>();
        SlidingWindow window = new SlidingWindow(WINDOW_SIZE);
        for (SignalAlternateLog log : logs) {
            if (log.getEntryTime() == null || log.getSpacePct() == null) {
                continue;
            }
            statsMap.put(log.getEntryTime(), window.getFullStatistics());
            window.add(log);
        }
        return statsMap;
    }

    /**
     * 构建规则引擎上下文，填充 L2 特征与当前信号方向/时间
     */
    private WeightRuleContext buildContext(String symbol, String direction, long currentTimeMs, FeatureStatistics stats) {
        WeightRuleContext ctx = new WeightRuleContext();
        ctx.setSymbol(symbol);
        ctx.setBuy("LONG".equalsIgnoreCase(direction));
        ctx.setCurrentDirection(direction);
        ctx.setCurrentTimeMs(currentTimeMs);

        ctx.setAvgSpace(stats.getAvgSpace());
        ctx.setCumRatio(stats.getCumRatio());
        ctx.setDirectionSeq(stats.getDirectionSeq());
        ctx.setLastSignalTime(stats.getLastSignalTime());
        ctx.setLastDirection(stats.getLastDirection());
        ctx.setLatestSpace(Math.abs(stats.getLatestSpace()));
        ctx.setPercentile20(stats.getPercentile_20());
        ctx.setPercentile40(stats.getPercentile_40());
        ctx.setPercentile70(stats.getPercentile_70());
        ctx.setPercentile85(stats.getPercentile_85());
        ctx.setPercentile95(stats.getPercentile_95());
        ctx.setCumRatioPercentile40(stats.getCumRatioPercentile_40());
        ctx.setCumRatioPercentile60(stats.getCumRatioPercentile_60());
        return ctx;
    }
}
