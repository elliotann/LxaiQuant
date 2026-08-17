package com.chain.ai.trade.engine.controller.signal;

import com.chain.ai.trade.engine.controller.advice.LiveAdviceController;
import com.chain.ai.trade.engine.entity.TradingAdvice;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.mapper.TradingAdviceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/signal")
@RequiredArgsConstructor
public class AdviceSignalController {

    private final LiveAdviceController liveAdviceController;
    private final TradingAdviceMapper tradingAdviceMapper;
    private final ObjectMapper objectMapper;

    @PostMapping("/create-from-advice")
    public ApiResponse<CreateFromAdviceResponse> createFromAdvice(@RequestBody CreateFromAdviceRequest request) {
        if (request == null || request.adviceId == null || request.adviceId.trim().isEmpty()) {
            return ApiResponse.error(400, "adviceId 不能为空");
        }
        String action = request.action == null ? "" : request.action.trim();
        if (action.isEmpty()) {
            return ApiResponse.error(400, "action 不能为空");
        }
        if ("all_signals".equals(action)) {
            action = "all_signals";
        }

        LiveAdviceController.AdviceRecord record = LiveAdviceController.getAdviceRecord(request.adviceId.trim());
        Map<String, Object> tradeplan = record != null ? record.tradeplan : null;
        boolean tradeplanValid = record != null && record.tradeplanValid;
        List<String> tradeplanErrors = record != null ? record.tradeplanErrors : List.of();

        if (tradeplan == null && request.options != null) {
            Object t = request.options.get("tradeplan");
            if (t instanceof Map) {
                tradeplan = (Map<String, Object>) t;
                tradeplanValid = true;
                tradeplanErrors = List.of();
            }
        }
        if (tradeplan == null) {
            Map<String, Object> loaded = loadTradeplanFromDb(request.adviceId.trim());
            if (loaded != null) {
                tradeplan = loaded;
                tradeplanValid = true;
                tradeplanErrors = List.of();
            }
        }

        if (tradeplan == null) {
            return ApiResponse.error(404, "adviceId 不存在或已过期（且未携带 tradeplan）");
        }
        if (!tradeplanValid) {
            return ApiResponse.error(400, "tradeplan 无效: " + String.join("；", tradeplanErrors));
        }

        try {
            if ("all_signals".equals(action)) {
                List<Long> ids = new ArrayList<>();
                for (String a : List.of("limit_signal", "cond_signal", "hedge_signal", "close_signal")) {
                    try {
                        Long id = liveAdviceController.createTechnicalSignalFromAdvice(request.adviceId.trim(), tradeplan, a);
                        if (id != null) ids.add(id);
                    } catch (Exception ignored) {
                    }
                }
                if (ids.isEmpty()) {
                    return ApiResponse.error(400, "没有可生成的信号（请检查 tradeplan 内容是否包含备选策略/持仓等）");
                }
                return ApiResponse.success("信号已生成，量化系统将自动处理", new CreateFromAdviceResponse(request.adviceId.trim(), action, null, ids));
            }

            if ("limit_signal".equals(action) || "cond_signal".equals(action) || "hedge_signal".equals(action) || "close_signal".equals(action)) {
                Long id = liveAdviceController.createTechnicalSignalFromAdvice(request.adviceId.trim(), tradeplan, action);
                return ApiResponse.success("信号已生成，量化系统将自动处理", new CreateFromAdviceResponse(request.adviceId.trim(), action, id, List.of()));
            }

            return ApiResponse.error(400, "不支持的 action: " + action);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage() == null ? "生成信号失败" : e.getMessage());
        }
    }

    @GetMapping("/auto-config")
    public ApiResponse<AutoConfigResponse> getAutoConfig() {
        LiveAdviceController.AutoSignalConfig cfg = LiveAdviceController.getAutoSignalConfig();
        AutoConfigResponse resp = new AutoConfigResponse(cfg.enabled, cfg.allowedActions, cfg.maxRiskPercent, cfg.onlySimulation, cfg.defaultSignalStrength);
        return ApiResponse.success(resp);
    }

    @PostMapping("/auto-config")
    public ApiResponse<AutoConfigResponse> saveAutoConfig(@RequestBody AutoConfigRequest request) {
        List<String> allowed = request != null && request.allowedActions != null ? request.allowedActions : List.of();
        BigDecimal maxRisk = request != null ? request.maxRiskPercent : null;
        Boolean onlySimulation = request != null ? request.onlySimulation : null;
        Boolean enabled = request != null ? request.enabled : Boolean.FALSE;
        BigDecimal defaultSignalStrength = request != null ? request.defaultSignalStrength : null;
        LiveAdviceController.setAutoSignalConfig(new LiveAdviceController.AutoSignalConfig(enabled, allowed, maxRisk, onlySimulation, defaultSignalStrength));
        LiveAdviceController.AutoSignalConfig cfg = LiveAdviceController.getAutoSignalConfig();
        AutoConfigResponse resp = new AutoConfigResponse(cfg.enabled, cfg.allowedActions, cfg.maxRiskPercent, cfg.onlySimulation, cfg.defaultSignalStrength);
        return ApiResponse.success("已保存", resp);
    }

    public static class CreateFromAdviceRequest {
        public String adviceId;
        public String action;
        public Map<String, Object> options;
    }

    public static class CreateFromAdviceResponse {
        public String adviceId;
        public String action;
        public Long signalId;
        public List<Long> signalIds;

        public CreateFromAdviceResponse(String adviceId, String action, Long signalId, List<Long> signalIds) {
            this.adviceId = adviceId;
            this.action = action;
            this.signalId = signalId;
            this.signalIds = signalIds != null ? signalIds : List.of();
        }
    }

    public static class AutoConfigRequest {
        public Boolean enabled;
        public List<String> allowedActions;
        public BigDecimal maxRiskPercent;
        public Boolean onlySimulation;
        public BigDecimal defaultSignalStrength;
    }

    public static class AutoConfigResponse {
        public Boolean enabled;
        public List<String> allowedActions;
        public BigDecimal maxRiskPercent;
        public Boolean onlySimulation;
        public BigDecimal defaultSignalStrength;

        public AutoConfigResponse(Boolean enabled, List<String> allowedActions, BigDecimal maxRiskPercent, Boolean onlySimulation, BigDecimal defaultSignalStrength) {
            this.enabled = enabled;
            this.allowedActions = allowedActions;
            this.maxRiskPercent = maxRiskPercent;
            this.onlySimulation = onlySimulation;
            this.defaultSignalStrength = defaultSignalStrength;
        }
    }

    private Map<String, Object> loadTradeplanFromDb(String adviceId) {
        if (tradingAdviceMapper == null || objectMapper == null) {
            return null;
        }
        if (adviceId == null || adviceId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<TradingAdvice> q = new LambdaQueryWrapper<>();
        q.eq(TradingAdvice::getAdviceId, adviceId);
        TradingAdvice row = tradingAdviceMapper.selectOne(q);
        if (row == null) {
            return null;
        }
        String json = row.getTradeplanJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }
}
