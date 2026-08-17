package com.chain.ai.trade.engine.controller.signal;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.signal.entity.dos.SignalServiceConfig;
import com.chain.ai.trade.engine.signal.mapper.SignalServiceConfigMapper;
import com.chain.ai.trade.engine.signal.rule.WeightRuleConfig;
import com.chain.ai.trade.engine.signal.service.SignalServiceConfigService;
import com.chain.ai.trade.engine.signal.service.WeightRuleVersionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/signal-service")
@RequiredArgsConstructor
@Slf4j
public class SignalServiceController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SignalServiceConfigMapper signalServiceConfigMapper;
    private final SignalServiceConfigService signalServiceConfigService;
    private final WeightRuleVersionService weightRuleVersionService;
    private final ObjectMapper objectMapper;

    private static final List<SignalServiceDefinition> SERVICE_DEFINITIONS = List.of(
            new SignalServiceDefinition(
                    "BollingerRsiSignService",
                    "BollingerRsiSignService",
                    List.of(
                            param("rsiLength", "RSI周期", "number", 8, 1d, 200d, 1d, null, null, null),
                            param("bbLength", "布林周期", "number", 108, 1d, 300d, 1d, null, null, null),
                            param("bbMultiplier", "布林倍数", "number", 2.0, 0.1d, 10d, 0.1d, null, null, null),
                            param("rsiThreshold", "RSI阈值", "number", 50, 1d, 100d, 1d, null, null, null),
                            param("useAndeanFilter", "启用Andean过滤", "boolean", true, null, null, null, null, null, null),
                            param("andeanLength", "Andean周期", "number", 50, 1d, 200d, 1d, null, null, null),
                            param("andeanSignalLength", "Andean信号周期", "number", 9, 1d, 100d, 1d, null, null, null),
                            param("andeanEarlySignalMode", "Andean提前信号", "boolean", true, null, null, null, null, null, null),
                            param("useElliottWave", "启用艾略特波浪", "boolean", true, null, null, null, null, null, null),
                            param("elliottWaveMinBars", "艾略特最小K线数", "number", 200, 50d, 1000d, 10d, null, null, null),
                            param("useRiskModule", "启用风险模块", "boolean", true, null, null, null, null, null, null),
                            param("riskModuleEvaluators", "风险评估器列表", "text", "", null, null, null, null, null, null)
                    )
            ),
            new SignalServiceDefinition(
                    "FibonacciBandsSignService",
                    "FibonacciBandsSignService",
                    List.of(
                            param("length", "主周期", "number", 21, 1d, 200d, 1d, null, "core", "双EMA基础线周期，值越大轨道越平滑"),
                            param("atrLength", "ATR周期", "number", 14, 1d, 200d, 1d, null, "core", "波动率计算周期"),
                            param("tpAggressiveness", "止盈激进程度", "select", "low", null, null, null, List.of(
                                    option("低 (lower4/upper4)", "low"),
                                    option("中 (lower2/upper2)", "medium"),
                                    option("高 (lower1/upper1)", "high")
                            ), "core", "止盈轨道选择，低=最远轨道(保守)，高=最近轨道(激进)"),
                            param("useAtr", "使用ATR", "boolean", true, null, null, null, null, "core", "使用ATR计算波动率，关闭则使用标准差")
                    )
            ),
            new SignalServiceDefinition(
                    "RangeFilterDWSignService",
                    "RangeFilterDWSignService",
                    List.of(
                            param("filterType", "过滤类型", "select", "Type 1", null, null, null, List.of(
                                    option("Type 1", "Type 1"),
                                    option("Type 2", "Type 2"),
                                    option("Type 3", "Type 3")
                            ), "core", "Range Filter 核心算法类型，Type 1 为标准计算，Type 2/3 为变体"),
                            param("movementSource", "价格来源", "select", "Close", null, null, null, List.of(
                                    option("Close", "Close"),
                                    option("Open", "Open"),
                                    option("High", "High"),
                                    option("Low", "Low")
                            ), "core", "计算 Range Filter 使用的价格来源"),
                            param("rangeQuantity", "范围倍数", "number", 2.618, 1d, 10d, 0.1d, null, "core", "Range Filter 通道倍数，越大通道越宽"),
                            param("rangeScale", "范围尺度", "select", "Average Change", null, null, null, List.of(
                                    option("Average Change", "Average Change"),
                                    option("ATR", "ATR"),
                                    option("Standard Deviation", "Standard Deviation")
                            ), "core", "通道宽度的计算方式"),
                            param("rangePeriod", "范围周期", "number", 14, 1d, 200d, 1d, null, "core", "Range Filter 的计算周期"),
                            param("smoothRange", "平滑范围", "boolean", true, null, null, null, null, "core", "是否对 Range Filter 进行平滑处理"),
                            param("smoothPeriod", "平滑周期", "number", 27, 1d, 200d, 1d, null, "core", "平滑周期，值越大通道越平滑"),
                            param("averageFilterChanges", "平均变化", "boolean", true, null, null, null, null, "core", "是否对 filter 变化进行平均处理"),
                            param("averageSamples", "平均样本数", "number", 2, 1d, 10d, 1d, null, "core", "平均处理的样本数量"),
                            param("detailedLog", "详细日志", "boolean", false, null, null, null, null, "core", "开启会输出详细的信号计算日志，用于调试"),
                            param("useHTFMacdFilter1", "启用高周期MACD过滤1", "boolean", true, null, null, null, null, "macd1", "启用第一层高周期 MACD 方向过滤"),
                            param("htfMacdResolution1", "高周期1 (分钟)", "number", 3, 1d, 1440d, 1d, null, "macd1", "第一层 MACD 使用的时间周期（分钟），如 3=3分钟线"),
                            param("htfMacdFast1", "MACD快线1", "number", 12, 1d, 50d, 1d, null, "macd1", "第一层 MACD 快线周期"),
                            param("htfMacdSlow1", "MACD慢线1", "number", 26, 1d, 100d, 1d, null, "macd1", "第一层 MACD 慢线周期"),
                            param("htfMacdSignal1", "MACD信号1", "number", 9, 1d, 50d, 1d, null, "macd1", "第一层 MACD 信号线周期"),
                            param("htfMacdFilterMode1", "MACD过滤模式1", "select", "HISTOGRAM", null, null, null, List.of(
                                    option("方向", "MACD_DIRECTION"),
                                    option("信号线方向", "SIGNAL_DIRECTION"),
                                    option("柱体", "HISTOGRAM"),
                                    option("交叉", "MACD_CROSS")
                            ), "macd1", "第一层 MACD 过滤判断方式"),
                            param("useHTFMacdFilter2", "启用高周期MACD过滤2", "boolean", true, null, null, null, null, "macd2", "启用第二层高周期 MACD 方向过滤"),
                            param("htfMacdResolution2", "高周期2 (分钟)", "number", 15, 1d, 1440d, 1d, null, "macd2", "第二层 MACD 使用的时间周期（分钟），如 15=15分钟线"),
                            param("htfMacdFast2", "MACD快线2", "number", 12, 1d, 50d, 1d, null, "macd2", "第二层 MACD 快线周期"),
                            param("htfMacdSlow2", "MACD慢线2", "number", 26, 1d, 100d, 1d, null, "macd2", "第二层 MACD 慢线周期"),
                            param("htfMacdSignal2", "MACD信号2", "number", 9, 1d, 50d, 1d, null, "macd2", "第二层 MACD 信号线周期"),
                            param("htfMacdFilterMode2", "MACD过滤模式2", "select", "HISTOGRAM", null, null, null, List.of(
                                    option("方向", "MACD_DIRECTION"),
                                    option("信号线方向", "SIGNAL_DIRECTION"),
                                    option("柱体", "HISTOGRAM"),
                                    option("交叉", "MACD_CROSS")
                            ), "macd2", "第二层 MACD 过滤判断方式"),
                            param("useRiskModule", "启用风险模块", "boolean", true, null, null, null, null, "risk", "开启后使用风险评估器对信号进行额外过滤"),
                            param("riskModuleEvaluators", "风险评估器列表", "text", "", null, null, null, null, "risk", "风险评估器的 Bean 名称列表，逗号分隔"),
                            param("useDualSwingFilter", "启用双Swing横盘识别", "boolean", true, null, null, null, null, "swing", "通过双 Swing 高低点识别横盘区间，横盘时抑制信号"),
                            param("swingLookback", "Swing回溯K线数", "number", 3, 1d, 20d, 1d, null, "swing", "识别 Swing 高低点时的左右回溯K线数"),
                            param("swingAllowedEqual", "Swing允许相等值", "number", 0, 0d, 10d, 1d, null, "swing", "Swing 识别时允许两边相等的K线数量"),
                            param("swingRecentBars", "Swing分析范围K线", "number", 55, 10d, 500d, 5d, null, "swing", "双Swing分析的K线范围，仅分析最近的 N 根K线"),
                            param("swingRangeThreshold", "Swing横盘阈值", "number", 0.04, 0.001d, 0.5d, 0.001d, null, "swing", "横盘判定的价格变动阈值（比例），值越小越容易判定为横盘"),
                            param("allowBreakoutInRanging", "横盘允许突破信号", "boolean", true, null, null, null, null, "swing", "横盘判定后是否仍允许突破信号产生"),
                            param("breakoutConfirmationBars", "突破确认K线数", "number", 1, 1d, 10d, 1d, null, "swing", "横盘突破信号需要连续确认的K线数"),
                            param("rangeQuantityLow", "低波动范围倍数", "number", 1.618, 0.1d, 10d, 0.1d, null, "volatility", "低波动率时使用的 Range Filter 倍数"),
                            param("rangeQuantityHigh", "高波动范围倍数", "number", 2.618, 0.1d, 10d, 0.1d, null, "volatility", "高波动率时使用的 Range Filter 倍数"),
                            param("atrThreshold", "ATR波动阈值", "number", 0.8, 0.1d, 10d, 0.1d, null, "volatility", "区分高低波动率的 ATR 阈值，值越小越容易判定为高波动"),
                            param("atrPeriodForDynamic", "动态ATR周期", "number", 30, 1d, 200d, 1d, null, "volatility", "用于动态波动率判断的 ATR 计算周期"),
                            param("priceMoveFilterEnabled", "启用价格变动过滤", "boolean", false, null, null, null, null, "price", "开启后过滤价格变动幅度过小的信号，避免噪音"),
                            param("priceMoveThreshold", "价格变动阈值(%)", "number", 2.0, 0.1d, 100d, 0.1d, null, "price", "价格变动的最小百分比，低于此值不产生信号"),
                            param("enableRangeTrading", "启用横盘交易模式", "boolean", true, null, null, null, null, "rangeTrading", "横盘市场中在边界附近产生反向交易信号"),
                            param("rangeEntryDistance", "横盘入场距离", "number", 0.001, 0.0001d, 0.1d, 0.0001d, null, "rangeTrading", "价格触及横盘边界多远时产生入场信号"),
                            param("rangeUseFilters", "横盘使用EMA/MACD过滤", "boolean", false, null, null, null, null, "rangeTrading", "横盘交易模式下是否仍应用 EMA/MACD 过滤"),
                            param("rangeBoundaryType", "横盘边界类型", "select", "AVERAGE", null, null, null, List.of(
                                    option("平均值", "AVERAGE"),
                                    option("第二高低点", "SECOND_PRICE"),
                                    option("最近高低点", "RECENT")
                            ), "rangeTrading", "横盘边界计算方式"),
                            param("useSmcOrderBlockRange", "启用SMC订单块横盘", "boolean", true, null, null, null, null, "smc", "通过 SMC 订单块间隙判断横盘状态"),
                            param("smcRangeThresholdPercent", "SMC横盘间隙阈值(%)", "number", 2.0, 0.1d, 50d, 0.1d, null, "smc", "SMC 订单块间隙低于此百分比时判定为横盘"),
                            param("smcStopLossOffset", "SMC止损偏移", "number", 0.005, 0.0d, 0.05d, 0.001d, null, "smcWeight", "SMC 止损计算的偏移比例，越大止损越远"),
                            param("smcMinTargetSpaceRatio", "SMC目标最小空间", "number", 0.005, 0.0d, 0.05d, 0.001d, null, "smcWeight", "SMC 止盈与入场间的最小空间比例"),
                            param("maxRiskPercent", "最大风险比例(%)", "number", 2.0, 0.1d, 20.0d, 0.1d, null, "smcWeight", "单笔最大可接受风险占当前价格的百分比"),
                            param("minRR", "最小盈亏比", "number", 1.2, 0.5d, 10.0d, 0.1d, null, "smcWeight", "开仓所需的最小盈亏比，低于此值不开仓"),
                            param("useEmaScore", "启用EMA评分", "boolean", false, null, null, null, null, "smcWeight", "开启后 SMC 权重计算中加入多周期 EMA 评分")
                    )
            )
    );

    @GetMapping("/definitions")
    public ResponseEntity<ApiResponse<List<SignalServiceDefinition>>> getDefinitions() {
        return ResponseEntity.ok(ApiResponse.success(SERVICE_DEFINITIONS));
    }

    @GetMapping("/configs")
    public ResponseEntity<ApiResponse<List<SignalServiceConfigResponse>>> listConfigs() {
        List<SignalServiceConfig> configs = signalServiceConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SignalServiceConfig>()
                        .orderByDesc(SignalServiceConfig::getUpdatedAt)
        );
        List<SignalServiceConfigResponse> response = configs.stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(SignalServiceConfigResponse::getUpdatedAtTs, Comparator.nullsLast(Long::compareTo)).reversed())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/configs")
    public ResponseEntity<ApiResponse<SignalServiceConfigResponse>> createConfig(@RequestBody SignalServiceConfigRequest request) {
        try {
            SignalServiceConfig saved = saveConfig(request, null);
            return ResponseEntity.ok(ApiResponse.success(toResponse(saved)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("创建信号服务配置失败", ex);
            return ResponseEntity.ok(ApiResponse.error("创建失败: " + ex.getMessage()));
        }
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<ApiResponse<SignalServiceConfigResponse>> updateConfig(
            @PathVariable Long id,
            @RequestBody SignalServiceConfigRequest request) {
        try {
            SignalServiceConfig existing = signalServiceConfigMapper.selectById(id);
            if (existing == null) {
                return ResponseEntity.ok(ApiResponse.error("配置不存在"));
            }
            SignalServiceConfig saved = saveConfig(request, id);
            return ResponseEntity.ok(ApiResponse.success(toResponse(saved)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("更新信号服务配置失败", ex);
            return ResponseEntity.ok(ApiResponse.error("更新失败: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteConfig(@PathVariable Long id) {
        int deleted = signalServiceConfigMapper.deleteById(id);
        if (deleted == 0) {
            return ResponseEntity.ok(ApiResponse.error("配置不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/configs/{id}/weight-rules")
    public ResponseEntity<ApiResponse<WeightRuleConfig>> getWeightRules(@PathVariable Long id) {
        SignalServiceConfig config = signalServiceConfigMapper.selectById(id);
        if (config == null) {
            return ResponseEntity.ok(ApiResponse.error("配置不存在"));
        }
        WeightRuleConfig rules = signalServiceConfigService.getWeightRules(config.getServiceKey());
        return ResponseEntity.ok(ApiResponse.success(rules != null ? rules : new WeightRuleConfig()));
    }

    @PutMapping("/configs/{id}/weight-rules")
    public ResponseEntity<ApiResponse<Boolean>> updateWeightRules(
            @PathVariable Long id,
            @RequestBody WeightRuleConfig weightRules) {
        try {
            SignalServiceConfig config = signalServiceConfigMapper.selectById(id);
            if (config == null) {
                return ResponseEntity.ok(ApiResponse.error("配置不存在"));
            }
            config.setWeightRulesJson(objectMapper.writeValueAsString(weightRules));
            signalServiceConfigMapper.updateById(config);
            weightRuleVersionService.createSnapshot(id, weightRules, "更新权重规则", null);
            return ResponseEntity.ok(ApiResponse.success(true));
        } catch (Exception ex) {
            log.error("更新权重规则失败", ex);
            return ResponseEntity.ok(ApiResponse.error("更新失败: " + ex.getMessage()));
        }
    }

    private SignalServiceConfig saveConfig(SignalServiceConfigRequest request, Long id) {
        if (request == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        if (StringUtils.isBlank(request.getName())) {
            throw new IllegalArgumentException("配置名称不能为空");
        }
        if (StringUtils.isBlank(request.getServiceKey())) {
            throw new IllegalArgumentException("服务类不能为空");
        }
        SignalServiceConfig config = id != null ? signalServiceConfigMapper.selectById(id) : new SignalServiceConfig();
        if (config == null) {
            throw new IllegalArgumentException("配置不存在");
        }
        config.setName(request.getName().trim());
        config.setServiceKey(request.getServiceKey().trim());
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        Map<String, Object> params = request.getParams() != null ? request.getParams() : new HashMap<>();
        try {
            config.setParamsJson(objectMapper.writeValueAsString(params));
        } catch (Exception ex) {
            throw new IllegalArgumentException("参数序列化失败: " + ex.getMessage());
        }
        if (request.getWeightRules() != null) {
            try {
                config.setWeightRulesJson(objectMapper.writeValueAsString(request.getWeightRules()));
            } catch (Exception ex) {
                throw new IllegalArgumentException("权重规则序列化失败: " + ex.getMessage());
            }
        }
        Date now = new Date();
        boolean isUpdate = config.getId() != null;
        config.setUpdatedAt(now);
        if (!isUpdate) {
            config.setCreatedAt(now);
            signalServiceConfigMapper.insert(config);
        } else {
            signalServiceConfigMapper.updateById(config);
        }
        Long savedId = config.getId();
        if (isUpdate && request.getWeightRules() != null) {
            weightRuleVersionService.createSnapshot(savedId, request.getWeightRules(), "更新信号服务配置", null);
        }
        return signalServiceConfigMapper.selectById(savedId);
    }

    private SignalServiceConfigResponse toResponse(SignalServiceConfig config) {
        Map<String, Object> params = new HashMap<>();
        if (StringUtils.isNotBlank(config.getParamsJson())) {
            try {
                params = objectMapper.readValue(config.getParamsJson(), new TypeReference<>() {});
            } catch (Exception ex) {
                log.warn("解析信号服务参数失败: {}", ex.getMessage());
            }
        }
        WeightRuleConfig weightRules = null;
        if (StringUtils.isNotBlank(config.getWeightRulesJson())) {
            try {
                weightRules = objectMapper.readValue(config.getWeightRulesJson(), WeightRuleConfig.class);
            } catch (Exception ex) {
                log.warn("解析权重规则失败: {}", ex.getMessage());
            }
        }
        SignalServiceConfigResponse response = new SignalServiceConfigResponse();
        response.setId(config.getId());
        response.setName(config.getName());
        response.setServiceKey(config.getServiceKey());
        response.setEnabled(config.getEnabled());
        response.setParams(params);
        response.setWeightRules(weightRules);
        if (config.getUpdatedAt() != null) {
            long ts = config.getUpdatedAt().getTime();
            response.setUpdatedAtTs(ts);
            response.setUpdatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(TIME_FORMATTER));
        }
        return response;
    }

    private static SignalServiceParamDefinition param(
            String key,
            String label,
            String type,
            Object defaultValue,
            Double min,
            Double max,
            Double step,
            List<SignalServiceParamOption> options,
            String group,
            String description) {
        return new SignalServiceParamDefinition(key, label, type, defaultValue, min, max, step, options, group, description);
    }

    private static SignalServiceParamOption option(String label, Object value) {
        return new SignalServiceParamOption(label, value);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalServiceDefinition {
        private String key;
        private String label;
        private List<SignalServiceParamDefinition> parameters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalServiceParamDefinition {
        private String key;
        private String label;
        private String type;
        private Object defaultValue;
        private Double min;
        private Double max;
        private Double step;
        private List<SignalServiceParamOption> options;
        private String group;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalServiceParamOption {
        private String label;
        private Object value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalServiceConfigRequest {
        private String name;
        private String serviceKey;
        private Boolean enabled;
        private Map<String, Object> params;
        private WeightRuleConfig weightRules;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalServiceConfigResponse {
        private Long id;
        private String name;
        private String serviceKey;
        private Boolean enabled;
        private Map<String, Object> params;
        private WeightRuleConfig weightRules;
        private String updatedAt;
        private Long updatedAtTs;
    }
}
