package com.chain.ai.trade.engine.controller.signal;

import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.signal.entity.dos.WeightRuleVersion;
import com.chain.ai.trade.engine.signal.rule.IndicatorMetadata;
import com.chain.ai.trade.engine.signal.rule.IndicatorProviderRegistry;
import com.chain.ai.trade.engine.signal.rule.RuleEvaluationResult;
import com.chain.ai.trade.engine.signal.rule.WeightRuleConfig;
import com.chain.ai.trade.engine.signal.rule.WeightRuleContext;
import com.chain.ai.trade.engine.signal.rule.WeightRuleEngine;
import com.chain.ai.trade.engine.signal.service.WeightRuleVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/rule-engine")
@RequiredArgsConstructor
@Slf4j
public class RuleEngineController {

    private final IndicatorProviderRegistry indicatorProviderRegistry;
    private final WeightRuleVersionService weightRuleVersionService;
    private final ICandlestickService candlestickService;
    private final ObjectMapper objectMapper;

    @GetMapping("/indicators")
    public ResponseEntity<ApiResponse<List<IndicatorMetadata>>> getIndicators() {
        List<IndicatorMetadata> all = indicatorProviderRegistry.getAllMetadata();
        return ResponseEntity.ok(ApiResponse.success(all));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<RuleEvaluationResult>> test(@RequestBody RuleEngineTestRequest request) {
        try {
            WeightRuleEngine engine = new WeightRuleEngine();
            engine.setIndicatorProviderRegistry(indicatorProviderRegistry);
            engine.setCandlestickService(candlestickService);

            WeightRuleContext ctx = new WeightRuleContext();
            ctx.setBuy("BUY".equalsIgnoreCase(request.getDirection()));
            ctx.setSymbol(request.getSymbol());
            ctx.setCurrentPrice(request.getCurrentPrice() != null ? request.getCurrentPrice() : 0);
            ctx.setMarketTrend(request.getMarketTrend());
            if (request.getKLines() != null) {
                List<WeightRuleContext.CandlestickSnapshot> kLines = new ArrayList<>();
                for (TestCandlestick c : request.getKLines()) {
                    WeightRuleContext.CandlestickSnapshot snap = new WeightRuleContext.CandlestickSnapshot();
                    snap.setOpen(c.getOpen());
                    snap.setHigh(c.getHigh());
                    snap.setLow(c.getLow());
                    snap.setClose(c.getClose());
                    snap.setId(c.getId());
                    kLines.add(snap);
                }
                ctx.setKLines(kLines);
            }
            if (request.getContext() != null) {
                if (request.getContext().containsKey("emaFast")) ctx.setEmaFast(toDouble(request.getContext().get("emaFast")));
                if (request.getContext().containsKey("emaSlow")) ctx.setEmaSlow(toDouble(request.getContext().get("emaSlow")));
                if (request.getContext().containsKey("emaRatio")) ctx.setEmaRatio(toDouble(request.getContext().get("emaRatio")));
                if (request.getContext().containsKey("macdLine")) ctx.setMacdLine(toDouble(request.getContext().get("macdLine")));
                if (request.getContext().containsKey("macdSignal")) ctx.setMacdSignal(toDouble(request.getContext().get("macdSignal")));
                if (request.getContext().containsKey("macdHistogram")) ctx.setMacdHistogram(toDouble(request.getContext().get("macdHistogram")));
                if (request.getContext().containsKey("volumeRatio")) ctx.setVolumeRatio(toDouble(request.getContext().get("volumeRatio")));
                if (request.getContext().containsKey("volumeTrend")) ctx.setVolumeTrend((String) request.getContext().get("volumeTrend"));
                if (request.getContext().containsKey("pricePosition")) ctx.setPricePosition(toDouble(request.getContext().get("pricePosition")));
                if (request.getContext().containsKey("dayOfWeek")) ctx.setDayOfWeek(toInteger(request.getContext().get("dayOfWeek")));
                if (request.getContext().containsKey("hourOfDay")) ctx.setHourOfDay(toInteger(request.getContext().get("hourOfDay")));
                if (request.getContext().containsKey("detectedPatterns")) {
                    String val = request.getContext().get("detectedPatterns").toString();
                    String[] parts = val.split(",");
                    Set<String> patterns = new java.util.HashSet<>();
                    for (String p : parts) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) patterns.add(trimmed);
                    }
                    ctx.setDetectedPatterns(patterns);
                }
                if (request.getContext().containsKey("smcAlignment")) {
                    ctx.setSmcAlignment((String) request.getContext().get("smcAlignment"));
                }
            }

            RuleEvaluationResult result = engine.evaluateWithTrace(request.getWeightRules(), ctx);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("测试规则引擎失败", e);
            return ResponseEntity.ok(ApiResponse.error("测试失败: " + e.getMessage()));
        }
    }

    @GetMapping("/versions/{configId}")
    public ResponseEntity<ApiResponse<List<WeightRuleVersion>>> listVersions(@PathVariable Long configId) {
        List<WeightRuleVersion> versions = weightRuleVersionService.listVersions(configId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping("/versions/{configId}/restore/{version}")
    public ResponseEntity<ApiResponse<WeightRuleConfig>> restoreVersion(
            @PathVariable Long configId,
            @PathVariable Integer version) {
        try {
            WeightRuleConfig config = weightRuleVersionService.restoreVersion(configId, version);
            if (config == null) {
                return ResponseEntity.ok(ApiResponse.error("版本不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(config));
        } catch (Exception e) {
            log.error("恢复权重规则版本失败", e);
            return ResponseEntity.ok(ApiResponse.error("恢复失败: " + e.getMessage()));
        }
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return null; }
    }

    @Data
    public static class RuleEngineTestRequest {
        private String direction;
        private String symbol;
        private Double currentPrice;
        private String marketTrend;
        private List<TestCandlestick> kLines;
        private WeightRuleConfig weightRules;
        private Map<String, Object> context;
    }

    @Data
    public static class TestCandlestick {
        private double open;
        private double high;
        private double low;
        private double close;
        private long id;
    }
}
