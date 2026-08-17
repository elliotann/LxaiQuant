package com.chain.ai.trade.engine.strategy;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 出场规则配置 DTO。
 * <p>
 * 存储为 StrategyParameter（group=exit_rules_config, key=config）的单条 JSON 记录。
 * V7 按 enabled 开关加载出场规则，逻辑与旧系统一致。
 * </p>
 */
@Data
@NoArgsConstructor
public class ExitRulesConfigDTO {

    /** 信号反转出场：当入场信号反转时平仓 */
    private SignalReversalConfig signalReversal = new SignalReversalConfig();

    /** 技术指标出场（MACD、成交量等） */
    private TechnicalConfig technical = new TechnicalConfig();

    /** 时间止盈：持仓达到指定时间后按比例止盈 */
    private TimeBasedConfig timeBasedTakeProfit = new TimeBasedConfig();

    /** 时间止损：持仓达到指定时间后按比例止损 */
    private TimeBasedConfig timeBasedStopLoss = new TimeBasedConfig();

    /** SMC 出场：基于 Smart Money Concept 的出场策略 */
    private SmcConfig smcExit = new SmcConfig();

    /** 结构止盈止损：基于 SMC 结构化出场策略（新规则 SmcStructuredExitRule） */
    private StructureStopProfitConfig structureStopProfit = new StructureStopProfitConfig();

    /** 分批止盈：分多批按不同比例止盈 */
    private BatchConfig batchTakeProfit = new BatchConfig();

    /** 分批止损：分多批按不同比例止损 */
    private BatchConfig batchStopLoss = new BatchConfig();

    /** 分批移动止盈 */
    private BatchConfig batchTrailingGain = new BatchConfig();

    /** 分批移动止损 */
    private BatchConfig batchTrailingLoss = new BatchConfig();

    /** 固定百分比止损 */
    private FixedPercentStopLossConfig fixedPercentStopLoss = new FixedPercentStopLossConfig();

    /** 固定百分比止盈 */
    private FixedPercentTakeProfitConfig fixedPercentTakeProfit = new FixedPercentTakeProfitConfig();

    /** 移动止损：价格回调一定比例后触发止损 */
    private TrailingConfig trailingStopLoss = new TrailingConfig();

    /** 移动止盈：价格回调一定比例后触发止盈 */
    private TrailingConfig trailingStopGain = new TrailingConfig();

    /**
     * 将前端传入的百分比数值（1 = 1%）统一转为小数（0.01 = 1%）
     */
    private static double toDecimalPercent(double value) {
        return value / 100.0;
    }

    /**
     * 将前端传来的旧格式 exitRules map 转换为 ExitRulesConfigDTO。
     * 兼容新旧两种格式：V7 嵌套对象格式 和 V6 扁平字段格式。
     */
    @SuppressWarnings("unchecked")
    public static ExitRulesConfigDTO fromExitRulesMap(Map<String, Object> map) {
        ExitRulesConfigDTO dto = new ExitRulesConfigDTO();
        if (map == null || map.isEmpty()) return dto;

        // --- 信号反转出场 ---
        Object reversalEnabled = map.get("signalReversalExitEnabled");
        if (reversalEnabled instanceof Boolean) {
            dto.getSignalReversal().setEnabled((Boolean) reversalEnabled);
        } else if (reversalEnabled != null) {
            dto.getSignalReversal().setEnabled("true".equalsIgnoreCase(reversalEnabled.toString()));
        }
        Object reversalMustWin = map.get("signalReversalMustWin");
        if (reversalMustWin instanceof Boolean) {
            dto.getSignalReversal().setMustWin((Boolean) reversalMustWin);
        } else if (reversalMustWin != null) {
            dto.getSignalReversal().setMustWin("true".equalsIgnoreCase(reversalMustWin.toString()));
        }

        // --- 分批止盈 ---
        Object batchEnabled = map.get("batchTakeProfitEnabled");
        if (batchEnabled instanceof Boolean) {
            dto.getBatchTakeProfit().setEnabled((Boolean) batchEnabled);
        } else if (batchEnabled != null) {
            dto.getBatchTakeProfit().setEnabled("true".equalsIgnoreCase(batchEnabled.toString()));
        }
        Object batchCount = map.get("batchTakeProfitCount");
        if (batchCount instanceof Number) {
            dto.getBatchTakeProfit().setCount(((Number) batchCount).intValue());
        }
        // 从 batchTakeProfitPlans 提取百分比和比率
        Object plansObj = map.get("batchTakeProfitPlans");
        if (plansObj != null) {
            List<Double> percents = new ArrayList<>();
            List<Double> ratios = new ArrayList<>();
            if (plansObj instanceof List) {
                for (Object item : (List<Object>) plansObj) {
                    if (item instanceof Map) {
                        Map<String, Object> plan = (Map<String, Object>) item;
                        Object percentObj = plan.get("percent");
                        if (percentObj instanceof Number) {
                            percents.add(((Number) percentObj).doubleValue());
                        }
                        Object ratioObj = plan.get("positionPercent");
                        if (!(ratioObj instanceof Number)) ratioObj = plan.get("ratio");
                        if (ratioObj instanceof Number) {
                            double r = ((Number) ratioObj).doubleValue();
                            ratios.add(toDecimalPercent(r));
                        }
                    }
                }
            }
            if (!percents.isEmpty()) dto.getBatchTakeProfit().setPercents(percents);
            if (!ratios.isEmpty()) dto.getBatchTakeProfit().setRatios(ratios);
        }

        // --- stopLoss → fixedPercentStopLoss + trailingStopLoss ---
        Object stopLossObj = map.get("stopLoss");
        if (stopLossObj instanceof Map) {
            Map<String, Object> sl = (Map<String, Object>) stopLossObj;
            Map<String, Object> fp = (Map<String, Object>) sl.get("fixed_percent");
            if (fp != null) {
                Object enabled = fp.get("enabled");
                boolean fpEnabled = enabled instanceof Boolean ? (Boolean) enabled : "true".equalsIgnoreCase(String.valueOf(enabled));
                dto.getFixedPercentStopLoss().setEnabled(fpEnabled);
                Object percent = fp.get("percent");
                if (percent instanceof Number) {
                    double pct = ((Number) percent).doubleValue();
                    dto.getFixedPercentStopLoss().setPercent(toDecimalPercent(pct));
                }
            }
            Map<String, Object> fpt = (Map<String, Object>) sl.get("fixed_percent_trailing");
            if (fpt != null) {
                Object enabled = fpt.get("enabled");
                boolean trEnabled = enabled instanceof Boolean ? (Boolean) enabled : "true".equalsIgnoreCase(String.valueOf(enabled));
                dto.getTrailingStopLoss().setEnabled(trEnabled);
                Object percent = fpt.get("percent");
                if (percent instanceof Number) {
                    double pct = ((Number) percent).doubleValue();
                    dto.getTrailingStopLoss().setPercent(toDecimalPercent(pct));
                }
                Object barCount = fpt.get("barCount");
                if (barCount instanceof Number) {
                    dto.getTrailingStopLoss().setBarCount(((Number) barCount).intValue());
                }
            }
        }

        // --- takeProfit → fixedPercentTakeProfit + trailingStopGain + technical + smc ---
        Object takeProfitObj = map.get("takeProfit");
        if (takeProfitObj instanceof Map) {
            Map<String, Object> tp = (Map<String, Object>) takeProfitObj;
            // 固定百分比止盈
            Object tpEnabled = tp.get("enabled");
            if (tpEnabled instanceof Boolean ? (Boolean) tpEnabled : "true".equalsIgnoreCase(String.valueOf(tpEnabled))) {
                dto.getFixedPercentTakeProfit().setEnabled(true);
            }
            Object tpPercent = tp.get("percent");
            if (tpPercent instanceof Number) {
                double pct = ((Number) tpPercent).doubleValue();
                dto.getFixedPercentTakeProfit().setPercent(toDecimalPercent(pct));
            }
            // trailing stop gain
            Map<String, Object> tpFpt = (Map<String, Object>) tp.get("fixed_percent_trailing");
            if (tpFpt != null) {
                Object enabled = tpFpt.get("enabled");
                dto.getTrailingStopGain().setEnabled(enabled instanceof Boolean ? (Boolean) enabled : "true".equalsIgnoreCase(String.valueOf(enabled)));
                Object percent = tpFpt.get("percent");
                if (percent instanceof Number) {
                    double pct = ((Number) percent).doubleValue();
                    dto.getTrailingStopGain().setPercent(toDecimalPercent(pct));
                }
                Object barCount = tpFpt.get("barCount");
                if (barCount instanceof Number) {
                    dto.getTrailingStopGain().setBarCount(((Number) barCount).intValue());
                }
            }
            // technical take profit conditions
            Object technicalObj = tp.get("technical");
            if (technicalObj instanceof Map) {
                Map<String, Object> tech = (Map<String, Object>) technicalObj;
                Object techEnabled = tech.get("enabled");
                if (techEnabled instanceof Boolean && (Boolean) techEnabled) {
                    dto.getTechnical().setEnabled(true);
                } else if (techEnabled != null && "true".equalsIgnoreCase(techEnabled.toString())) {
                    dto.getTechnical().setEnabled(true);
                }
                List<TechnicalCondition> conditions = new ArrayList<>();
                // macd
                Object macdEnabled = tech.get("macdEnabled");
                if (macdEnabled instanceof Boolean ? (Boolean) macdEnabled : "true".equalsIgnoreCase(String.valueOf(macdEnabled))) {
                    TechnicalCondition cond = new TechnicalCondition();
                    cond.setType("MACD");
                    Object mustWin = tech.get("macdMustWin");
                    cond.setMustWin(mustWin instanceof Boolean ? (Boolean) mustWin : "true".equalsIgnoreCase(String.valueOf(mustWin)));
                    conditions.add(cond);
                }
                // pin_volume
                Object pinEnabled = tech.get("pinVolumeEnabled");
                if (pinEnabled instanceof Boolean ? (Boolean) pinEnabled : "true".equalsIgnoreCase(String.valueOf(pinEnabled))) {
                    TechnicalCondition cond = new TechnicalCondition();
                    cond.setType("PIN_VOLUME");
                    Object mustWin = tech.get("pinVolumeMustWin");
                    cond.setMustWin(mustWin instanceof Boolean ? (Boolean) mustWin : "true".equalsIgnoreCase(String.valueOf(mustWin)));
                    conditions.add(cond);
                }
                if (!conditions.isEmpty()) {
                    dto.getTechnical().setConditions(conditions);
                    dto.getTechnical().setEnabled(true);
                }
            }
            // smc exit in takeProfit（已迁移到独立的 smcExit 嵌套结构，此处不再读取扁平字段）
        }

        // --- smcExit 独立出场配置 ---
        Object smcExitObj = map.get("smcExit");
        if (smcExitObj instanceof Map) {
            String smcJson = JSONUtil.toJsonStr(smcExitObj);
            SmcConfig smcConfig = JSONUtil.toBean(smcJson, SmcConfig.class);
            dto.setSmcExit(smcConfig);
        }

        // --- structureStopProfit 结构止盈止损 ---
        Object ssObj = map.get("structureStopProfit");
        if (ssObj instanceof Map) {
            String ssJson = JSONUtil.toJsonStr(ssObj);
            StructureStopProfitConfig ssConfig = JSONUtil.toBean(ssJson, StructureStopProfitConfig.class);
            dto.setStructureStopProfit(ssConfig);
        }

        // --- 时间止盈 ---
        Object timeBasedTpObj = map.get("timeBasedTakeProfit");
        if (timeBasedTpObj instanceof Map) {
            Map<String, Object> tbTp = (Map<String, Object>) timeBasedTpObj;
            Object enabled = tbTp.get("enabled");
            dto.getTimeBasedTakeProfit().setEnabled(enabled instanceof Boolean ? (Boolean) enabled : "true".equalsIgnoreCase(String.valueOf(enabled)));
            Object ratio = tbTp.get("ratio");
            if (!(ratio instanceof Number)) ratio = tbTp.get("percent");
            if (ratio instanceof Number) {
                double r = ((Number) ratio).doubleValue();
                dto.getTimeBasedTakeProfit().setRatio(toDecimalPercent(r));
            }
            Object days = tbTp.get("days");
            if (days instanceof List) {
                List<String> dayList = new ArrayList<>();
                for (Object d : (List<Object>) days) {
                    if (d != null) dayList.add(d.toString());
                }
                dto.getTimeBasedTakeProfit().setDays(dayList);
            }
        }

        // --- 时间止损 ---
        Object timeBasedSlObj = map.get("timeBasedStopLoss");
        if (timeBasedSlObj instanceof Map) {
            Map<String, Object> tbSl = (Map<String, Object>) timeBasedSlObj;
            Object enabled = tbSl.get("enabled");
            dto.getTimeBasedStopLoss().setEnabled(enabled instanceof Boolean ? (Boolean) enabled : "true".equalsIgnoreCase(String.valueOf(enabled)));
            Object ratio = tbSl.get("ratio");
            if (!(ratio instanceof Number)) ratio = tbSl.get("percent");
            if (ratio instanceof Number) {
                double r = ((Number) ratio).doubleValue();
                dto.getTimeBasedStopLoss().setRatio(toDecimalPercent(r));
            }
            Object days = tbSl.get("days");
            if (days instanceof List) {
                List<String> dayList = new ArrayList<>();
                for (Object d : (List<Object>) days) {
                    if (d != null) dayList.add(d.toString());
                }
                dto.getTimeBasedStopLoss().setDays(dayList);
            }
        }

        // --- 分批移动止盈 ---
        Object batchTgEnabled = map.get("batchTrailingGainEnabled");
        if (batchTgEnabled instanceof Boolean) {
            dto.getBatchTrailingGain().setEnabled((Boolean) batchTgEnabled);
        } else if (batchTgEnabled != null) {
            dto.getBatchTrailingGain().setEnabled("true".equalsIgnoreCase(batchTgEnabled.toString()));
        }
        Object batchTgCount = map.get("batchTrailingGainCount");
        if (batchTgCount instanceof Number) {
            dto.getBatchTrailingGain().setCount(((Number) batchTgCount).intValue());
        }
        Object batchTgPlansObj = map.get("batchTrailingGainPlans");
        if (batchTgPlansObj != null) {
            List<Double> percents = new ArrayList<>();
            List<Double> ratios = new ArrayList<>();
            if (batchTgPlansObj instanceof List) {
                for (Object item : (List<Object>) batchTgPlansObj) {
                    if (item instanceof Map) {
                        Map<String, Object> plan = (Map<String, Object>) item;
                        Object percentObj = plan.get("percent");
                        if (percentObj instanceof Number) {
                            percents.add(((Number) percentObj).doubleValue());
                        }
                        Object ratioObj = plan.get("positionPercent");
                        if (!(ratioObj instanceof Number)) ratioObj = plan.get("ratio");
                        if (ratioObj instanceof Number) {
                            double r = ((Number) ratioObj).doubleValue();
                            ratios.add(toDecimalPercent(r));
                        }
                    }
                }
            }
            if (!percents.isEmpty()) dto.getBatchTrailingGain().setPercents(percents);
            if (!ratios.isEmpty()) dto.getBatchTrailingGain().setRatios(ratios);
        }

        // --- 分批移动止损 ---
        Object batchTlEnabled = map.get("batchTrailingLossEnabled");
        if (batchTlEnabled instanceof Boolean) {
            dto.getBatchTrailingLoss().setEnabled((Boolean) batchTlEnabled);
        } else if (batchTlEnabled != null) {
            dto.getBatchTrailingLoss().setEnabled("true".equalsIgnoreCase(batchTlEnabled.toString()));
        }
        Object batchTlCount = map.get("batchTrailingLossCount");
        if (batchTlCount instanceof Number) {
            dto.getBatchTrailingLoss().setCount(((Number) batchTlCount).intValue());
        }
        Object batchTlPlansObj = map.get("batchTrailingLossPlans");
        if (batchTlPlansObj != null) {
            List<Double> percents = new ArrayList<>();
            List<Double> ratios = new ArrayList<>();
            if (batchTlPlansObj instanceof List) {
                for (Object item : (List<Object>) batchTlPlansObj) {
                    if (item instanceof Map) {
                        Map<String, Object> plan = (Map<String, Object>) item;
                        Object percentObj = plan.get("percent");
                        if (percentObj instanceof Number) {
                            percents.add(((Number) percentObj).doubleValue());
                        }
                        Object ratioObj = plan.get("positionPercent");
                        if (!(ratioObj instanceof Number)) ratioObj = plan.get("ratio");
                        if (ratioObj instanceof Number) {
                            double r = ((Number) ratioObj).doubleValue();
                            ratios.add(toDecimalPercent(r));
                        }
                    }
                }
            }
            if (!percents.isEmpty()) dto.getBatchTrailingLoss().setPercents(percents);
            if (!ratios.isEmpty()) dto.getBatchTrailingLoss().setRatios(ratios);
        }

        return dto;
    }

    /**
     * 将 JSON 字符串反序列化为 ExitRulesConfigDTO。
     * 如果 json 为 null 或空，返回默认配置（全部 enabled 默认值）。
     */
    public static ExitRulesConfigDTO fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ExitRulesConfigDTO();
        }
        return JSONUtil.toBean(json, ExitRulesConfigDTO.class);
    }

    /** 序列化为 JSON 字符串 */
    public String toJson() {
        return JSONUtil.toJsonStr(this);
    }

    // ==================== 嵌套配置类 ====================

    @Data
    @NoArgsConstructor
    public static class SignalReversalConfig {
        private boolean enabled = false;
        private boolean mustWin = false;
    }

    @Data
    @NoArgsConstructor
    public static class TechnicalConfig {
        private boolean enabled = false;
        private List<TechnicalCondition> conditions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class TechnicalCondition {
        private String type;        // MACD, PIN_VOLUME
        private boolean mustWin;
        private double commissionRate = 0.001;
    }

    @Data
    @NoArgsConstructor
    public static class TimeBasedConfig {
        private boolean enabled = false;
        private double ratio = 0.005;
        private List<String> days = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class SmcConfig {
        private boolean enabled = false;
        private ActiveTakeProfitConfig activeTakeProfit = new ActiveTakeProfitConfig();
        private PassiveExitConfig passiveExit = new PassiveExitConfig();
        private TrailingStopConfig trailingStop = new TrailingStopConfig();
        private InitialStopOffsetConfig initialStopOffset = new InitialStopOffsetConfig();
        private ReferenceConfig reference = new ReferenceConfig();
    }

    @Data
    @NoArgsConstructor
    public static class ActiveTakeProfitConfig {
        private boolean enabled = false;
        private ObConfig ob15m = new ObConfig();
        private ObConfig ob1h = new ObConfig();
        private HigherConfig higher = new HigherConfig();
    }

    @Data
    @NoArgsConstructor
    public static class ObConfig {
        private boolean enabled = true;
        private int closePercent = 50;
    }

    @Data
    @NoArgsConstructor
    public static class HigherConfig {
        private boolean enabled = false;
        private String period = "240";
        private int closePercent = 100;
    }

    @Data
    @NoArgsConstructor
    public static class PassiveExitConfig {
        private boolean enabled = false;
        private boolean reverseChoch = true;
        private boolean reverseBos = false;
    }

    @Data
    @NoArgsConstructor
    public static class TrailingStopConfig {
        private boolean enabled = false;
        private MoveToBreakevenConfig moveToBreakeven = new MoveToBreakevenConfig();
        private TrackStructureConfig trackStructure = new TrackStructureConfig();
        private String mode = "AUTO";
        private String gear = "MODERATE";
    }

    @Data
    @NoArgsConstructor
    public static class MoveToBreakevenConfig {
        private boolean enabled = true;
        private double triggerR = 1.5;
    }

    @Data
    @NoArgsConstructor
    public static class TrackStructureConfig {
        private boolean enabled = true;
        private String period = "15";
        private String point = "internal";
    }

    @Data
    @NoArgsConstructor
    public static class InitialStopOffsetConfig {
        private boolean enabled = false;
        private String mode = "percent";
        private double percent = 0.5;
        private double points = 0.01;
        private boolean stopLossObSameAsTarget = false;
        private boolean fixed15mOrderBlock = true;
    }

    @Data
    @NoArgsConstructor
    public static class ReferenceConfig {
        private String stopStructurePeriod = "15";
        private String targetPeriod = "60";
    }

    @Data
    @NoArgsConstructor
    public static class BatchConfig {
        private boolean enabled = false;
        private int count = 3;
        private List<Double> ratios = new ArrayList<>();
        private List<Double> percents = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class FixedPercentStopLossConfig {
        private boolean enabled = false;
        private double percent = 0.05;
    }

    @Data
    @NoArgsConstructor
    public static class FixedPercentTakeProfitConfig {
        private boolean enabled = false;
        private double percent = 0.03;
    }

    @Data
    @NoArgsConstructor
    public static class TrailingConfig {
        private boolean enabled = false;
        private double percent = 0.03;
        private int barCount = 5;
    }

    // ==================== 结构止盈止损配置（SmcStructuredExitRule） ====================

    @Data
    @NoArgsConstructor
    public static class StructureStopProfitConfig {
        private boolean enabled = true;
        /** 模式：manual=手动模式，auto=自动模式 */
        private String mode = "auto";
        private DynamicStopLossConfig dynamicStopLoss = new DynamicStopLossConfig();
        private TakeProfitActiveConfig takeProfitActive = new TakeProfitActiveConfig();
        private TrailingProtectionConfig trailingProtection = new TrailingProtectionConfig();
        private ReferenceConfig reference = new ReferenceConfig();

        @Data
        @NoArgsConstructor
        public static class DynamicStopLossConfig {
            private double dailyBuffer = 0.08;
            private double bufferBuffer = 0.12;
            private double ultimateBuffer = 0.25;
            private boolean autoEnableUltimate = true;
            private int dailyPeriod = 15;       // 15分钟
            private int bufferPeriod = 60;      // 60分钟（1H）
            private int ultimatePeriod = 240;   // 240分钟（4H）
        }

        @Data
        @NoArgsConstructor
        public static class TakeProfitActiveConfig {
            private int swingClosePct = 50;       // TP1 摆动点止盈平仓比例（%）
            private int ob1hClosePct = 50;        // TP2 1H对立OB止盈平仓比例（%）
            private Integer fvgClosePct;          // FVG止盈平仓比例（null=未启用）
            private double minRiskReward = 1.2;
            private double maxRiskReward = 4.0;
        }

        @Data
        @NoArgsConstructor
        public static class TrailingProtectionConfig {
            private boolean trailingEnabled = true;
            private double trailingBuffer = 0.08;     // 移动止损缓冲（0.08%）
            private boolean breakevenEnabled = true;
            private double breakevenBuffer = 0.05;    // 保本止损缓冲（0.05%）
        }

        @Data
        @NoArgsConstructor
        public static class ReferenceConfig {
            private int stopLossPeriod = 15;       // 止损参考周期（分钟）
            private int takeProfitPeriod = 60;     // 止盈参考周期（分钟）
        }
    }
}
