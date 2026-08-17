package com.chain.ai.trade.engine.service.impl;

import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.LlmConfigService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.entity.dto.AiConfirmRequest;
import com.chain.ai.trade.engine.strategy.entity.dto.AiGenerateRequest;
import com.chain.ai.trade.engine.strategy.entity.dto.AiStrategyRecommendation;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiStrategyService {

    private static final String PROVIDER_KEY_PREFIX = "provider:";
    private static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final LlmConfigService llmConfigService;
    private final IStrategyService strategyService;
    private final ITradingBotService tradingBotService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final String SYSTEM_PROMPT = """
            你是一个专业的量化交易策略顾问。根据用户的需求，推荐合适的交易策略配置。
            
            请分析用户的交易需求，返回严格的 JSON 格式（不要包含 markdown 代码块标记）：
            {
              "botType": "趋势跟踪/网格交易/均值回归/突破交易/剥头皮",
              "reason": "推荐理由，简明扼要说明为什么推荐这个策略",
              "baseConfig": {
                "symbol": "交易对，如 BTC/USDT",
                "timeframe": "时间周期，如 1h/4h/1d",
                "marketType": "交易市场类型，spot(现货) 或 swap(合约/永续)",
                "leverage": 1,
                "initialCapital": 1000
              },
              "strategyParams": {
                "参数名": "参数值"
              },
              "riskConfig": {
                "maxDrawdownPct": 20,
                "maxPositionPct": 50,
                "dailyLossLimitPct": 10
              }
            }
            
            参数说明：
            1. strategyParams 根据策略类型动态生成合理的参数
            2. 对于网格交易(GRID)，strategyParams 应包含:
               - gridLevels: 网格层数(5-20)
               - gridSpacingPct: 每层间距百分比(1-3)
               - orderSize: 每层订单大小占总资金比例(0.05=5%)
               - gridMode: 网格计算模式，"arithmetic"(等差) 或 "geometric"(等比)
               - gridDirection: 方向，"neutral"(中性) 或 "long"(做多) 或 "short"(做空)
            3. 所有数值使用数字类型，不要使用字符串
            4. 如果用户没有指定某些参数，根据市场常识给出合理默认值
            5. 使用中文回复 reason 字段
            6. 当 marketType 为 "spot"(现货) 时：leverage 固定为 1，gridDirection 只能为 "long"(做多)
            """;

    public ApiResponse<AiStrategyRecommendation> generate(AiGenerateRequest request) {
        try {
            LlmConfig cfg = llmConfigService.getByKey(PROVIDER_KEY_PREFIX + "deepseek");
            if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
                return ApiResponse.error(400, "DeepSeek API Key 未配置，请在系统设置中配置");
            }

            String apiKey = decryptApiKey(cfg.getApiKeyEnc());
            String baseUrl = cfg.getApiBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = DEEPSEEK_DEFAULT_BASE_URL;
            }
            String model = cfg.getModel() != null ? cfg.getModel() : "deepseek-chat";

            String marketType = request.getMarketType();
            String userPrompt = request.getPrompt();
            if (marketType != null && !marketType.isBlank()) {
                String marketTypeLabel = "spot".equals(marketType) ? "spot（现货）" : "swap（合约/永续）";
                userPrompt = "【用户选择的市场类型: " + marketTypeLabel + "】\n" + userPrompt;
            }

            String responseBody = callDeepSeek(userPrompt, apiKey, baseUrl, model);
            AiStrategyRecommendation recommendation = parseResponse(responseBody);

            return ApiResponse.success("生成成功", recommendation);

        } catch (Exception e) {
            log.error("AI策略生成失败", e);
            return ApiResponse.error("AI策略生成失败: " + e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Map<String, Object>> confirm(AiConfirmRequest request) {
        try {
            AiStrategyRecommendation rec = request.getRecommendation();

            String strategyId = strategyService.generateStrategyId();

            Strategy strategy = new Strategy();
            strategy.setStrategyId(strategyId);
            strategy.setName(request.getBotName() + "_策略");
            strategy.setStrategyType(mapBotTypeToStrategyType(rec.getBotType()));
            strategy.setTimeFrame(rec.getBaseConfig().getTimeframe());
            strategy.setDescription(rec.getReason());
            strategy.setStatus("draft");
            strategy.setVisibility("private");
            strategy.setCreatedAt(LocalDateTime.now());
            strategy.setUpdatedAt(LocalDateTime.now());
            strategyService.save(strategy);

            TradingBot bot = new TradingBot();
            bot.setBotName(request.getBotName());
            bot.setStrategyId(strategyId);
            bot.setUserId(request.getUserId());
            bot.setTradingPair(rec.getBaseConfig().getSymbol());
            bot.setAllocatedCapital(rec.getBaseConfig().getInitialCapital());
            bot.setCurrentCapital(rec.getBaseConfig().getInitialCapital());
            if (rec.getBaseConfig().getLeverage() != null) {
                Map<String, Object> config = new HashMap<>();
                config.put("leverage", rec.getBaseConfig().getLeverage());
                config.put("timeframe", rec.getBaseConfig().getTimeframe());
                config.put("strategyParams", rec.getStrategyParams());
                config.put("riskConfig", rec.getRiskConfig());
                bot.setConfiguration(objectMapper.writeValueAsString(config));
            }
            bot.setRemark(request.getRemark());
            bot.setAccountId(request.getAccountId());
            tradingBotService.createBot(bot);

            Map<String, Object> result = new HashMap<>();
            result.put("strategyId", strategyId);
            result.put("botId", bot.getBotId());
            result.put("botName", bot.getBotName());

            log.info("AI创建策略和机器人成功: strategyId={}, botId={}", strategyId, bot.getBotId());
            return ApiResponse.success("创建成功", result);

        } catch (Exception e) {
            log.error("AI确认创建失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    private String callDeepSeek(String userPrompt, String apiKey, String baseUrl, String model) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2048);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API 调用失败: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode error = json.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new RuntimeException("LLM API 返回错误: " + error.path("message").asText());
        }

        String content = json.path("choices").get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM 返回内容为空");
        }

        return content;
    }

    private AiStrategyRecommendation parseResponse(String content) throws Exception {
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        }

        JsonNode node = objectMapper.readTree(json);
        AiStrategyRecommendation rec = new AiStrategyRecommendation();
        rec.setBotType(node.path("botType").asText());
        rec.setReason(node.path("reason").asText());

        JsonNode baseConfigNode = node.path("baseConfig");
        if (!baseConfigNode.isMissingNode()) {
            AiStrategyRecommendation.BaseConfig baseConfig = new AiStrategyRecommendation.BaseConfig();
            baseConfig.setSymbol(baseConfigNode.path("symbol").asText());
            baseConfig.setTimeframe(baseConfigNode.path("timeframe").asText());
            baseConfig.setMarketType(baseConfigNode.path("marketType").asText());
            if (baseConfigNode.has("leverage") && !baseConfigNode.path("leverage").isNull()) {
                baseConfig.setLeverage(baseConfigNode.path("leverage").asInt());
            }
            if (baseConfigNode.has("initialCapital") && !baseConfigNode.path("initialCapital").isNull()) {
                baseConfig.setInitialCapital(new BigDecimal(baseConfigNode.path("initialCapital").asText()));
            }
            rec.setBaseConfig(baseConfig);
        }

        JsonNode strategyParamsNode = node.path("strategyParams");
        if (!strategyParamsNode.isMissingNode() && strategyParamsNode.isObject()) {
            Map<String, Object> params = objectMapper.convertValue(strategyParamsNode, Map.class);
            rec.setStrategyParams(params);
        }

        JsonNode riskConfigNode = node.path("riskConfig");
        if (!riskConfigNode.isMissingNode()) {
            AiStrategyRecommendation.RiskConfig riskConfig = new AiStrategyRecommendation.RiskConfig();
            if (riskConfigNode.has("maxDrawdownPct") && !riskConfigNode.path("maxDrawdownPct").isNull()) {
                riskConfig.setMaxDrawdownPct(new BigDecimal(riskConfigNode.path("maxDrawdownPct").asText()));
            }
            if (riskConfigNode.has("maxPositionPct") && !riskConfigNode.path("maxPositionPct").isNull()) {
                riskConfig.setMaxPositionPct(new BigDecimal(riskConfigNode.path("maxPositionPct").asText()));
            }
            if (riskConfigNode.has("dailyLossLimitPct") && !riskConfigNode.path("dailyLossLimitPct").isNull()) {
                riskConfig.setDailyLossLimitPct(new BigDecimal(riskConfigNode.path("dailyLossLimitPct").asText()));
            }
            rec.setRiskConfig(riskConfig);
        }

        return rec;
    }

    private String mapBotTypeToStrategyType(String botType) {
        if (botType == null) return "TREND";
        if (botType.contains("趋势")) return "TREND";
        if (botType.contains("网格")) return "GRID";
        if (botType.contains("均值回归") || botType.contains("回归")) return "MEAN_REVERSION";
        if (botType.contains("突破")) return "BREAKOUT";
        if (botType.contains("剥头皮") || botType.contains("高频")) return "SCALPING";
        return "TREND";
    }

    private String decryptApiKey(String encryptedKey) {
        try {
            String encryptionKey = System.getenv("ACCOUNT_SECRET_KEY");
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                encryptionKey = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
            }
            AesGcmEncryptor encryptor = new AesGcmEncryptor(encryptionKey);
            return encryptor.decrypt(encryptedKey);
        } catch (Exception e) {
            log.error("解密API Key失败", e);
            return encryptedKey;
        }
    }
}
