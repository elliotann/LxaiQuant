package com.chain.ai.trade.engine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.controller.openclaw.OpenClawOrdersController;
import com.chain.ai.trade.engine.controller.openclaw.OpenClawTradePlansController;
import com.chain.ai.trade.engine.controller.order.ManualController;
import com.chain.ai.trade.engine.entity.AiTradePlan;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.mapper.AiTradePlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trading/trade-plans")
@RequiredArgsConstructor
@Slf4j
public class TradingTradePlanController {

    private static final long PREVIEW_TTL_SECONDS = 30 * 60;

    private final AiTradePlanMapper aiTradePlanMapper;
    private final ObjectMapper objectMapper;
    private final RedisCache redisCache;
    private final OpenClawOrdersController openClawOrdersController;
    private final OpenClawTradePlansController openClawTradePlansController;

    @GetMapping
    public ApiResponse<List<TradePlanDTO>> list(
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) String status
    ) {
        try {
            int take = limit == null ? 50 : Math.min(200, Math.max(1, limit));
            String st = status == null ? null : status.trim();
            if (st != null && st.isEmpty()) st = null;

            LambdaQueryWrapper<AiTradePlan> w = new LambdaQueryWrapper<AiTradePlan>()
                    .eq(st == null ? false : true, AiTradePlan::getStatus, st)
                    .orderByDesc(AiTradePlan::getUpdateTime)
                    .last("limit " + take);
            List<AiTradePlan> rows = aiTradePlanMapper.selectList(w);

            List<TradePlanDTO> out = rows.stream().map(this::toDtoSafe).toList();
            return ApiResponse.success("OK", out);
        } catch (Exception e) {
            log.error("查询交易计划失败", e);
            return ApiResponse.error("查询交易计划失败: " + e.getMessage());
        }
    }

    @GetMapping("/{planUuid}")
    public ApiResponse<TradePlanDTO> get(@PathVariable String planUuid) {
        try {
            String id = planUuid == null ? "" : planUuid.trim();
            if (id.isBlank()) return ApiResponse.error("planUuid不能为空");
            AiTradePlan row = aiTradePlanMapper.selectOne(
                    new LambdaQueryWrapper<AiTradePlan>().eq(AiTradePlan::getPlanUuid, id).last("limit 1")
            );
            if (row == null) return ApiResponse.error("planUuid不存在");
            return ApiResponse.success("OK", toDtoSafe(row));
        } catch (Exception e) {
            log.error("查询交易计划失败", e);
            return ApiResponse.error("查询交易计划失败: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateTradePlanRequest req) {
        try {
            if (req == null) return ApiResponse.error("请求体不能为空");
            String previewType = req.getPreviewType() == null ? "" : req.getPreviewType().trim().toUpperCase();
            if (previewType.isBlank()) previewType = "OPEN";
            if (!previewType.equals("OPEN") && !previewType.equals("CLOSE")) return ApiResponse.error("previewType必须为OPEN或CLOSE");

            String planUuid = UUID.randomUUID().toString().replace("-", "");
            Date now = new Date();

            AiTradePlan plan = new AiTradePlan();
            plan.setPlanUuid(planUuid);
            plan.setPreviewType(previewType);
            plan.setPreviewId(null);
            plan.setName(req.getName());
            plan.setDescription(req.getDescription());
            plan.setStatus("pending");
            plan.setPlanContent(req.getPlanContent() == null ? null : objectMapper.writeValueAsString(req.getPlanContent()));
            plan.setTrace(req.getTrace() == null ? null : objectMapper.writeValueAsString(req.getTrace()));
            plan.setExecutionResult(null);
            if (plan.getCreateTime() == null) plan.setCreateTime(now);
            if (plan.getUpdateTime() == null) plan.setUpdateTime(now);
            if (plan.getDeleteFlag() == null) plan.setDeleteFlag(false);
            if (plan.getCreateBy() == null || plan.getCreateBy().isBlank()) plan.setCreateBy("system");
            if (plan.getUpdateBy() == null || plan.getUpdateBy().isBlank()) plan.setUpdateBy("system");

            aiTradePlanMapper.insert(plan);
            return ApiResponse.success("创建成功", Map.of("planUuid", planUuid, "status", "pending"));
        } catch (Exception e) {
            log.error("创建交易计划失败", e);
            return ApiResponse.error("创建交易计划失败: " + e.getMessage());
        }
    }

    @PostMapping("/{planUuid}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String planUuid, @RequestBody PreviewTradePlanRequest req) {
        try {
            String id = planUuid == null ? "" : planUuid.trim();
            if (id.isBlank()) return ApiResponse.error("planUuid不能为空");
            AiTradePlan plan = aiTradePlanMapper.selectOne(
                    new LambdaQueryWrapper<AiTradePlan>().eq(AiTradePlan::getPlanUuid, id).last("limit 1")
            );
            if (plan == null) return ApiResponse.error("planUuid不存在");
            if ("executed".equalsIgnoreCase(plan.getStatus())) return ApiResponse.error("计划已执行");

            Map<String, Object> pc = null;
            if (plan.getPlanContent() != null && !plan.getPlanContent().isBlank()) {
                pc = objectMapper.readValue(plan.getPlanContent(), Map.class);
            }
            if (pc == null) pc = new HashMap<>();

            String accountId = req != null && req.getAccountId() != null && !req.getAccountId().trim().isBlank()
                    ? req.getAccountId().trim()
                    : (pc.get("accountId") == null ? "" : String.valueOf(pc.get("accountId")).trim());
            if (accountId.isBlank()) return ApiResponse.error("accountId不能为空");

            String symbol = pc.get("symbol") == null ? "" : String.valueOf(pc.get("symbol")).trim();
            if (symbol.isBlank()) return ApiResponse.error("symbol不能为空");

            String side = pc.get("side") == null ? "" : String.valueOf(pc.get("side")).trim();
            if (side.isBlank()) return ApiResponse.error("side不能为空");

            String orderType = pc.get("orderType") == null ? "MARKET" : String.valueOf(pc.get("orderType")).trim();
            Object qtyObj = pc.get("quantity");
            Object levObj = pc.get("leverage");
            Object priceObj = pc.get("limitPrice");

            String pt = plan.getPreviewType() == null ? "OPEN" : plan.getPreviewType().trim().toUpperCase();
            if (!pt.equals("OPEN") && !pt.equals("CLOSE")) pt = "OPEN";

            ResponseEntity<ApiResponse<?>> res;
            if (pt.equals("OPEN")) {
                ManualController.ManualOpenRequest open = new ManualController.ManualOpenRequest();
                open.setAccountId(accountId);
                open.setSymbol(symbol);
                open.setSide(side);
                open.setOrderType(orderType);
                if (qtyObj != null) open.setQuantity(new java.math.BigDecimal(String.valueOf(qtyObj)));
                if (levObj != null) open.setLeverage(Integer.valueOf(String.valueOf(levObj)));
                if (priceObj != null) open.setLimitPrice(new java.math.BigDecimal(String.valueOf(priceObj)));
                res = openClawOrdersController.previewOpen(System.getenv("OPENCLAW_BRIDGE_TOKEN"), open);
            } else {
                ManualController.ManualCloseRequest close = new ManualController.ManualCloseRequest();
                close.setAccountId(accountId);
                close.setSymbol(symbol);
                close.setSide(side);
                close.setOrderType(orderType);
                if (qtyObj != null) close.setQuantity(new java.math.BigDecimal(String.valueOf(qtyObj)));
                if (priceObj != null) close.setLimitPrice(new java.math.BigDecimal(String.valueOf(priceObj)));
                res = openClawOrdersController.previewClose(System.getenv("OPENCLAW_BRIDGE_TOKEN"), close);
            }

            ApiResponse<?> body = res.getBody();
            if (body == null) return ApiResponse.error("预检失败");
            if (!Boolean.TRUE.equals(body.getSuccess())) return ApiResponse.error(body.getMessage() == null ? "预检失败" : body.getMessage());

            Object dataObj = body.getData();
            if (!(dataObj instanceof Map)) return ApiResponse.error("预检返回数据异常");
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object previewIdObj = data.get("previewId");
            String previewId = previewIdObj == null ? "" : String.valueOf(previewIdObj).trim();
            if (previewId.isBlank()) return ApiResponse.error("预检返回previewId为空");

            plan.setPreviewId(previewId);
            plan.setUpdateTime(new Date());
            plan.setUpdateBy("system");
            aiTradePlanMapper.updateById(plan);

            return ApiResponse.success("预检通过", Map.of(
                    "planUuid", id,
                    "previewId", previewId,
                    "expiresInSeconds", PREVIEW_TTL_SECONDS,
                    "warnings", data.get("warnings")
            ));
        } catch (Exception e) {
            log.error("预检交易计划失败", e);
            return ApiResponse.error("预检交易计划失败: " + e.getMessage());
        }
    }

    @PostMapping("/{planUuid}/confirm")
    public ApiResponse<?> confirm(@PathVariable String planUuid) {
        try {
            String id = planUuid == null ? "" : planUuid.trim();
            if (id.isBlank()) return ApiResponse.error("planUuid不能为空");
            AiTradePlan plan = aiTradePlanMapper.selectOne(
                    new LambdaQueryWrapper<AiTradePlan>().eq(AiTradePlan::getPlanUuid, id).last("limit 1")
            );
            if (plan == null) return ApiResponse.error("planUuid不存在");
            if (plan.getPreviewId() == null || plan.getPreviewId().isBlank()) return ApiResponse.error("请先预检");

            OpenClawTradePlansController.ConfirmPlanRequest req = new OpenClawTradePlansController.ConfirmPlanRequest();
            req.setPreviewId(plan.getPreviewId());
            ResponseEntity<ApiResponse<?>> res = openClawTradePlansController.confirm(id, req);
            ApiResponse<?> body = res.getBody();
            return body == null ? ApiResponse.error("确认失败") : body;
        } catch (Exception e) {
            log.error("确认执行交易计划失败", e);
            return ApiResponse.error("确认执行交易计划失败: " + e.getMessage());
        }
    }

    private TradePlanDTO toDtoSafe(AiTradePlan p) {
        TradePlanDTO dto = new TradePlanDTO();
        dto.setId(p.getId());
        dto.setPlanUuid(p.getPlanUuid());
        dto.setPreviewId(p.getPreviewId());
        dto.setPreviewType(p.getPreviewType());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setStatus(p.getStatus());
        dto.setCreatedAtMs(p.getCreateTime() == null ? null : p.getCreateTime().getTime());
        dto.setUpdatedAtMs(p.getUpdateTime() == null ? null : p.getUpdateTime().getTime());
        try {
            if (p.getPlanContent() != null && !p.getPlanContent().isBlank()) {
                dto.setPlanContent(objectMapper.readValue(p.getPlanContent(), Map.class));
            }
        } catch (Exception ignored) {
        }
        try {
            if (p.getTrace() != null && !p.getTrace().isBlank()) {
                dto.setTrace(objectMapper.readValue(p.getTrace(), Map.class));
            }
        } catch (Exception ignored) {
        }
        try {
            if (p.getExecutionResult() != null && !p.getExecutionResult().isBlank()) {
                dto.setExecutionResult(objectMapper.readValue(p.getExecutionResult(), Object.class));
            }
        } catch (Exception ignored) {
        }
        return dto;
    }

    @Data
    public static class TradePlanDTO {
        private String id;
        private String planUuid;
        private String previewId;
        private String previewType;
        private String name;
        private String description;
        private String status;
        private Long createdAtMs;
        private Long updatedAtMs;
        private Map<String, Object> planContent;
        private Map<String, Object> trace;
        private Object executionResult;
    }

    @Data
    public static class CreateTradePlanRequest {
        private String name;
        private String description;
        private String previewType;
        private Map<String, Object> planContent;
        private Map<String, Object> trace;
    }

    @Data
    public static class PreviewTradePlanRequest {
        private String accountId;
    }
}
