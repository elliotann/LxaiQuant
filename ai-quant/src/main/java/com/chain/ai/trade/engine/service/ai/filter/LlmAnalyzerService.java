package com.chain.ai.trade.engine.service.ai.filter;

import com.chain.ai.trade.engine.service.LlmConfigService;
import com.chain.ai.trade.engine.service.prompt.MarkdownPromptTemplateService;
import com.chain.ai.trade.engine.service.prompt.MarkdownPromptTemplateService.LoadedTemplate;
import com.chain.ai.trade.engine.service.ai.filter.MarketDataCollector.MarketData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmAnalyzerService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final LlmConfigService llmConfigService;
    private final MarkdownPromptTemplateService promptTemplateService;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    public LlmResult analyze(MarketData marketData) {
        try {
            LoadedTemplate template = promptTemplateService.loadAiFilterSignalTemplate(environment);
            String prompt = buildPrompt(template, marketData);
            String llmResponse = callLlm(prompt);
            return parseResult(llmResponse);
        } catch (Exception e) {
            return new LlmResult("REJECT", "LOW", 0,
                List.of("LLM 调用失败: " + e.getMessage()), List.of(),
                BigDecimal.ZERO, "LLM 异常，默认拒绝");
        }
    }

    private String buildPrompt(LoadedTemplate template, MarketData data) {
        String systemPrompt = template.system;
        String userPrompt = template.user;

        userPrompt = userPrompt
            .replace("{{symbol}}", safe(data.getSymbol()))
            .replace("{{signalDirection}}", safe(data.getSignalDirection()))
            .replace("{{signalStrength}}", safe(data.getSignalStrength() != null ? data.getSignalStrength().toString() : ""))
            .replace("{{signalTime}}", safe(data.getSignalTime()))
            .replace("{{weeklyTrend}}", safe(data.getWeeklyTrend()))
            .replace("{{trend4h}}", safe(data.getTrend4h()))
            .replace("{{rsi4h}}", safe(data.getRsi4h()))
            .replace("{{bbPosition4h}}", safe(data.getBbPosition4h()))
            .replace("{{trend1h}}", safe(data.getTrend1h()))
            .replace("{{rsi1h}}", safe(data.getRsi1h()))
            .replace("{{macdStatus1h}}", safe(data.getMacdStatus1h()))
            .replace("{{latestPrice}}", safe(data.getLatestPrice()))
            .replace("{{rsi15m}}", safe(data.getRsi15m()))
            .replace("{{atr15m}}", safe(data.getAtr15m()))
            .replace("{{avgVolume20}}", safe(data.getAvgVolume20()))
            .replace("{{currentVolume}}", safe(data.getCurrentVolume()))
            .replace("{{volumeRatio}}", safe(data.getVolumeRatio()))
            .replace("{{resistanceLevels}}", safe(data.getResistanceLevels()))
            .replace("{{supportLevels}}", safe(data.getSupportLevels()))
            .replace("{{distanceToResistance}}", safe(data.getDistanceToResistance()))
            .replace("{{distanceToSupport}}", safe(data.getDistanceToSupport()))
            .replace("{{recentCandles}}", safe(data.getRecentCandles()));

        return systemPrompt + "\n\n" + userPrompt;
    }

    private String callLlm(String prompt) throws Exception {
        var active = llmConfigService.getActiveSelection();
        String provider = active != null && active.getProvider() != null ? active.getProvider() : "deepseek";
        String model = active != null && active.getModel() != null ? active.getModel() : "deepseek-chat";

        String apiKey = llmConfigService.getDecryptedActiveApiKey();
        String baseUrl = "https://api.deepseek.com";
        if (active != null && active.getApiBaseUrl() != null && !active.getApiBaseUrl().isBlank()) {
            baseUrl = active.getApiBaseUrl();
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 1024);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", prompt),
            Map.of("role", "user", "content", "请根据上述要求直接输出 JSON 审核结果。")
        ));

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("LLM API 调用失败: HTTP " + resp.statusCode() + " - " + resp.body());
        }

        JsonNode json = objectMapper.readTree(resp.body());
        JsonNode content = json.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode()) {
            throw new RuntimeException("LLM 响应格式异常: " + resp.body());
        }
        return content.asText();
    }

    private LlmResult parseResult(String llmResponse) {
        try {
            JsonNode json = objectMapper.readTree(llmResponse);
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
            return new LlmResult("REJECT", "LOW", 0,
                List.of("解析 LLM 响应失败"), List.of(),
                BigDecimal.ZERO, "解析异常，默认拒绝");
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    public static class LlmResult {
        private final String decision;
        private final String confidence;
        private final int score;
        private final List<String> keyReasons;
        private final List<String> risks;
        private final BigDecimal suggestedStrength;
        private final String summary;

        public LlmResult(String decision, String confidence, int score, List<String> keyReasons,
                         List<String> risks, BigDecimal suggestedStrength, String summary) {
            this.decision = decision;
            this.confidence = confidence;
            this.score = score;
            this.keyReasons = keyReasons;
            this.risks = risks;
            this.suggestedStrength = suggestedStrength;
            this.summary = summary;
        }

        public String getDecision() { return decision; }
        public String getConfidence() { return confidence; }
        public int getScore() { return score; }
        public List<String> getKeyReasons() { return keyReasons; }
        public List<String> getRisks() { return risks; }
        public BigDecimal getSuggestedStrength() { return suggestedStrength; }
        public String getSummary() { return summary; }
    }
}
