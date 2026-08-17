package com.chain.ai.trade.engine.strategy;

import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.engine2.rules.base.DynamicRiskEngineExitRule;
import com.chain.ai.trade.engine2.rules.base.SmcStructuredExitRule;
import com.chain.ai.trade.engine.strategy.core.rule.ExitRuleAdapter;
import com.chain.ai.trade.engine.strategy.core.rule.IndicatorDrivenDirectionRule;
import com.chain.ai.trade.engine.strategy.core.rule.MacdCrossExitRule;
import com.chain.ai.trade.engine.strategy.core.rule.OrDirectionalRule;
import com.chain.ai.trade.engine.strategy.core.rule.PinVolumeExitRule;
import com.chain.ai.trade.engine.strategy.entity.dos.EntryRuleCondition;
import com.chain.ai.trade.engine.strategy.service.IStrategyEntryRuleService;
import com.chain.ai.trade.engine.strategy.service.IStrategyParameterService;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.core.rule.DirectionalRule;
import com.chain.ai.trade.extension.ta4j.core.rule.ManualGear;
import com.chain.ai.trade.extension.ta4j.core.rule.TrailingMode;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(StrategyFactory.class);

    @Autowired(required = false)
    private IStrategyParameterService strategyParameterService;

    @Autowired(required = false)
    private IStrategyEntryRuleService strategyEntryRuleService;

    @Autowired(required = false)
    private IStrategyService strategyService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ======================== Strategy Resolution ========================

    public String resolveStrategyClassName(String strategyId) {
        if (strategyId == null || strategyService == null) {
            return null;
        }
        try {
            var strategy = strategyService.getByStrategyId(strategyId);
            if (strategy != null && strategy.getClassName() != null && !strategy.getClassName().trim().isEmpty()) {
                return strategy.getClassName();
            }
        } catch (Exception e) {
            log.warn("解析策略类名失败: strategyId={}", strategyId, e);
        }
        return null;
    }

    // ======================== Config Loading ========================

    public ExitRulesConfigDTO loadExitRulesConfig(String strategyId, String robotId) {
        if (strategyId == null || strategyParameterService == null) {
            return new ExitRulesConfigDTO();
        }
        try {
            String json = strategyParameterService.getParameterValue(strategyId, "exit_rules_config", "config");
            if (json != null && !json.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = objectMapper.readValue(json, Map.class);
                return ExitRulesConfigDTO.fromExitRulesMap(rawMap);
            }
        } catch (Exception e) {
            log.warn("读取 exit_rules_config/config 失败: {}", e.getMessage());
        }
        return new ExitRulesConfigDTO();
    }

    /** 加载加仓配置（add_position_config/config），返回 key-value Map */
    public Map<String, Object> loadAddPositionConfig(String strategyId) {
        if (strategyId == null || strategyParameterService == null) {
            return Collections.emptyMap();
        }
        try {
            String json = strategyParameterService.getParameterValue(strategyId, "add_position_config", "config");
            if (json != null && !json.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                return map;
            }
        } catch (Exception e) {
            log.warn("读取 add_position_config/config 失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /** 加载仓位控制配置（position_risk/config），返回 key-value Map */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPositionRiskConfig(String strategyId) {
        if (strategyId == null || strategyParameterService == null) {
            return Collections.emptyMap();
        }
        try {
            String json = strategyParameterService.getParameterValue(strategyId, "position_risk", "config");
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            log.warn("读取 position_risk/config 失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }









    // ---------- 结构止盈止损（V2 SmcStructuredExitRule） ----------

    /**
     * 创建结构止盈止损规则（SmcStructuredExitRule）。
     * 由 engine2 流程调用，使用 MultiTimeFrameProvider 和 TradingRule 接口。
     *
     * @return 配置好的 TradingRule 列表（LONG + SHORT），空列表表示未启用
     */
    public List<TradingRule> buildStructureStopProfitRules(MultiTimeFrameProvider mtfProvider, String symbol,
                                                           ExitRulesConfigDTO config) {
        List<TradingRule> rules = new ArrayList<>();
        var ss = config.getStructureStopProfit();
        if (ss == null || !ss.isEnabled()) return rules;

        // 多头
        SmcStructuredExitRule longRule = new SmcStructuredExitRule(mtfProvider, symbol, SignalType.LONG);
        configureStructuredRule(longRule, ss);
        rules.add(longRule);

        // 空头
        SmcStructuredExitRule shortRule = new SmcStructuredExitRule(mtfProvider, symbol, SignalType.SHORT);
        configureStructuredRule(shortRule, ss);
        rules.add(shortRule);

        log.info("已创建结构止盈止损规则: symbol={}", symbol);
        return rules;
    }

    /**
     * 将 DTO 配置注入 V2 SmcStructuredExitRule 实例。
     * DTO 存储百分比数值（如 0.08 = 0.08%），规则内部使用小数（如 0.0008），
     * 通过 toDecimalPct 转换。
     */
    private void configureStructuredRule(SmcStructuredExitRule rule,
                                         ExitRulesConfigDTO.StructureStopProfitConfig ss) {
        // ---- 动态止损 ----
        var dsl = ss.getDynamicStopLoss();
        rule.setStructuralExitEnabled(ss.isEnabled());
        rule.setDynamicStopLossEnabled(true);
        rule.setAutoEnableUltimate(dsl.isAutoEnableUltimate());
        rule.setStopLossDailyBuffer(toDecimalPct(dsl.getDailyBuffer()));
        rule.setStopLossBufferBuffer(toDecimalPct(dsl.getBufferBuffer()));
        rule.setStopLossUltimateBuffer(toDecimalPct(dsl.getUltimateBuffer()));
        rule.setStopLossDailyPeriod(String.valueOf(dsl.getDailyPeriod()));
        rule.setStopLossBufferPeriod(String.valueOf(dsl.getBufferPeriod()));
        rule.setStopLossUltimatePeriod(String.valueOf(dsl.getUltimatePeriod()));

        // ---- 主动止盈 ----
        var tp = ss.getTakeProfitActive();
        rule.setActiveTakeProfitEnabled(true);
        rule.setSwingClosePct(tp.getSwingClosePct());
        rule.setOb1hClosePct(tp.getOb1hClosePct());
        rule.setFvgClosePct(tp.getFvgClosePct());
        rule.setMinRiskReward(tp.getMinRiskReward());
        rule.setMaxRiskReward(tp.getMaxRiskReward());

        // ---- 移动止损 + 保本 ----
        var tlp = ss.getTrailingProtection();
        rule.setTrailingEnabled(tlp.isTrailingEnabled());
        rule.setTrailingBuffer(toDecimalPct(tlp.getTrailingBuffer()));
        rule.setBreakevenEnabled(tlp.isBreakevenEnabled());
        rule.setBreakevenBuffer(toDecimalPct(tlp.getBreakevenBuffer()));

        // ---- 参考时间框架 ----
        var ref = ss.getReference();
        rule.setReferenceStopLossPeriod(String.valueOf(ref.getStopLossPeriod()));
        rule.setReferenceTakeProfitPeriod(String.valueOf(ref.getTakeProfitPeriod()));
    }

    /** 将百分比数值（0.08 = 0.08%）转为小数（0.0008） */
    private static double toDecimalPct(double value) {
        return value / 100.0;
    }

    private static SmartMoneyConceptsIndicator createSmcIndicator(BarSeries series) {
        var smcConfig = new SmartMoneyConceptsIndicator.Config();
        smcConfig.setSwingsLength(50);
        smcConfig.setShowInternalOrderBlocks(true);
        smcConfig.setShowSwingOrderBlocks(true);
        smcConfig.setShowEqualHighsLows(true);
        smcConfig.setShowPremiumDiscountZones(true);
        smcConfig.setShowFairValueGaps(false);
        return new SmartMoneyConceptsIndicator(series, smcConfig, null, null, null);
    }

    // ======================== 动态风控引擎 ========================

    /**
     * 加载动态风控引擎配置（dynamic_risk_engine/config）。
     */
    public DynamicRiskEngineDTO loadDynamicRiskEngine(String strategyId) {
        if (strategyId == null || strategyParameterService == null) return null;
        try {
            String json = strategyParameterService.getParameterValue(strategyId, "dynamic_risk_engine", "config");
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, DynamicRiskEngineDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取 dynamic_risk_engine/config 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 创建动态风控引擎出场规则（LONG + SHORT）。
     *
     * @return 配置好的 TradingRule 列表，未启用时返回空列表
     */
    public List<TradingRule> buildDynamicRiskEngineRules(MultiTimeFrameProvider mtfProvider, String symbol,
                                                         DynamicRiskEngineDTO dto) {
        List<TradingRule> rules = new ArrayList<>();
        if (dto == null) return rules;

        boolean stopEnabled = dto.getTrailingStop() != null && dto.getTrailingStop().isEnabled();
        boolean tpEnabled = dto.getTrailingTakeProfit() != null && dto.getTrailingTakeProfit().isEnabled();
        if (!stopEnabled && !tpEnabled) return rules;

        DynamicRiskEngineExitRule longRule = new DynamicRiskEngineExitRule(mtfProvider, symbol, SignalType.LONG);
        configureDynamicRiskEngineRule(longRule, dto);
        rules.add(longRule);

        DynamicRiskEngineExitRule shortRule = new DynamicRiskEngineExitRule(mtfProvider, symbol, SignalType.SHORT);
        configureDynamicRiskEngineRule(shortRule, dto);
        rules.add(shortRule);

        log.info("已创建动态风控引擎出场规则: symbol={}", symbol);
        return rules;
    }

    /**
     * 将 DTO 配置注入动态风控引擎规则实例（百分比转小数）。
     */
    private void configureDynamicRiskEngineRule(DynamicRiskEngineExitRule rule, DynamicRiskEngineDTO dto) {
        var ts = dto.getTrailingStop();
        if (ts != null) {
            rule.setStopEnabled(ts.isEnabled());
            rule.setStopPeriod(ts.getPeriod());
            rule.setStopUseOb(ts.getStructureTypes() != null && ts.getStructureTypes().isOb());
            rule.setStopUseSwing(ts.getStructureTypes() == null || ts.getStructureTypes().isSwing());
            rule.setStopOffsetBuffer(toDecimalPct(ts.getOffsetBuffer()));
            rule.setStopBreakWick(!"close".equals(ts.getBreakMode()));
            rule.setStopActivation(ts.getActivation());
        }

        var tp = dto.getTrailingTakeProfit();
        if (tp != null) {
            rule.setTpEnabled(tp.isEnabled());
            rule.setTpPeriod(tp.getPeriod());
            rule.setTpUseOb(tp.getStructureTypes() != null && tp.getStructureTypes().isOb());
            rule.setTpUseSwing(tp.getStructureTypes() == null || tp.getStructureTypes().isSwing());
            rule.setTpOffsetBuffer(toDecimalPct(tp.getOffsetBuffer()));
            rule.setTpTriggerWick(!"close".equals(tp.getTriggerMode()));
            rule.setTpActivation(tp.getActivation());
            rule.setTpExitMode(tp.getExitMode());
            rule.setTpMinStepEnabled(tp.isMinStepEnabled());
            rule.setTpMinStep(toDecimalPct(tp.getMinStep()));
        }
    }
}
