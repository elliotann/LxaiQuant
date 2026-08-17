package com.chain.ai.trade.engine.signal.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.common.entity.constants.TrendType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.SmcBarResult;
import com.chain.ai.trade.engine.data.entity.dos.SmcOrderBlock;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.entity.dto.PriceTarget;
import com.chain.ai.trade.engine.entity.dto.StopLossLevel;
import com.chain.ai.trade.engine.signal.service.impl.SmcIndicatorService;
import com.chain.ai.trade.engine.signal.strategy.SignalStrategyRouter;
import com.chain.ai.trade.engine.signal.entity.dto.*;
import com.chain.ai.trade.engine.signal.feature.BacktestContextHolder;
import com.chain.ai.trade.engine.signal.feature.SignalFeatureProvider;
import com.chain.ai.trade.engine.signal.rule.*;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.ChaosExceptionEvaluator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.PositionRatioCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.WaveIndexCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.trend.SmcTrendUtils;
import com.chain.ai.trade.engine.data.entity.dto.smc.ChaosExceptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.chain.ai.trade.common.entity.constants.SignalType.*;

@Slf4j
@Component
public abstract class DefaultSignService implements ISignService {

    // ==================== 常量 ====================
    private static final ThreadLocal<Boolean> SIGN_SEND_ENABLED = ThreadLocal.withInitial(() -> true);
    private static final int SMC_SWINGS_LENGTH = 50;
    private static final int SMC_INTERNAL_OB_COUNT = 3;
    private static final int SMC_SWING_OB_COUNT = 3;
    private static final double EQUAL_HIGH_LOW_THRESHOLD = 0.1;
    private static final int EQUAL_HIGH_LOW_LENGTH = 3;
    private static final int MIN_KLINES_FOR_SMC = 80;

    // ==================== 可配置参数（子类可覆盖）====================
    /** 是否生成 SMC 展示型明细数据（smc全量/关键点位/止盈止损/波次看板）。生产关闭以省性能，本地测试开启用于核对 */
    @Value("${signal.smc.detail-enabled:false}")
    protected boolean smcDetailEnabled = false;

    protected double smcStopLossOffset = 0.005;
    protected double smcMinTargetSpaceRatio = 0.005;
    protected double maxRiskPercent = 0.02;
    protected double minRR = 1.2;
    protected boolean useEmaScore = false;

    // ==================== 依赖注入 ====================
    @Autowired
    public ITradeSignalSignalService tradeSignalSignalService;

    @Autowired
    protected ICandlestickService candlestickService;

    @Autowired
    protected ITechnicalSignalService technicalSignalService;

    @Autowired
    protected SignalStrategyRouter signalStrategyRouter;

    @Autowired
    protected SignalServiceConfigService signalServiceConfigService;

    @Autowired(required = false)
    protected IndicatorProviderRegistry indicatorProviderRegistry;

    @Autowired
    private SmcIndicatorService smcIndicatorService; // 新增注入

    @Autowired
    private SignalAlternateLogService signalAlternateLogService; // L1 信号交替配对

    @Autowired
    private SignalFeatureProvider featureProvider;

    @Autowired(required = false)
    private BacktestContextHolder backtestContextHolder;

    @Value("${quant.env:live}")
    private String quantEnv = "live";

    protected String signalServiceKey;

    /**
     * 获取当前信号服务对应的规则配置 serviceKey（供离线重算等场景映射 strategyName -> serviceKey）
     */
    public String getSignalServiceKey() {
        return signalServiceKey;
    }

    // ==================== 信号发送控制 ====================
    public static void disableSignalSending() {
        SIGN_SEND_ENABLED.set(false);
    }

    public static void enableSignalSending() {
        SIGN_SEND_ENABLED.set(true);
    }

    protected static boolean isSignalSendingEnabled() {
        Boolean enabled = SIGN_SEND_ENABLED.get();
        return enabled == null || enabled;
    }

    // ==================== 公开接口实现 ====================
    @Override
    public WeightAndConfidenceDto getWeightAndConfidence(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());


        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();
        if (kLines.size() < 21) {
            return new WeightAndConfidenceDto(BigDecimal.ZERO, null, null, null);
        }

        String breakoutSignal = calcDto.getSignalType().name();
        boolean isValidDirection = (OrderSideEnum.BUY.getCode().equals(calcDto.getOpenSide()) && SignalType.LONG.name().equals(breakoutSignal)) ||
                (OrderSideEnum.SELL.getCode().equals(calcDto.getOpenSide()) && SignalType.SHORT.name().equals(breakoutSignal));
        if (!isValidDirection) {
            return new WeightAndConfidenceDto(BigDecimal.ZERO, null, null, null);
        }

        double currentPrice = kLines.get(kLines.size() - 1).getOpenPrice().doubleValue();
        long signalTime = kLines.get(kLines.size() - 1).getId();
        PriceTargetsInfo priceTargetsInfo = null;
        if("2025-01-01 06:00:00".equals(DateUtil.longConvertDateTime(signalTime))){
            System.out.printf("");
        }
        // 规则引擎优先
        if (signalServiceKey != null && !signalServiceKey.isEmpty()) {
            WeightRuleConfig weightRules = signalServiceConfigService.getWeightRules(signalServiceKey);
            boolean useRuleEngine = weightRules != null && weightRules.isEnabled()
                    && weightRules.getRules() != null && !weightRules.getRules().isEmpty();
            if (useRuleEngine) {
                SmcBarResult smc15 = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKXMIN15, signalTime);
                SmcBarResult smc1h = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKXMIN60, signalTime);
                RuleEvaluationResult result = evaluateWeightRuleEngine(calcDto, symbol, currentPrice, signalTime, smc15, smc1h, weightRules);
                if (result != null) {
                    if (result.isVetoed()) {
                        log.debug("权重规则否决开仓: symbol={}, reason={}", symbol, result.getReason());
                        WeightAndConfidenceDto vetoResult = new WeightAndConfidenceDto(BigDecimal.ZERO, priceTargetsInfo, result.getReason(), result.getTrendState());
                        return vetoResult;
                    }
                    return new WeightAndConfidenceDto(BigDecimal.valueOf(result.getFinalWeight()).setScale(2, RoundingMode.HALF_UP),
                            priceTargetsInfo, null, result.getTrendState());
                }
            }
        }
        return new WeightAndConfidenceDto(BigDecimal.valueOf(0).setScale(2, RoundingMode.HALF_UP),
                priceTargetsInfo, null, null);
    }

    // ==================== 信号保存 ====================
    protected Long saveSign(IndicatorCalcDto calcDto, SignalType signalType) {
        return saveSign(calcDto, signalType, null, null, null);
    }

    protected Long saveSign(IndicatorCalcDto calcDto, SignalType signalType,
                            String customKlineTime, String customDataInterval, String customStrategyType) {
        if (!isSignalSendingEnabled()) {
            log.debug("信号发送已禁用，跳过信号保存。信号类型: {}, 线程: {}", signalType, Thread.currentThread().getName());
            return null;
        }

        if (!LONG.equals(signalType) && !SHORT.equals(signalType) && !CLOSE_LONG.equals(signalType) && !CLOSE_SHORT.equals(signalType)) {
            return null;
        }

        try {
            TechnicalSignalDTO technicalSignalDTO = new TechnicalSignalDTO();
            List<Candlestick> kLines = calcDto.getKLines();
            Candlestick lastKline = kLines.get(kLines.size() - 1);
            // 关键K线：信号确认K线的上一根（倒数第二根）
            Candlestick keyKline = kLines.get(kLines.size() - 2);
            String symbol = calcDto.getSymbol();

            // ================== 1. 基础信息赋值（完全保留你原来的写法） ==================
            technicalSignalDTO.setSymbol(symbol.endsWith("-SWAP") ? symbol : symbol + "-SWAP");
            technicalSignalDTO.setTimeframe(customDataInterval != null && !customDataInterval.isEmpty() ?
                    customDataInterval : lastKline.getCandlestickIntervalEnum().getCode());
            technicalSignalDTO.setKlineTime(customKlineTime != null && !customKlineTime.isEmpty() ?
                    customKlineTime : lastKline.getTimeStr());
            technicalSignalDTO.setKlineTimestamp(lastKline.getId());
            technicalSignalDTO.setCurrentPrice(lastKline.getClosePrice());
            // 关键K线（倒数第二根）的开高低收
            technicalSignalDTO.setOpenPrice(keyKline.getOpenPrice());
            technicalSignalDTO.setClosePrice(keyKline.getClosePrice());
            technicalSignalDTO.setHighPrice(keyKline.getHighPrice());
            technicalSignalDTO.setLowPrice(keyKline.getLowPrice());
            technicalSignalDTO.setIndicator(calcDto.getRobotName());
            technicalSignalDTO.setStrategyName(customStrategyType != null && !customStrategyType.isEmpty() ?
                    customStrategyType : calcDto.getRobotName());

            // ================== 2. 计算权重（保留原有逻辑，会触发 15m/1h 缓存） ==================
            long wt0 = System.currentTimeMillis();
            String technicalDirection = determineTechnicalDirection(signalType, calcDto);
            calcDto.setSignalType(signalType);
            WeightAndConfidenceDto weightAndConfidence = getWeightAndConfidence(calcDto);
            log.info("信号耗时：{} - saveSign/getWeightAndConfidence: {}ms",System.currentTimeMillis() ,System.currentTimeMillis() - wt0);

            technicalSignalDTO.setTechnicalDirection(technicalDirection);
            technicalSignalDTO.setSignalStrength(weightAndConfidence.getWeight());

            // ================== 3. 【核心改造】直接在入库前构建完整 extraParams ==================
            long extraT0 = System.currentTimeMillis();
            String extraParams = buildFullExtraParams(
                    symbol,
                    lastKline.getId(),
                    weightAndConfidence.getReason(),
                    calcDto.getRobotName(),      // robotId 用于计算 CriticalLevel
                    technicalDirection,
                    lastKline.getClosePrice()
            );
            log.info("信号耗时 - saveSign/buildFullExtraParams: {}ms", System.currentTimeMillis() - extraT0);

            technicalSignalDTO.setExtraParams(extraParams);
            technicalSignalDTO.setMarketTrend(weightAndConfidence.getTrendState() != null ?
                    weightAndConfidence.getTrendState().name() : null);
            technicalSignalDTO.setSignalHash(technicalSignalService.generateSignalHash(technicalSignalDTO));

            // ================== 4. 其他可选参数 ==================
            if (calcDto.getEntryType() != null) {
                technicalSignalDTO.setEntryType(calcDto.getEntryType());
            }
            if (calcDto.getLimitPrice() != null) {
                technicalSignalDTO.setLimitPrice(calcDto.getLimitPrice());
            }
            if (lastKline.getExchange() != null) {
                technicalSignalDTO.setDataSource(lastKline.getExchange().name());
            }

            // ================== 5. 直接保存（一次落库，不再二次更新） ==================
            long dbT0 = System.currentTimeMillis();
            Long signalId = technicalSignalService.saveTechnicalSignal(technicalSignalDTO);
            log.debug("信号耗时：{} - saveSign/DB save: {}ms", System.currentTimeMillis(),System.currentTimeMillis() - dbT0);
            calcDto.setCurrentCandlestick(lastKline);

            // ================== 5.5 L1 信号交替配对（特征工程） ==================
            if (signalId != null) {
                signalAlternateLogService.tryPairAndInsert(signalId, calcDto);
            }

            log.info("技术信号保存成功: id={}, symbol={}, indicator={}, direction={}, strength={}",
                    signalId, technicalSignalDTO.getSymbol(), technicalSignalDTO.getIndicator(),
                    technicalDirection, weightAndConfidence.getWeight());
            return signalId;

        } catch (Exception e) {
            log.error("保存技术信号失败", e);
            return null;
        } finally {
            // ================== 6. 清除当前线程的 SMC 缓存，防止内存泄漏 ==================
            SmcIndicatorService.clearCache();
        }
    }

    /**
     * 在信号入库前，一次性构建包含所有周期 SMC 数据和关键价位的 extraParams
     */
    private String buildFullExtraParams(String symbol, long signalTimeMs, String reason,
                                        String robotId, String direction, BigDecimal currentPrice) {
        // 生产环境默认关闭展示型明细数据，仅保留加仓门控依赖的信号共振方向
        if (!smcDetailEnabled) {
            return buildMinimalExtraParams(symbol, signalTimeMs, reason, direction);
        }

        // 定义需要采集的周期（15m、1h 已在 getWeightAndConfidence 中缓存，此处直接命中）
        CandlestickIntervalEnum[] periods = {
                CandlestickIntervalEnum.OKXMIN3,
                CandlestickIntervalEnum.OKXMIN15,
                CandlestickIntervalEnum.OKXMIN60,
                CandlestickIntervalEnum.OKX4HOUR,
                CandlestickIntervalEnum.OKX1D
        };
        String[] labels = {"3M", "15M", "1H", "4H", "1D"};

        // 1. 收集 SmcBarResult（供序列化到 extraParams.smc 字段）
        Map<String, SmcBarResult> barResults = new LinkedHashMap<>();
        // 2. 收集原始 Result（供计算 CriticalLevel 使用）
        Map<String, SmartMoneyConceptsIndicator.Result> rawResults = new LinkedHashMap<>();

        for (int i = 0; i < periods.length; i++) {
            // 这里会命中缓存，只有 3M、4H、1D 会触发首次加载计算（15m、1h 直接返回）
            SmcBarResult barResult = computeSmcSnapshot(symbol, periods[i], signalTimeMs);
            barResults.put(labels[i], barResult);

            // 获取原始结果（同样命中缓存，无额外计算）
            SmartMoneyConceptsIndicator.Result rawResult = computeSmcResult(symbol, periods[i], signalTimeMs);
            rawResults.put(labels[i], rawResult);
        }

        // 3. 计算关键价位（CriticalLevels）
        List<CriticalLevel> criticalLevels = null;
        PriceTargetsInfo priceTargetsInfo = null;
        if (robotId != null && !robotId.isEmpty()) {
            criticalLevels = signalStrategyRouter.resolve(robotId)
                    .calculate(rawResults, direction, currentPrice.doubleValue());
            priceTargetsInfo = convertCritLevelToPriceTargetsInfo(criticalLevels);
        }

        // 4. 组装最终 JSON（直接沿用你原来的 mergeExtraParams 逻辑，但内联在这里避免二次拼接）
        JSONObject root = new JSONObject();
        if (reason != null) {
            root.set("reason", reason);
        }

        JSONObject smcJson = new JSONObject();
        for (Map.Entry<String, SmcBarResult> entry : barResults.entrySet()) {
            smcJson.set(entry.getKey().toLowerCase(),
                    entry.getValue() != null ? toSafeJsonObj(entry.getValue()) : null);
        }
        root.set("smc", smcJson);

        if (criticalLevels != null && !criticalLevels.isEmpty()) {
            root.set("criticalLevels", toSafeJsonArray(criticalLevels));
        }
        if (priceTargetsInfo != null) {
            root.set("priceTargetsInfo", toSafeJsonObj(priceTargetsInfo));
        }

        // 5. 构建简版 SMC 看板数据（方向矩阵、周期共振等）
        // 获取历史序列用于三层评估
        Map<String, List<SmartMoneyConceptsIndicator.Result>> historyResults = new LinkedHashMap<>();
        historyResults.put("15M", smcIndicatorService.getSmcResultHistory(
                symbol, CandlestickIntervalEnum.OKXMIN15, signalTimeMs, 100));
        historyResults.put("1H", smcIndicatorService.getSmcResultHistory(
                symbol, CandlestickIntervalEnum.OKXMIN60, signalTimeMs, 120));
        historyResults.put("4H", smcIndicatorService.getSmcResultHistory(
                symbol, CandlestickIntervalEnum.OKX4HOUR, signalTimeMs, 80));

        JSONObject dashboardJson = buildSmcDashboard(rawResults, historyResults, direction, currentPrice.doubleValue());
        root.set("smcDashboard", dashboardJson);

        return root.toString();
    }

    /**
     * 构建精简版 extraParams：仅保留加仓门控所需的信号共振方向（smcDashboard.alignment）。
     * <p>跳过 smc 全量、关键点位、止盈止损、波次看板等展示型数据，避免生产环境不必要的计算。</p>
     */
    private String buildMinimalExtraParams(String symbol, long signalTimeMs, String reason, String direction) {
        CandlestickIntervalEnum[] periods = {
                CandlestickIntervalEnum.OKXMIN3,
                CandlestickIntervalEnum.OKXMIN15,
                CandlestickIntervalEnum.OKXMIN60,
                CandlestickIntervalEnum.OKX4HOUR,
                CandlestickIntervalEnum.OKX1D
        };
        Map<String, SmartMoneyConceptsIndicator.Result> rawResults = new LinkedHashMap<>();
        for (CandlestickIntervalEnum period : periods) {
            rawResults.put(period.name(), computeSmcResult(symbol, period, signalTimeMs));
        }

        JSONObject dashboard = new JSONObject();
        dashboard.set("alignment", computeAlignment(rawResults, direction));

        JSONObject root = new JSONObject();
        if (reason != null) {
            root.set("reason", reason);
        }
        root.set("smcDashboard", dashboard);
        return root.toString();
    }

    /**
     * 从原始 SMC 结果中构建简版看板数据（方向矩阵、周期共振、趋势评估、三层波次等）。
     * <p>不重新拉取K线，仅利用 buildFullExtraParams 中已有的 rawResults 和 historyResults。</p>
     */
    private JSONObject buildSmcDashboard(Map<String, SmartMoneyConceptsIndicator.Result> rawResults,
                                          Map<String, List<SmartMoneyConceptsIndicator.Result>> historyResults,
                                          String signalDirection, double currentPrice) {
        JSONObject dashboard = new JSONObject();
        if (rawResults == null || rawResults.isEmpty()) {
            return dashboard;
        }

        // 1. 方向矩阵：每个周期的 swingTrend 方向
        JSONObject matrix = new JSONObject();
        int bullCount = 0;
        int bearCount = 0;
        for (Map.Entry<String, SmartMoneyConceptsIndicator.Result> entry : rawResults.entrySet()) {
            SmartMoneyConceptsIndicator.Result r = entry.getValue();
            if (r == null) continue;
            int st = r.getSwingTrend();
            String dir = st >= 0 ? "多头" : "空头";
            matrix.set(entry.getKey(), dir);
            if (st >= 0) bullCount++; else bearCount++;
        }
        dashboard.set("matrix", matrix);

        // 2. 周期共振
        int total = bullCount + bearCount;
        String resonance;
        if (bullCount == total) {
            resonance = "多方共振";
        } else if (bearCount == total) {
            resonance = "空方共振";
        } else if (bullCount > bearCount) {
            resonance = "偏多(" + bullCount + "/" + total + ")";
        } else {
            resonance = "偏空(" + bearCount + "/" + total + ")";
        }
        dashboard.set("resonance", resonance);

        // 3. 信号方向与共振方向一致性（信号共振）
        dashboard.set("alignment", computeAlignment(rawResults, signalDirection));

        // 4. 三层评估：战略层(4H) / 战术层(1H) / 执行层(15M)
        JSONObject layers = new JSONObject();
        appendLayerData(layers, "4H", rawResults.get("4H"), historyResults.get("4H"), signalDirection, currentPrice);
        appendLayerData(layers, "1H", rawResults.get("1H"), historyResults.get("1H"), signalDirection, currentPrice);
        appendLayerData(layers, "15M", rawResults.get("15M"), historyResults.get("15M"), signalDirection, currentPrice);
        dashboard.set("layers", layers);

        return dashboard;
    }

    /**
     * 计算信号共振方向（顺势做多 / 顺势做空 / 逆势 / 方向分歧）。
     * <p>基于多周期 swingTrend 统计：多数周期与信号方向一致为顺势，相反为逆势，相等为分歧。</p>
     *
     * @param rawResults      多周期 SMC Result（key 为周期标签，如 3M/15M/1H/4H/1D）
     * @param signalDirection 信号方向（LONG/SHORT）
     * @return 顺势做多 / 顺势做空 / 逆势 / 方向分歧
     */
    private String computeAlignment(Map<String, SmartMoneyConceptsIndicator.Result> rawResults, String signalDirection) {
        int bullCount = 0;
        int bearCount = 0;
        for (SmartMoneyConceptsIndicator.Result r : rawResults.values()) {
            if (r == null) continue;
            if (r.getSwingTrend() >= 0) bullCount++;
            else bearCount++;
        }

        boolean signalIsLong = "LONG".equalsIgnoreCase(signalDirection);
        boolean signalIsShort = "SHORT".equalsIgnoreCase(signalDirection);
        if (signalIsLong && bullCount > bearCount) {
            return "顺势做多";
        } else if (signalIsShort && bearCount > bullCount) {
            return "顺势做空";
        } else if (bullCount == bearCount) {
            return "方向分歧";
        }
        return "逆势";
    }

    /**
     * 为单个周期追加波次、位置比、结构年龄等评估数据。
     */
    private void appendLayerData(JSONObject layers, String period,
                                  SmartMoneyConceptsIndicator.Result result,
                                  List<SmartMoneyConceptsIndicator.Result> hist,
                                  String signalDirection, double currentPrice) {
        if (result == null) return;
        JSONObject layer = new JSONObject();

        // 摆动趋势
        int swingTrend = result.getSwingTrend();
        layer.set("swingTrend", swingTrend >= 0 ? "多头" : "空头");

        // 位置比
        boolean isBuy = "LONG".equalsIgnoreCase(signalDirection);
        double positionRatio = PositionRatioCalculator.calculate(result, isBuy, currentPrice);
        layer.set("positionRatio", Math.round(positionRatio * 100.0) / 100.0);

        if (hist != null && !hist.isEmpty()) {
            int lastIdx = hist.size() - 1;
            SmartMoneyConceptsIndicator.Result last = hist.get(lastIdx);

            // 波次（使用当前周期自身的趋势方向）
            boolean layerIsBuy = last.getSwingTrend() >= 0;
            int waveIndex = WaveIndexCalculator.calculate(hist, lastIdx, layerIsBuy);
            layer.set("waveIndex", waveIndex);
            layer.set("wavePhase", WaveIndexCalculator.getWavePhase(waveIndex, layerIsBuy));

            // 翻转次数
            int flipCount = WaveIndexCalculator.calculateFlipCount(hist, lastIdx, 20);
            layer.set("flipCount", flipCount);

            // 结构年龄
            int structureAge = WaveIndexCalculator.calculateStructureAge(hist, lastIdx);
            layer.set("structureAge", structureAge);

            // BOS
            layer.set("bullishBOS", last.isSwingBullishBOS());
            layer.set("bearishBOS", last.isSwingBearishBOS());
        }

        layers.set(period, layer);
    }

    private String determineTechnicalDirection(SignalType signalType, IndicatorCalcDto calcDto) {
        if (LONG.equals(signalType)) {
            calcDto.setOpenSide("buy");
            return "LONG";
        } else if (SHORT.equals(signalType)) {
            calcDto.setOpenSide("sell");
            return "SHORT";
        } else if (CLOSE_LONG.equals(signalType)) {
            calcDto.setOpenSide("buy");
            return "CLOSE_LONG";
        } else if (CLOSE_SHORT.equals(signalType)) {
            calcDto.setOpenSide("sell");
            return "CLOSE_SHORT";
        }
        return null;
    }


    // ==================== 规则引擎 ====================
    /**
     * 评估权重规则引擎，填充完整 SMC 上下文数据。
     * <p>支持 §5 全部 29 条默认规则的指标引用。</p>
     */
    protected RuleEvaluationResult evaluateWeightRuleEngine(IndicatorCalcDto calcDto, String symbol, double currentPrice, long signalTime,
                                                        SmcBarResult smc15, SmcBarResult smc1h, WeightRuleConfig weightRules) {
        RuleEvaluationResult result = null;
        CompositeState trendState = null;

        WeightRuleEngine engine = new WeightRuleEngine();
        if (indicatorProviderRegistry != null) {
            engine.setIndicatorProviderRegistry(indicatorProviderRegistry);
        }
        engine.setCandlestickService(candlestickService);

        WeightRuleContext ctx = new WeightRuleContext();
        ctx.setSymbol(symbol);
        ctx.setBuy(OrderSideEnum.BUY.getCode().equals(calcDto.getOpenSide()));
        ctx.setCurrentPrice(currentPrice);
        ctx.setMarketTrend(calcDto.getMarketTrend());

        List<Candlestick> kLines = calcDto.getKLines();
        ctx.setKLines(kLines.stream().map(k -> {
            WeightRuleContext.CandlestickSnapshot snap = new WeightRuleContext.CandlestickSnapshot();
            snap.setOpen(k.getOpenPrice().doubleValue());
            snap.setHigh(k.getHighPrice().doubleValue());
            snap.setLow(k.getLowPrice().doubleValue());
            snap.setClose(k.getClosePrice().doubleValue());
            snap.setId(k.getId());
            snap.setExchange(k.getExchange());
            return snap;
        }).collect(Collectors.toList()));

        // 初始化所有 SMC 字段默认值（避免 indicator resolver 空指针）
        ctx.setSmcInternalBosAligned(0);
        ctx.setSmcChaosException(0);
        ctx.setSmcRangePercent20h(0);
        ctx.setSmcFlipCount(0);
        ctx.setSmc1hPositionRatio(0.5);
        ctx.setSmc4hPositionRatio(0.5);
        ctx.setSmc4hWave(0);
        ctx.setSmc1hWave(0);
        ctx.setSmc4hAge(0);
        ctx.setSmc1hAge(0);
        ctx.setSmcHlHealth(0);
        ctx.setSmcLhHealth(0);
        ctx.setSmcRiskRewardRatio(0);
        ctx.setSmcPositionMarginPercent(0);
        ctx.setSmcDirectionAligned(1);

        boolean isBuy = ctx.isBuy();

        // 当 smc15/smc1h 可用且规则中引用了 SMC 指标时，计算完整 SMC 上下文
        boolean hasSmcRules = isSmcDataNeeded(weightRules.getRules());
        if (smc15 != null && smc1h != null && hasSmcRules) {
            // ---- 1. 多周期 SmcBarResult ----
            SmcBarResult smc4h = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKX4HOUR, signalTime);
            SmcBarResult smc1d = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKX1D, signalTime);

            // ---- 2. 单周期 Raw Result ----
            SmartMoneyConceptsIndicator.Result result4h = computeSmcResult(symbol, CandlestickIntervalEnum.OKX4HOUR, signalTime);
            SmartMoneyConceptsIndicator.Result result1h = computeSmcResult(symbol, CandlestickIntervalEnum.OKXMIN60, signalTime);
            SmartMoneyConceptsIndicator.Result result15m = computeSmcResult(symbol, CandlestickIntervalEnum.OKXMIN15, signalTime);

            Map<CandlestickIntervalEnum, SmartMoneyConceptsIndicator.Result> resultMap = new HashMap<>();
            resultMap.put(CandlestickIntervalEnum.OKX4HOUR, result4h);
            resultMap.put(CandlestickIntervalEnum.OKXMIN60, result1h);
            resultMap.put(CandlestickIntervalEnum.OKXMIN15, result15m);

            // ---- 2a. 历史结果列表（用于波次/翻转/年龄计算）----
            List<SmartMoneyConceptsIndicator.Result> hist1h = smcIndicatorService.getSmcResultHistory(
                    symbol, CandlestickIntervalEnum.OKXMIN60, signalTime, 120);
            List<SmartMoneyConceptsIndicator.Result> hist4h = Collections.emptyList();
            if (result4h != null) {
                hist4h = smcIndicatorService.getSmcResultHistory(
                        symbol, CandlestickIntervalEnum.OKX4HOUR, signalTime, 80);
            }

            // ---- 3. 趋势状态 ----
            trendState = SmcTrendUtils.getDetailedTrendState(resultMap, currentPrice, false, false);
            ctx.setTrendState(trendState);

            // ---- 4. 供给/需求区 ----
            List<SmcBarResult> zoneList = Stream.of(smc15, smc1h, smc4h, smc1d).filter(Objects::nonNull).collect(Collectors.toList());
            ctx.setSmcInSupplyZone(isPriceInBiasZoneMulti(zoneList, currentPrice, -1) ? 1 : 0);
            ctx.setSmcInDemandZone(isPriceInBiasZoneMulti(zoneList, currentPrice, 1) ? 1 : 0);

            // ---- 5. 15M 内部 BOS 方向一致 ----
            if ((isBuy && smc15.isInternalBullishBOS()) || (!isBuy && smc15.isInternalBearishBOS())) {
                ctx.setSmcInternalBosAligned(1);
            }

            // ---- 6. 位置比率 ----
            if (result1h != null) {
                ctx.setSmc1hPositionRatio(PositionRatioCalculator.calculate(result1h, isBuy, currentPrice));
            }
            if (result4h != null) {
                ctx.setSmc4hPositionRatio(PositionRatioCalculator.calculate(result4h, isBuy, currentPrice));
            }

            // ---- 7. 波次 / 翻转计数 / 结构年龄 ----
            if (!hist1h.isEmpty()) {
                int last1h = hist1h.size() - 1;
                ctx.setSmc1hWave(WaveIndexCalculator.calculate(hist1h, last1h, isBuy));
                ctx.setSmcFlipCount(WaveIndexCalculator.calculateFlipCount(hist1h, last1h, 20));
                ctx.setSmc1hAge(WaveIndexCalculator.calculateStructureAge(hist1h, last1h));
            }
            if (!hist4h.isEmpty()) {
                int last4h = hist4h.size() - 1;
                ctx.setSmc4hWave(WaveIndexCalculator.calculate(hist4h, last4h, isBuy));
                ctx.setSmc4hAge(WaveIndexCalculator.calculateStructureAge(hist4h, last4h));
            }

            // ---- 8. HL / LH 健康度 ----
            ctx.setSmcHlHealth(computeHlHealth(result1h, result4h, currentPrice));
            ctx.setSmcLhHealth(computeLhHealth(result1h, result4h, currentPrice));

            // ---- 9. 方向一致度（1H vs 4H 趋势方向比较） ----
            ctx.setSmcDirectionAligned(computeDirectionAligned(smc15, smc1h, smc4h, isBuy) ? 1 : 0);

            // ---- 9a. 信号共振（5周期 swingTrend 与信号方向一致性，与 extraParams.smcDashboard.alignment 一致）----
            Map<String, SmartMoneyConceptsIndicator.Result> alignmentMap = new LinkedHashMap<>();
            alignmentMap.put("3M", computeSmcResult(symbol, CandlestickIntervalEnum.OKXMIN3, signalTime));
            alignmentMap.put("15M", result15m);
            alignmentMap.put("1H", result1h);
            alignmentMap.put("4H", result4h);
            alignmentMap.put("1D", computeSmcResult(symbol, CandlestickIntervalEnum.OKX1D, signalTime));
            ctx.setSmcAlignment(computeAlignment(alignmentMap, isBuy ? "LONG" : "SHORT"));

            // ---- 10. 20小时振幅百分比 ----
            ctx.setSmcRangePercent20h(computeRangePercent20h(symbol, signalTime));

            // ---- 11. 混沌特例真实计算 ----
            ctx.setSmcChaosException(computeChaosException(result1h, currentPrice, isBuy, ctx.getSmc4hWave(),
                    ctx.getSmcRiskRewardRatio(),
                    ctx.getSmcRiskPercent() != null ? ctx.getSmcRiskPercent() : 0.0,
                    ctx.getSmcFlipCount()) ? 1 : 0);

            log.debug("SMC上下文填充完成: symbol={}, trend={}, flipCount={}, wave4h={}, wave1h={}, " +
                            "range20h={}, posRatio1h={}, posRatio4h={}, hlHealth={}, lhHealth={}, " +
                            "dirAligned={}, chaos={}",
                    symbol, trendState, ctx.getSmcFlipCount(), ctx.getSmc4hWave(), ctx.getSmc1hWave(),
                    ctx.getSmcRangePercent20h(), ctx.getSmc1hPositionRatio(), ctx.getSmc4hPositionRatio(),
                    ctx.getSmcHlHealth(), ctx.getSmcLhHealth(), ctx.getSmcDirectionAligned(), ctx.getSmcChaosException());
        }
        enrichFeatureContext(ctx, calcDto);
        enrichWeightRuleContext(ctx, calcDto);
        result = engine.evaluateWithTrace(weightRules, ctx);
        result.setTrendState(trendState);
        return result;
    }

    protected void enrichWeightRuleContext(WeightRuleContext ctx, IndicatorCalcDto calcDto) {
        // 供子类扩展
    }

    /**
     * 将 L2 信号特征（SignalFeatureProvider）注入规则引擎上下文。
     * <p>
     * 关联键与 Phase 4 配对写入一致：strategyName = calcDto.getRobotName()，timeframe 由 resolveTimeframe 解析。
     */
    private void enrichFeatureContext(WeightRuleContext ctx, IndicatorCalcDto calcDto) {
        String strategyName = calcDto.getRobotName();
        String timeframe = resolveTimeframe(calcDto);

        if (strategyName != null && timeframe != null) {
            FeatureStatistics stats = featureProvider.getFullStatistics(strategyName, calcDto.getSymbol(), timeframe);
            if (stats != null) {
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
            }
        }

        // 当前信号方向（LONG/SHORT）
        ctx.setCurrentDirection(calcDto.getSignalType() != null ? calcDto.getSignalType().name() : null);

        // 时间上下文：回测注入当前回测时间，实盘回退系统时间
        Long backtestTime = isBacktest() && backtestContextHolder != null ? backtestContextHolder.getCurrentTime() : null;
        ctx.setCurrentTimeMs(backtestTime != null ? backtestTime : System.currentTimeMillis());
    }

    /**
     * 解析信号周期（与 SignalAlternateLogService 同源），优先取 calcDto 上的周期枚举。
     */
    private String resolveTimeframe(IndicatorCalcDto calcDto) {
        CandlestickIntervalEnum interval = calcDto.getCandlestickIntervalEnum();
        if (interval == null) {
            List<Candlestick> kLines = calcDto.getKLines();
            if (kLines != null && !kLines.isEmpty()) {
                interval = kLines.get(kLines.size() - 1).getCandlestickIntervalEnum();
            }
        }
        return interval != null ? interval.name() : null;
    }

    private boolean isBacktest() {
        return "backtest".equals(quantEnv);
    }

    // ==================== 辅助方法 ====================
    // ==================== SMC 数据计算（核心） ====================
    protected SmcBarResult computeSmcSnapshot(String symbol, CandlestickIntervalEnum intervalEnum, long signalTimeMs) {
        return smcIndicatorService.getSmcSnapshot(symbol, intervalEnum, signalTimeMs);
    }

    protected SmartMoneyConceptsIndicator.Result computeSmcResult(String symbol, CandlestickIntervalEnum intervalEnum, long signalTimeMs) {
        return smcIndicatorService.getSmcResult(symbol, intervalEnum, signalTimeMs);
    }

    // ==================== 辅助布尔判断 ====================
    private boolean isPriceInsideOB(SmcBarResult smc, double price, boolean isBuy) {
        if (smc == null) return false;
        int requiredBias = isBuy ? 1 : -1;
        if (smc.getInternalOrderBlocks() != null) {
            for (SmcOrderBlock ob : smc.getInternalOrderBlocks()) {
                if (ob.getBias() == requiredBias && price >= ob.getLow() && price <= ob.getHigh()) return true;
            }
        }
        if (smc.getSwingOrderBlocks() != null) {
            for (SmcOrderBlock ob : smc.getSwingOrderBlocks()) {
                if (ob.getBias() == requiredBias && price >= ob.getLow() && price <= ob.getHigh()) return true;
            }
        }
        return false;
    }

    private boolean isPriceInBiasZone(SmcBarResult smc, double price, int bias) {
        if (smc == null) return false;
        if (smc.getInternalOrderBlocks() != null) {
            for (SmcOrderBlock ob : smc.getInternalOrderBlocks()) {
                if (ob.getBias() == bias && price >= ob.getLow() && price <= ob.getHigh()) return true;
            }
        }
        if (smc.getSwingOrderBlocks() != null) {
            for (SmcOrderBlock ob : smc.getSwingOrderBlocks()) {
                if (ob.getBias() == bias && price >= ob.getLow() && price <= ob.getHigh()) return true;
            }
        }
        return false;
    }

    // 检查规则中是否引用了任何 SMC 指标（触发完整上下文填充）
    private static final Set<String> SMC_INDICATOR_IDS = Set.of(
            "SMC_POSITION_SCORE", "SMC_NET_RR", "SMC_POSITION_SCORE_15M",
            "SMC_IN_SUPPLY_ZONE", "SMC_IN_DEMAND_ZONE", "SMC_RISK_PERCENT",
            "SMC_MARKET_TREND", "SMC_INTERNAL_BOS_ALIGNED", "SMC_CHAOS_EXCEPTION",
            "SMC_RANGE_PERCENT_20H", "SMC_FLIP_COUNT",
            "SMC_1H_POSITION_RATIO", "SMC_4H_POSITION_RATIO",
            "SMC_4H_WAVE", "SMC_1H_WAVE", "SMC_4H_AGE", "SMC_1H_AGE",
            "SMC_HL_HEALTH", "SMC_LH_HEALTH", "SMC_RISK_REWARD_RATIO",
            "SMC_POSITION_MARGIN_PERCENT", "SMC_DIRECTION_ALIGNED");

    private boolean isSmcDataNeeded(List<WeightRule> rules) {
        return rules.stream()
                .filter(WeightRule::isEnabled)
                .flatMap(rule -> rule.getConditions().stream())
                .anyMatch(cond -> SMC_INDICATOR_IDS.contains(cond.getIndicator()));
    }

    // 多周期供给/需求区检查：任一周期命中即返回 true
    private boolean isPriceInBiasZoneMulti(List<SmcBarResult> smcList, double price, int bias) {
        return smcList.stream().anyMatch(smc -> isPriceInBiasZone(smc, price, bias));
    }

    // ==================== SMC 字段计算辅助 ====================

    /** 计算 HL 健康度（多头结构）：1=健康, 0=未知, -1=危险, -2=损坏 */
    private int computeHlHealth(SmartMoneyConceptsIndicator.Result result1h,
                                 SmartMoneyConceptsIndicator.Result result4h, double currentPrice) {
        SmartMoneyConceptsIndicator.Result primary = result1h != null ? result1h : result4h;
        if (primary == null) return 0;
        Double hl = primary.getLastHigherLow();
        Double swingLow = primary.getLastSwingLow();
        if (hl == null || hl.isNaN()) return 0;
        if (swingLow == null || swingLow.isNaN()) return 0;
        if (currentPrice < swingLow) return -2;
        if (currentPrice < hl) return -1;
        return 1;
    }

    /** 计算 LH 健康度（空头结构）：1=健康, 0=未知, -1=危险, -2=损坏 */
    private int computeLhHealth(SmartMoneyConceptsIndicator.Result result1h,
                                 SmartMoneyConceptsIndicator.Result result4h, double currentPrice) {
        SmartMoneyConceptsIndicator.Result primary = result1h != null ? result1h : result4h;
        if (primary == null) return 0;
        Double lh = primary.getLastLowerHigh();
        Double swingHigh = primary.getLastSwingHigh();
        if (lh == null || lh.isNaN()) return 0;
        if (swingHigh == null || swingHigh.isNaN()) return 0;
        if (currentPrice > swingHigh) return -2;
        if (currentPrice > lh) return -1;
        return 1;
    }

    /** 计算多周期方向一致性 */
    private boolean computeDirectionAligned(SmcBarResult smc15, SmcBarResult smc1h, SmcBarResult smc4h, boolean isBuy) {
        int alignmentScore = 0;
        int total = 0;
        if (smc15 != null) {
            alignmentScore += smc15.getSwingTrend() > 0 ? 1 : (smc15.getSwingTrend() < 0 ? -1 : 0);
            total++;
        }
        if (smc1h != null) {
            alignmentScore += smc1h.getSwingTrend() > 0 ? 1 : (smc1h.getSwingTrend() < 0 ? -1 : 0);
            total++;
        }
        if (smc4h != null) {
            alignmentScore += smc4h.getSwingTrend() > 0 ? 1 : (smc4h.getSwingTrend() < 0 ? -1 : 0);
            total++;
        }
        if (total == 0) return true;
        // 信号方向 vs 多数周期趋势方向一致
        double avg = (double) alignmentScore / total;
        return isBuy ? avg > 0 : avg < 0;
    }

    /** 计算 20 小时振幅百分比（1H K线：取最近 20 条的高低最大幅度） */
    private double computeRangePercent20h(String symbol, long signalTimeMs) {
        try {
            KlineParam kp = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(CandlestickIntervalEnum.OKXMIN60)
                    .endTime(signalTimeMs)
                    .size(25)
                    .build();
            List<Candlestick> klines = candlestickService.listByLtId(kp);
            if (klines == null || klines.size() < 3) return 0;
            // 最多取最新 20 根
            int limit = Math.min(klines.size(), 20);
            double maxHigh = klines.get(0).getHighPrice().doubleValue();
            double minLow = klines.get(0).getLowPrice().doubleValue();
            for (int i = 1; i < limit; i++) {
                maxHigh = Math.max(maxHigh, klines.get(i).getHighPrice().doubleValue());
                minLow = Math.min(minLow, klines.get(i).getLowPrice().doubleValue());
            }
            if (minLow <= 0) return 0;
            return (maxHigh - minLow) / minLow * 100;
        } catch (Exception e) {
            log.warn("computeRangePercent20h 异常: symbol={}", symbol, e);
            return 0;
        }
    }

    /** 计算混沌特例是否触发 */
    private boolean computeChaosException(SmartMoneyConceptsIndicator.Result result,
                                           double currentPrice, boolean isBuy,
                                           int waveIndex, double riskRewardRatio,
                                           double riskPercent, int flipCount) {
        if (result == null) return false;
        ChaosExceptionResult evalResult = ChaosExceptionEvaluator.evaluate(
                waveIndex, riskRewardRatio, riskPercent, flipCount);
        return evalResult != null && evalResult.isTriggered();
    }

    private int getTrendPolarity(TrendType trendType) {
        switch (trendType) {
            case STRONG_BULLISH:
            case BULLISH_PULLBACK:
            case POTENTIAL_BOTTOM:
                return 1;
            case STRONG_BEARISH:
            case BEARISH_PULLBACK:
            case POTENTIAL_TOP:
                return -1;
            default:
                return 0;
        }
    }

    private double getTrendStrength(TrendType trendType) {
        switch (trendType) {
            case POTENTIAL_BOTTOM:
            case POTENTIAL_TOP:
                return 2;
            case STRONG_BULLISH:
            case STRONG_BEARISH:
                return 1.5;
            case BULLISH_PULLBACK:
            case BEARISH_PULLBACK:
                return 0.5;
            default:
                return 0;
        }
    }


    private static PriceTargetsInfo convertCritLevelToPriceTargetsInfo(List<CriticalLevel> criticalLevels) {
        if (criticalLevels == null || criticalLevels.isEmpty()) {
            return null;
        }
        PriceTargetsInfo info = new PriceTargetsInfo();
        List<PriceTarget> priceTargets = new ArrayList<>();
        List<StopLossLevel> stopLossLevels = new ArrayList<>();
        for (CriticalLevel cl : criticalLevels) {
            if (cl.getPrice() == null) continue;
            String action = cl.getAction();
            if (action == null) continue;
            if (action.contains("止损")) {
                StopLossLevel.StopLossLevelBuilder builder = StopLossLevel.builder()
                        .level(cl.getPriority() != null ? cl.getPriority() : 1)
                        .price(cl.getPrice())
                        .type(cl.getType())
                        .basedOn(cl.getPeriod());
                if (cl.getPriority() != null && cl.getPriority() == 1) {
                    info.setOptimalStopLoss(cl.getPrice());
                    builder.isPrimary(true);
                }
                stopLossLevels.add(builder.build());
            } else if (action.contains("止盈")) {
                PriceTarget.PriceTargetBuilder builder = PriceTarget.builder()
                        .level(cl.getPriority() != null ? cl.getPriority() : 1)
                        .price(cl.getPrice())
                        .basedOn(cl.getPeriod());
                if (cl.getPriority() != null && cl.getPriority() == 1) {
                    info.setOptimalTakeProfit(cl.getPrice());
                }
                priceTargets.add(builder.build());
            }
        }
        info.setPriceTargets(priceTargets);
        info.setStopLossLevels(stopLossLevels);
        return info;
    }

    private static String mergeExtraParams(String existingExtraParams, Map<String, SmcBarResult> barResults, List<CriticalLevel> criticalLevels, PriceTargetsInfo priceTargetsInfo) {
        JSONObject root;
        if (existingExtraParams != null && !existingExtraParams.isBlank()) {
            try {
                root = JSONUtil.parseObj(existingExtraParams);
            } catch (Exception ignored) {
                root = JSONUtil.createObj();
            }
        } else {
            root = JSONUtil.createObj();
        }

        JSONObject smc = JSONUtil.createObj();
        if (barResults != null) {
            for (Map.Entry<String, SmcBarResult> entry : barResults.entrySet()) {
                smc.set(entry.getKey().toLowerCase(), entry.getValue() != null ? toSafeJsonObj(entry.getValue()) : null);
            }
        }
        root.set("smc", smc);
        if (criticalLevels != null && !criticalLevels.isEmpty()) {
            root.set("criticalLevels", JSONUtil.parseArray(criticalLevels));
        }
        if (priceTargetsInfo != null) {
            root.set("priceTargetsInfo", JSONUtil.parseObj(priceTargetsInfo));
        }
        return root.toString();
    }

    private static JSONObject toSafeJsonObj(Object bean) {
        if (bean == null) return null;
        Map<String, Object> map = BeanUtil.beanToMap(bean, new LinkedHashMap<>(),
                CopyOptions.create().setIgnoreNullValue(false).setIgnoreError(true));
        sanitizeNonFiniteNumbers(map);
        return JSONUtil.parseObj(map);
    }

    /**
     * 将集合中的每个 Bean 转为 Map 并清理非有限数值后序列化为 JSONArray，避免 NaN/Infinity 序列化报错
     */
    private static JSONArray toSafeJsonArray(Collection<?> beans) {
        List<Object> safeList = new ArrayList<>();
        if (beans != null) {
            for (Object bean : beans) {
                if (bean == null) {
                    safeList.add(null);
                    continue;
                }
                Map<String, Object> map = BeanUtil.beanToMap(bean, new LinkedHashMap<>(),
                        CopyOptions.create().setIgnoreNullValue(false).setIgnoreError(true));
                sanitizeNonFiniteNumbers(map);
                safeList.add(map);
            }
        }
        return JSONUtil.parseArray(safeList);
    }

    private static void sanitizeNonFiniteNumbers(Object value) {
        if (value == null) return;
        if (value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : new ArrayList<>(m.entrySet())) {
                Object k = e.getKey();
                Object v = e.getValue();
                Object sanitized = sanitizeValue(v);
                ((Map<Object, Object>) m).put(k, sanitized);
                sanitizeNonFiniteNumbers(sanitized);
            }
        } else if (value instanceof Collection<?> c) {
            int idx = 0;
            for (Object v : new ArrayList<>(c)) {
                Object sanitized = sanitizeValue(v);
                if (c instanceof List<?> list) ((List<Object>) list).set(idx, sanitized);
                sanitizeNonFiniteNumbers(sanitized);
                idx++;
            }
        }
    }
    private static Object sanitizeValue(Object v) {
        if (v instanceof Double d) return Double.isFinite(d) ? d : null;
        if (v instanceof Float f) return Float.isFinite(f) ? f : null;
        return v;
    }
    // ==================== 配置钩子（供子类覆盖） ====================
    private void applyConfiguredParams() {
        // 子类可覆盖
    }

    private void applyOverrideParams(Map<String, String> overrides) {
        // 子类可覆盖
    }

}