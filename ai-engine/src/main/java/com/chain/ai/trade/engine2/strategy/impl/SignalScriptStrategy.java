package com.chain.ai.trade.engine2.strategy.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import com.chain.ai.trade.engine2.core.EntrySignal;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.ScaleInReason;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.ScaleInRule;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import com.chain.ai.trade.engine.strategy.core.rule.MultiDirectionEntryRule;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;



/**
 * 信号驱动的策略实现 — 从 DefaultDealStrategyTrade 中提取的策略决策部分。
 * <p>
 * 适配 ScriptStrategy 双方法接口：
 * <ul>
 *   <li>shouldEntry → 查询信号缓存 + MultiDirectionEntryRule，返回 LONG/SHORT</li>
 *   <li>shouldExit → 委托给 TradingRule 出场规则集（信号反转/止损/止盈/移动止损）</li>
 * </ul>
 */
@Slf4j
public class SignalScriptStrategy implements ScriptStrategy {

    private final String strategyId;
    private final String symbol;
    private final BarSeries series;
    private final SignalCacheManager signalCacheManager;

    /** DB配置的入场规则（基于信号缓存） */
    private final MultiDirectionEntryRule entryRule;

    /** DB配置的出场规则组合（SignalReversalRule / FixedStopLossRule 等） */
    private final TradingRule exitRule;

    /** 加仓规则配置（可空） */
    private final ScaleInRule scaleInRule;
    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;
    public SignalScriptStrategy(String strategyId, String symbol,
                                BarSeries series, SignalCacheManager signalCacheManager,
                                MultiDirectionEntryRule entryRule, TradingRule exitRule,
                                ScaleInRule scaleInRule) {
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.series = series;
        this.signalCacheManager = signalCacheManager;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
        this.scaleInRule = scaleInRule;
    }

    @Override
    public EntrySignal shouldEntry(int index, Bar bar, TradingContext context) {
        if (signalCacheManager == null) {
            log.info("[ENTRY] signalCacheManager 为空，跳过入场: symbol={}", symbol);
            return null;
        }

        // 使用 MultiDirectionEntryRule 确定方向（基于信号缓存）
        Trade.TradeType direction = null;
        if (entryRule != null) {
            direction = entryRule.getDirection(index, null);
        }
        if (direction == null) {
            return null;
        }

        SignalType signalType = convertToSignalType(direction.name());
        if (signalType == null) {
            return null;
        }

        // 已有同向持仓时不再重复入场
        if (signalType == SignalType.LONG && context.hasLongPosition()) return null;
        if (signalType == SignalType.SHORT && context.hasShortPosition()) return null;

        // 从信号缓存获取信号强度权重和技术信号ID
        double signalStrength = 1.0;
        Long signalId = null;
        SignalInfo signalInfo = signalCacheManager.getSignal(index, series);
        if (signalInfo != null) {
            signalStrength = signalInfo.getWeight();
            signalId = signalInfo.getId();
        }

        log.info("[ENTRY] 信号命中: symbol={}, index={}, direction={}, strength={}, signalId={}",
                symbol, index, signalType, signalStrength, signalId);
        return new EntrySignal(signalType, signalStrength, signalId);
    }

    @Override
    public ExitSignal shouldExit(int index, Bar bar, TradingContext context) {
        if (exitRule == null) {
            return null;
        }
        return exitRule.evaluate(index, bar, series, context);
    }

    @Override
    public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context) {
        //暂时不删除，调试用的


        String targetTime = "2025-05-04 21:00:00";
        String currentTime = DateUtil.formatDateTime(new java.util.Date(bar.getBeginTime().toEpochMilli() - TIMEZONE_OFFSET_MS));
        boolean hitTarget = targetTime.equals(currentTime);
        if(hitTarget){
            System.out.println("here");
        }
        // 1. 入场信号是加仓前提（不含信号不允许加仓）
        if (entryRule == null) return null;
        Trade.TradeType direction = entryRule.getDirection(index, null);
        if (direction == null) return null;
        SignalType signalType = convertToSignalType(direction.name());
        if (signalType == null) return null;

        // 2. 必须有同向持仓
        if (signalType == SignalType.LONG && !context.hasLongPosition()) return null;
        if (signalType == SignalType.SHORT && !context.hasShortPosition()) return null;

        // 3. 获取信号强度和信号ID
        double signalStrength = 0.0;
        Long signalId = null;
        SignalInfo signalInfo = null;
        if (signalCacheManager != null) {
            signalInfo = signalCacheManager.getSignal(index, series);
            if (signalInfo != null) {
                signalStrength = signalInfo.getWeight();
                signalId = signalInfo.getId();
            }
        }

        // 4. 顺势门控：仅当信号方向与多周期共振方向一致（信号共振）时才允许加仓
        if (!isTrendAligned(signalType, signalInfo)) {
            return null;
        }

        // 5. 叠加 scaleInRule 条件（幅度/间距/EMA趋势等），传入信号方向约束
        if (scaleInRule != null) {
            ScaleInSignal result = scaleInRule.shouldScaleIn(index, bar, series, context, signalType);
            if (result == null) return null;
            return new ScaleInSignal(signalType, result.getReason(), signalStrength,
                    result.getTakeProfitPrice(), result.getStopLossPrice(), result.getPrice(),
                    signalId);
        }

        // 6. 无 scaleInRule → 纯信号驱动加仓
        return new ScaleInSignal(signalType, ScaleInReason.SIGNAL, signalStrength, null, null, null,
                signalId);
    }

    /**
     * 检查信号方向是否与多周期共振方向一致（信号共振 = 顺势）。
     * <p>
     * 解析 SignalInfo.extraParams 中的 smcDashboard.alignment 字段：
     * <ul>
     *   <li>LONG 信号 → 仅当 alignment="顺势做多" 才允许加仓</li>
     *   <li>SHORT 信号 → 仅当 alignment="顺势做空" 才允许加仓</li>
     *   <li>无信号数据或 alignment 缺失 → 不允许加仓（保守）</li>
     * </ul>
     */
    private boolean isTrendAligned(SignalType signalType, SignalInfo signalInfo) {
        if (signalInfo == null) {
            log.debug("[SCALE_IN] 无信号数据，拒绝加仓");
            return false;
        }
        String extraParams = signalInfo.getExtraParams();
        if (extraParams == null || extraParams.isEmpty()) {
            log.info("[SCALE_IN] 信号无extraParams，拒绝加仓: direction={}, signalId={}", signalType, signalInfo.getId());
            return false;
        }
        try {
            JSONObject root = JSONUtil.parseObj(extraParams);
            JSONObject dashboard = root.getJSONObject("smcDashboard");
            if (dashboard == null) {
                log.info("[SCALE_IN] extraParams无smcDashboard，拒绝加仓: direction={}, signalId={}, extraParams={}",
                        signalType, signalInfo.getId(), extraParams);
                return false;
            }
            String alignment = dashboard.getStr("alignment");
            if (alignment == null) {
                log.info("[SCALE_IN] smcDashboard无alignment，拒绝加仓: direction={}, signalId={}",
                        signalType, signalInfo.getId());
                return false;
            }
            boolean aligned = (signalType == SignalType.LONG && "顺势做多".equals(alignment))
                    || (signalType == SignalType.SHORT && "顺势做空".equals(alignment));
            if (aligned) {
                log.info("[SCALE_IN] 顺势加仓通过: direction={}, alignment={}, signalId={}",
                        signalType, alignment, signalInfo.getId());
            } else {
                log.info("[SCALE_IN] 非顺势拒绝加仓: direction={}, alignment={}, signalId={}",
                        signalType, alignment, signalInfo.getId());
            }
            return aligned;
        } catch (Exception e) {
            log.warn("[SCALE_IN] 解析信号共振数据失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将信号类型字符串转为 SignalType 枚举
     */
    private static SignalType convertToSignalType(String sigType) {
        if ("LONG".equalsIgnoreCase(sigType) || "BUY".equalsIgnoreCase(sigType)) {
            return SignalType.LONG;
        }
        if ("SHORT".equalsIgnoreCase(sigType) || "SELL".equalsIgnoreCase(sigType)) {
            return SignalType.SHORT;
        }
        return null;
    }
}
