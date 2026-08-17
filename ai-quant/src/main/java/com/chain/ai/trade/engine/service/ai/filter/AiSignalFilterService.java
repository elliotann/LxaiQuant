package com.chain.ai.trade.engine.service.ai.filter;

import com.chain.ai.trade.agent.service.AgentService;
import com.chain.ai.trade.engine.service.ai.filter.AiFilterConfigLoader.AiFilterConfig;
import com.chain.ai.trade.engine.service.ai.filter.ConsensusCalibrator.CalibratedResult;
import com.chain.ai.trade.engine.service.ai.filter.ObjectiveScorer.ScoreInput;
import com.chain.ai.trade.engine.service.ai.filter.ObjectiveScorer.ScoreResult;
import com.chain.ai.trade.engine.service.ai.filter.MarketDataCollector.MarketData;
import com.chain.ai.trade.engine.service.ai.filter.LlmAnalyzerService.LlmResult;
import com.chain.ai.trade.engine.service.ai.filter.dto.AiFilterRequest;
import com.chain.ai.trade.engine.service.ai.filter.dto.AiFilterResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSignalFilterService {

    private static final String SKILL_NAME = "ai_filter_signal_v1";

    private final AiFilterConfigLoader configLoader;
    private final MarketDataCollector marketDataCollector;
    private final ObjectiveScorer objectiveScorer;
    private final AgentService agentService;
    private final ConsensusCalibrator consensusCalibrator;
    private final ObjectMapper objectMapper;

    public AiFilterResult filter(AiFilterRequest request) {
        return filter(request, null);
    }

    public AiFilterResult filter(AiFilterRequest request, Consumer<String> onToken) {
        AiFilterConfig config = configLoader.load(request.getStrategy());
        String symbol = request.getSymbol();
        String direction = request.getDirection();
        BigDecimal signalStrength = request.getSignalStrength();

        if (!config.isEnabled()) {
            return AiFilterResult.notEnabled(signalStrength);
        }

        MarketData marketData = marketDataCollector.collectPromptData(symbol, direction, signalStrength);
        marketData.setSignalTime(request.getSignalTime() != null ? request.getSignalTime().toString() : "");

        ScoreInput scoreInput = marketDataCollector.collectScoreInput(symbol, direction);
        ScoreResult objectiveScore = objectiveScorer.score(scoreInput, config);

        LlmResult llmResult = null;
        if (objectiveScore.getDecision() == ObjectiveScorer.DIRECT_NEUTRAL) {
            try {
                llmResult = analyzeStreaming(marketData, onToken).get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("流式 LLM 分析异常", e);
                llmResult = new LlmResult("REJECT", "LOW", 0,
                        List.of("LLM 调用失败: " + e.getMessage()), List.of(),
                        BigDecimal.ZERO, "LLM 异常，默认拒绝");
            }
        }

        CalibratedResult calibrated = consensusCalibrator.calibrate(objectiveScore, llmResult, signalStrength);

        String aiFilterResultJson = buildAiFilterResultJson(calibrated, llmResult);

        log.info("AI 过滤结果: symbol={}, direction={}, originalStrength={}, decision={}, adjustedStrength={}",
            symbol, direction, signalStrength, calibrated.getFinalDecision(), calibrated.getAdjustedStrength());

        return new AiFilterResult(calibrated.getFinalDecision(), calibrated.getAdjustedStrength(),
            calibrated.getObjectiveScore(), calibrated.getLlmDecision(), aiFilterResultJson);
    }

    private CompletableFuture<LlmResult> analyzeStreaming(MarketData marketData, Consumer<String> onToken) {
        String userMessage = buildUserMessage(marketData);

        TokenStream tokenStream = agentService.chatStream(SKILL_NAME, userMessage, null);

        CompletableFuture<LlmResult> future = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();

        tokenStream
                .onPartialResponse(chunk -> {
                    sb.append(chunk);
                    if (onToken != null) {
                        onToken.accept(chunk);
                    }
                })
                .onCompleteResponse(response -> {
                    String fullText = sb.toString().trim();
                    try {
                        future.complete(parseResult(fullText));
                    } catch (Exception e) {
                        log.warn("Failed to parse streamed LLM result, raw={}", fullText, e);
                        future.complete(defaultReject("Parse error: " + e.getMessage()));
                    }
                })
                .onError(error -> {
                    log.error("LLM streaming error", error);
                    future.complete(defaultReject("Stream error: " + error.getMessage()));
                })
                .start();

        return future;
    }

    private static String buildUserMessage(MarketData data) {
        return """
                请审核以下技术信号：

                交易对：%s
                信号方向：%s
                信号原始仓位乘数：%s
                信号时间：%s

                多周期技术状态：
                - 周线趋势：%s
                - 4h 趋势：%s
                - 4h RSI：%s
                - 4h 布林带位置：%s
                - 1h 趋势：%s
                - 1h RSI：%s
                - 1h MACD 状态：%s

                最新价格信息：
                - 最新价：%s
                - 15m RSI：%s
                - 15m ATR：%s

                成交量分析：
                - 20 周期均量：%s
                - 当前量：%s
                - 量比：%s

                关键支撑阻力位：
                - 上方阻力：%s
                - 下方支撑：%s
                - 距阻力位距离：%s
                - 距支撑位距离：%s

                近期 K 线（最近 20 根）：
                %s

                请根据上述信息，按 SYSTEM 中的评审维度输出审核结果。
                """.formatted(
                safe(data.getSymbol()),
                safe(data.getSignalDirection()),
                safe(data.getSignalStrength() != null ? data.getSignalStrength().toString() : ""),
                safe(data.getSignalTime()),
                safe(data.getWeeklyTrend()),
                safe(data.getTrend4h()),
                safe(data.getRsi4h()),
                safe(data.getBbPosition4h()),
                safe(data.getTrend1h()),
                safe(data.getRsi1h()),
                safe(data.getMacdStatus1h()),
                safe(data.getLatestPrice()),
                safe(data.getRsi15m()),
                safe(data.getAtr15m()),
                safe(data.getAvgVolume20()),
                safe(data.getCurrentVolume()),
                safe(data.getVolumeRatio()),
                safe(data.getResistanceLevels()),
                safe(data.getSupportLevels()),
                safe(data.getDistanceToResistance()),
                safe(data.getDistanceToSupport()),
                safe(data.getRecentCandles())
        );
    }

    private LlmResult parseResult(String llmResponse) {
        try {
            String jsonStr = extractJson(llmResponse);
            if (jsonStr == null) {
                log.warn("No JSON found in LLM response, raw={}", llmResponse);
                return defaultReject("No JSON in response");
            }
            JsonNode json = objectMapper.readTree(jsonStr);
            String decision = json.path("decision").asText("REJECT");
            String confidence = json.path("confidence").asText("LOW");
            int score = json.path("score").asInt(0);
            String summary = json.path("summary").asText("");
            BigDecimal suggestedStrength = new BigDecimal(json.path("suggestedStrength").asDouble(0.0));

            List<String> reasons = new ArrayList<>();
            JsonNode reasonsNode = json.path("key_reasons");
            if (reasonsNode.isArray()) {
                for (JsonNode r : reasonsNode) reasons.add(r.asText());
            }

            List<String> risks = new ArrayList<>();
            JsonNode risksNode = json.path("risks");
            if (risksNode.isArray()) {
                for (JsonNode r : risksNode) risks.add(r.asText());
            }

            return new LlmResult(decision, confidence, score, reasons, risks, suggestedStrength, summary);
        } catch (Exception e) {
            log.warn("Failed to parse LLM result, raw={}", llmResponse, e);
            return new LlmResult("REJECT", "LOW", 0,
                    List.of("解析 LLM 响应失败"), List.of(),
                    BigDecimal.ZERO, "解析异常，默认拒绝");
        }
    }

    private static String extractJson(String text) {
        if (text == null) return null;
        int idx = text.indexOf("```json");
        if (idx >= 0) {
            int start = idx + 7;
            int end = text.indexOf("```", start);
            if (end > start) return text.substring(start, end).trim();
        }
        idx = text.indexOf('{');
        if (idx >= 0) {
            int depth = 0;
            for (int i = idx; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0) return text.substring(idx, i + 1);
            }
        }
        return null;
    }

    private static LlmResult defaultReject(String reason) {
        return new LlmResult("REJECT", "LOW", 0,
                List.of(reason), List.of(),
                BigDecimal.ZERO, "LLM 异常，默认拒绝");
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private String buildAiFilterResultJson(CalibratedResult calibrated, LlmResult llmResult) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("decision", calibrated.getFinalDecision());
            result.put("score", calibrated.getObjectiveScore());
            result.put("llmDecision", calibrated.getLlmDecision());
            result.put("summary", llmResult != null ? llmResult.getSummary() : "");
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"decision\":\"" + calibrated.getFinalDecision() + "\"}";
        }
    }

}
