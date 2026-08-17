package com.chain.ai.trade.engine.controller.openclaw;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.controller.order.ManualController;
import com.chain.ai.trade.engine.entity.AiTradePlan;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.mapper.AiTradePlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/openclaw/trade-plans")
@RequiredArgsConstructor
public class OpenClawTradePlansController {

    private static final long PLAN_TTL_SECONDS = 30 * 60;
    private static final String PLAN_KEY_PREFIX = "openclaw:tradeplan:";
    private static final String PREVIEW_KEY_PREFIX = "openclaw:order:preview:";

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;
    private final ManualController manualController;
    private final AiTradePlanMapper aiTradePlanMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@RequestBody CreatePlanRequest req) {
        if (req == null) {
            return ResponseEntity.ok(ApiResponse.error("请求体不能为空"));
        }
        String previewId = req.getPreviewId() == null ? "" : req.getPreviewId().trim();
        if (previewId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("previewId不能为空"));
        }
        try {
            PreviewStore previewStore = loadPreview(previewId);
            String planUuid = UUID.randomUUID().toString().replace("-", "");

            TradePlanStore store = new TradePlanStore();
            store.setPlanUuid(planUuid);
            store.setPreviewId(previewId);
            store.setCreatedAtMs(System.currentTimeMillis());
            store.setName(req.getName());
            store.setDescription(req.getDescription());
            store.setStatus("pending");
            store.setPlanContent(req.getPlanContent());
            store.setTrace(req.getTrace());
            store.setPreviewType(previewStore.getType());

            redisCache.put(PLAN_KEY_PREFIX + planUuid, objectMapper.writeValueAsString(store), PLAN_TTL_SECONDS);
            persistPlan(store);

            Map<String, Object> data = new HashMap<>();
            data.put("planUuid", planUuid);
            data.put("previewId", previewId);
            data.put("status", store.getStatus());
            data.put("planContent", store.getPlanContent());
            data.put("trace", store.getTrace());
            data.put("expiresInSeconds", PLAN_TTL_SECONDS);
            data.put("next", Map.of(
                    "tool", "quant_trade_plan_confirm",
                    "params", Map.of("planUuid", planUuid, "previewId", previewId)
            ));

            return ResponseEntity.ok(ApiResponse.success("计划已创建", data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "创建失败" : e.getMessage()));
        }
    }

    @GetMapping("/{planUuid}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable String planUuid) {
        String id = planUuid == null ? "" : planUuid.trim();
        if (id.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("planUuid不能为空"));
        }
        try {
            TradePlanStore store = loadPlan(id);
            return ResponseEntity.ok(ApiResponse.success("查询成功", store));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "查询失败" : e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) String status
    ) {
        int take = limit == null ? 50 : Math.min(200, Math.max(1, limit));
        String st = status == null ? null : status.trim();
        if (st != null && st.isEmpty()) st = null;
        try {
            LambdaQueryWrapper<AiTradePlan> w = new LambdaQueryWrapper<AiTradePlan>()
                    .eq(st == null ? false : true, AiTradePlan::getStatus, st)
                    .orderByDesc(AiTradePlan::getUpdateTime)
                    .last("limit " + take);
            List<AiTradePlan> rows = aiTradePlanMapper.selectList(w);
            List<TradePlanStore> out = rows.stream().map(p -> {
                TradePlanStore s = new TradePlanStore();
                s.setPlanUuid(p.getPlanUuid());
                s.setPreviewId(p.getPreviewId());
                s.setPreviewType(p.getPreviewType());
                s.setName(p.getName());
                s.setDescription(p.getDescription());
                s.setStatus(p.getStatus());
                s.setCreatedAtMs(p.getCreateTime() == null ? null : p.getCreateTime().getTime());
                try {
                    if (p.getPlanContent() != null && !p.getPlanContent().isBlank()) {
                        s.setPlanContent(objectMapper.readValue(p.getPlanContent(), Map.class));
                    }
                    if (p.getTrace() != null && !p.getTrace().isBlank()) {
                        s.setTrace(objectMapper.readValue(p.getTrace(), Map.class));
                    }
                    if (p.getExecutionResult() != null && !p.getExecutionResult().isBlank()) {
                        s.setExecutionResult(objectMapper.readValue(p.getExecutionResult(), Object.class));
                    }
                } catch (Exception ignored) {
                }
                return s;
            }).toList();
            return ResponseEntity.ok(ApiResponse.success("查询成功", out));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "查询失败" : e.getMessage()));
        }
    }

    @PostMapping("/{planUuid}/confirm")
    public ResponseEntity<ApiResponse<?>> confirm(@PathVariable String planUuid, @RequestBody ConfirmPlanRequest req) {
        String id = planUuid == null ? "" : planUuid.trim();
        if (id.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("planUuid不能为空"));
        }
        if (req == null || req.getPreviewId() == null || req.getPreviewId().trim().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("previewId不能为空"));
        }
        try {
            TradePlanStore store = loadPlan(id);
            String providedPreviewId = req.getPreviewId().trim();
            if (!providedPreviewId.equals(store.getPreviewId())) {
                return ResponseEntity.ok(ApiResponse.error("previewId不匹配"));
            }

            if ("executed".equalsIgnoreCase(store.getStatus()) && store.getExecutionResult() != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("planUuid", store.getPlanUuid());
                data.put("status", store.getStatus());
                data.put("executionResult", store.getExecutionResult());
                return ResponseEntity.ok(ApiResponse.success("已执行", data));
            }

            PreviewStore previewStore;
            try {
                previewStore = loadPreview(providedPreviewId);
            } catch (Exception ex) {
                store.setStatus("failed");
                store.setExecutionResult(Map.of("error", ex.getMessage() == null ? "previewId已过期或不存在" : ex.getMessage()));
                redisCache.put(PLAN_KEY_PREFIX + id, objectMapper.writeValueAsString(store), PLAN_TTL_SECONDS);
                persistPlan(store);
                return ResponseEntity.ok(ApiResponse.error("previewId已过期或不存在"));
            }
            if (previewStore.getType() == null || previewStore.getType().isBlank()) {
                return ResponseEntity.ok(ApiResponse.error("预检数据缺少类型"));
            }

            ApiResponse<?> result;
            if ("OPEN".equalsIgnoreCase(previewStore.getType())) {
                if (previewStore.getOpen() == null) return ResponseEntity.ok(ApiResponse.error("预检数据缺少开仓请求"));
                result = manualController.open(previewStore.getOpen());
            } else if ("CLOSE".equalsIgnoreCase(previewStore.getType())) {
                if (previewStore.getClose() == null) return ResponseEntity.ok(ApiResponse.error("预检数据缺少平仓请求"));
                result = manualController.close(previewStore.getClose());
            } else {
                return ResponseEntity.ok(ApiResponse.error("未知预检类型"));
            }

            store.setStatus(Boolean.TRUE.equals(result.getSuccess()) ? "executed" : "failed");
            store.setExecutionResult(result);
            redisCache.put(PLAN_KEY_PREFIX + id, objectMapper.writeValueAsString(store), PLAN_TTL_SECONDS);
            persistPlan(store);

            Map<String, Object> data = new HashMap<>();
            data.put("planUuid", store.getPlanUuid());
            data.put("status", store.getStatus());
            data.put("executionResult", result);
            return ResponseEntity.ok(ApiResponse.success("确认完成", data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "确认失败" : e.getMessage()));
        }
    }

    private TradePlanStore loadPlan(String planUuid) throws Exception {
        AiTradePlan plan = aiTradePlanMapper.selectOne(
                new LambdaQueryWrapper<AiTradePlan>().eq(AiTradePlan::getPlanUuid, planUuid).last("limit 1")
        );
        if (plan != null) {
            TradePlanStore store = new TradePlanStore();
            store.setPlanUuid(plan.getPlanUuid());
            store.setPreviewId(plan.getPreviewId());
            store.setPreviewType(plan.getPreviewType());
            store.setName(plan.getName());
            store.setDescription(plan.getDescription());
            store.setStatus(plan.getStatus());
            store.setCreatedAtMs(plan.getCreateTime() == null ? null : plan.getCreateTime().getTime());
            if (plan.getPlanContent() != null && !plan.getPlanContent().isBlank()) {
                store.setPlanContent(objectMapper.readValue(plan.getPlanContent(), Map.class));
            }
            if (plan.getTrace() != null && !plan.getTrace().isBlank()) {
                store.setTrace(objectMapper.readValue(plan.getTrace(), Map.class));
            }
            if (plan.getExecutionResult() != null && !plan.getExecutionResult().isBlank()) {
                store.setExecutionResult(objectMapper.readValue(plan.getExecutionResult(), Object.class));
            }
            return store;
        }

        String raw = (String) redisCache.get(PLAN_KEY_PREFIX + planUuid);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("planUuid已过期或不存在");
        }
        return objectMapper.readValue(raw, TradePlanStore.class);
    }

    private PreviewStore loadPreview(String previewId) throws Exception {
        String raw = (String) redisCache.get(PREVIEW_KEY_PREFIX + previewId);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("previewId已过期或不存在");
        }
        return objectMapper.readValue(raw, PreviewStore.class);
    }

    private void persistPlan(TradePlanStore store) throws Exception {
        if (store == null) return;
        String planUuid = store.getPlanUuid() == null ? "" : store.getPlanUuid().trim();
        if (planUuid.isBlank()) return;

        try {
            AiTradePlan existing = aiTradePlanMapper.selectOne(
                    new LambdaQueryWrapper<AiTradePlan>().eq(AiTradePlan::getPlanUuid, planUuid).last("limit 1")
            );
            Date now = new Date();
            if (existing == null) {
                AiTradePlan plan = new AiTradePlan();
                ensureBaseFields(plan, now);
                plan.setPlanUuid(planUuid);
                plan.setPreviewId(store.getPreviewId());
                plan.setPreviewType(store.getPreviewType());
                plan.setName(store.getName());
                plan.setDescription(store.getDescription());
                plan.setStatus(store.getStatus());
                plan.setPlanContent(store.getPlanContent() == null ? null : objectMapper.writeValueAsString(store.getPlanContent()));
                plan.setTrace(store.getTrace() == null ? null : objectMapper.writeValueAsString(store.getTrace()));
                plan.setExecutionResult(store.getExecutionResult() == null ? null : objectMapper.writeValueAsString(store.getExecutionResult()));
                plan.setUpdateTime(now);
                plan.setUpdateBy("system");
                aiTradePlanMapper.insert(plan);
                return;
            }

            existing.setPreviewId(store.getPreviewId());
            existing.setPreviewType(store.getPreviewType());
            existing.setName(store.getName());
            existing.setDescription(store.getDescription());
            existing.setStatus(store.getStatus());
            existing.setPlanContent(store.getPlanContent() == null ? existing.getPlanContent() : objectMapper.writeValueAsString(store.getPlanContent()));
            existing.setTrace(store.getTrace() == null ? existing.getTrace() : objectMapper.writeValueAsString(store.getTrace()));
            existing.setExecutionResult(store.getExecutionResult() == null ? existing.getExecutionResult() : objectMapper.writeValueAsString(store.getExecutionResult()));
            ensureBaseFields(existing, now);
            existing.setUpdateTime(now);
            existing.setUpdateBy("system");
            aiTradePlanMapper.updateById(existing);
        } catch (Exception e) {
            throw new IllegalStateException("ai_trade_plan 表不可用，请先执行 ai-quant/src/main/resources/schema.sql 中 ai_trade_plan 建表语句", e);
        }
    }

    private void ensureBaseFields(AiTradePlan plan, Date now) {
        if (plan.getCreateTime() == null) plan.setCreateTime(now);
        if (plan.getUpdateTime() == null) plan.setUpdateTime(now);
        if (plan.getDeleteFlag() == null) plan.setDeleteFlag(false);
        if (plan.getCreateBy() == null || plan.getCreateBy().isBlank()) plan.setCreateBy("system");
        if (plan.getUpdateBy() == null || plan.getUpdateBy().isBlank()) plan.setUpdateBy("system");
    }

    @Data
    public static class CreatePlanRequest {
        private String name;
        private String description;
        private String previewId;
        private Map<String, Object> planContent;
        private Map<String, Object> trace;
    }

    @Data
    public static class ConfirmPlanRequest {
        private String previewId;
    }

    @Data
    public static class TradePlanStore {
        private String planUuid;
        private String previewId;
        private String previewType;
        private String name;
        private String description;
        private String status;
        private Long createdAtMs;
        private Map<String, Object> planContent;
        private Map<String, Object> trace;
        private Object executionResult;
    }

    @Data
    public static class PreviewStore {
        private String type;
        private String previewId;
        private Long createdAtMs;
        private ManualController.ManualOpenRequest open;
        private ManualController.ManualCloseRequest close;
    }
}
