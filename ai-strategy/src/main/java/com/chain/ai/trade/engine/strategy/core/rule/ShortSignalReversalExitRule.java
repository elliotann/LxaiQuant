package com.chain.ai.trade.engine.strategy.core.rule;


import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.strategy.enums.ExitRuleType;
import com.chain.ai.trade.extension.core.constants.ExitType;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空信号反转出场规则
 */
@Slf4j
public class ShortSignalReversalExitRule extends AbstractExitRule {

    private static final Logger LOG = LoggerFactory.getLogger(ShortSignalReversalExitRule.class);

    private final BarSeries barSeries;
    private final ITechnicalSignalService signalService;
    private final String symbol;
    private final String robotId;

    private final Map<Integer, String> signalCache = new ConcurrentHashMap<>();

    private final Map<String, SignalInfo> signalCacheOld;

    /**
     * 构造函数
     *
     * @param barSeries K线数据序列
     * @param signalService 信号服务
     * @param symbol 交易对
     */
    public ShortSignalReversalExitRule(BarSeries barSeries, ITechnicalSignalService signalService, String robotId, String symbol, Map<String, SignalInfo> signalCache) {
        super(
                ExitRuleType.SIGNAL_BASED,
                ExitType.SIGNAL_REVERSAL,
                "信号反转规则"
        );

        this.barSeries = barSeries;
        this.signalService = signalService;
        this.robotId = robotId;
        this.symbol = symbol;
        this.signalCacheOld = signalCache;
        // 添加规则参数
        addParameter("symbol", symbol);
    }

    @Override
    public boolean evaluate(int index, TradingRecord tradingRecord) {

        // 在传统回测/实盘执行框架中，我们不依赖 ta4j 的 TradingRecord 来管理仓位，
        // 仓位由订单系统管理，因此这里不再使用 tradingRecord 判断是否有仓位。
        // 只要当前K线时间上出现反向信号（LB），就认为需要平掉空头仓位。

        if (index >= barSeries.getBarCount()) {
            return false;
        }

        Bar bar = barSeries.getBar(index);
        Instant endInstant = bar.getEndTime();
        ZonedDateTime utcTime = ZonedDateTime.ofInstant(endInstant, ZoneOffset.UTC);
        String klineTime = utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 从缓存中查找信号
        SignalInfo signalInfo = signalCacheOld.get(klineTime);
        String signalType = signalInfo != null ? signalInfo.getSignalType() : null;

        // 记录所有LONG信号检测
        if ("LONG".equals(signalType)) {
            double weight = signalInfo != null ? signalInfo.getWeight() : 1.0;
            log.info("空头策略检测到LONG信号 - 索引:{}, 时间:{}, 权重:{}, 触发空头出场", index, klineTime, weight);
            return true;
        } else if (signalType != null) {
            log.debug("空头策略信号类型不匹配: 期望=LONG, 实际={}, 时间={}, 索引={}",
                    signalType, klineTime, index);
        } else {
            log.trace("空头策略未找到信号: 时间={}, 索引={}", klineTime, index);
        }

        return false;
    }

    /**
     * 获取指定位置的信号
     */
    private String getSignalAt(int index) {
        // 先从缓存获取
        String cachedSignal = signalCache.get(index);
        if (cachedSignal != null) {
            return cachedSignal;
        }

        // 缓存中没有，从服务获取
        if (signalService == null) {
            LOG.warn("信号服务未注入，无法获取信号");
            return null;
        }

        try {
            // 获取K线时间
            long barTime = barSeries.getBar(index).getBeginTime().toEpochMilli() - 60 * 60 * 1000 * 8;

            // 查询信号
            TechnicalSignal signal = signalService.getTechnicalSignalByTime(
                    robotId, // robotId可以为空
                    symbol,
                    null, // 不指定信号类型
                    barTime
            );

            String signalValue = null;
            if (signal != null && signal.getTechnicalDirection() != null) {
                // 将技术方向转换为信号类型
                signalValue = convertTechnicalDirectionToSignalType(signal.getTechnicalDirection());
            }

            // 存入缓存
            if (signalValue != null) {
                signalCache.put(index, signalValue);
            }

            return signalValue;

        } catch (Exception e) {
            LOG.error("获取信号失败: index={}, symbol={}", index, symbol, e);
            return null;
        }
    }

    /**
     * 将技术方向转换为信号类型
     * 技术方向: STRONG_BULLISH, BULLISH, NEUTRAL, BEARISH, STRONG_BEARISH
     * 信号类型: LB (Long/Buy), SB (Short/Sell)
     */
    private String convertTechnicalDirectionToSignalType(String technicalDirection) {
        if (technicalDirection == null) {
            return null;
        }

        // 简化转换：BULLISH -> LB, BEARISH -> SB
        return technicalDirection; // NEUTRAL或其他
    }

    /**
     * 清空信号缓存
     */
    public void clearCache() {
        signalCache.clear();
        LOG.debug("清空信号缓存");
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return signalCache.size();
    }
}