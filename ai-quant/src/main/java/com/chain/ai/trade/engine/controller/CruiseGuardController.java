package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/trading/cruise")
@RequiredArgsConstructor
@Slf4j
public class CruiseGuardController {

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;

    @Value("${cruise.guard.enabled:true}")
    private boolean cruiseEnabled;

    @Value("${cruise.guard.defaultGoalPercent:2}")
    private double defaultGoalPercent;

    @Value("${cruise.guard.defaultMaxLossPercent:1}")
    private double defaultMaxLossPercent;

    @Value("${cruise.guard.action:PAUSE}")
    private String defaultAction;

    @Value("${cruise.guard.cancelPendingPlans:true}")
    private boolean defaultCancelPendingPlans;

    @Value("${cruise.guard.closePositions:true}")
    private boolean defaultClosePositions;

    private static String keyOf(String accountId) {
        return "cruise:guard:session:" + accountId;
    }

    private static long secondsUntilEndOfDay() {
        ZoneId zone = ZoneId.systemDefault();
        long nowMs = System.currentTimeMillis();
        long endMs = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long sec = Duration.ofMillis(Math.max(0, endMs - nowMs)).getSeconds();
        return Math.max(60, sec);
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@RequestBody StartRequest req) {
        try {
            if (!cruiseEnabled) return ApiResponse.error("巡航功能未启用");
            if (req == null) return ApiResponse.error("请求体不能为空");
            String accountId = req.getAccountId() == null ? "" : req.getAccountId().trim();
            if (accountId.isBlank()) return ApiResponse.error("accountId不能为空");

            double goalPercent = req.getGoalPercent() != null ? req.getGoalPercent() : defaultGoalPercent;
            double maxLossPercent = req.getMaxLossPercent() != null ? req.getMaxLossPercent() : defaultMaxLossPercent;
            String action = req.getAction() == null || req.getAction().trim().isEmpty() ? defaultAction : req.getAction().trim().toUpperCase();
            if (!action.equals("PAUSE") && !action.equals("STOP")) return ApiResponse.error("action必须为PAUSE或STOP");

            boolean cancelPendingPlans = req.getCancelPendingPlans() != null ? req.getCancelPendingPlans() : defaultCancelPendingPlans;
            boolean closePositions = req.getClosePositions() != null ? req.getClosePositions() : defaultClosePositions;
            List<String> botIds = req.getBotIds() == null ? List.of() : req.getBotIds().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

            Map<String, Object> session = new LinkedHashMap<>();
            session.put("enabled", true);
            session.put("status", "running");
            session.put("accountId", accountId);
            session.put("goalPercent", goalPercent);
            session.put("maxLossPercent", maxLossPercent);
            session.put("action", action);
            session.put("cancelPendingPlans", cancelPendingPlans);
            session.put("closePositions", closePositions);
            session.put("botIds", botIds);
            session.put("startedAtMs", System.currentTimeMillis());
            session.put("updatedAtMs", System.currentTimeMillis());

            String json = objectMapper.writeValueAsString(session);
            redisCache.put(keyOf(accountId), json, secondsUntilEndOfDay(), TimeUnit.SECONDS);

            return ApiResponse.success("OK", session);
        } catch (Exception e) {
            log.error("启动巡航失败", e);
            return ApiResponse.error("启动巡航失败: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<?> stop(@RequestParam String accountId) {
        try {
            String id = accountId == null ? "" : accountId.trim();
            if (id.isBlank()) return ApiResponse.error("accountId不能为空");
            redisCache.remove(keyOf(id));
            return ApiResponse.success("OK", Map.of("accountId", id, "status", "stopped"));
        } catch (Exception e) {
            log.error("停止巡航失败", e);
            return ApiResponse.error("停止巡航失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestParam String accountId) {
        try {
            String id = accountId == null ? "" : accountId.trim();
            if (id.isBlank()) return ApiResponse.error("accountId不能为空");
            Object raw = redisCache.get(keyOf(id));
            if (raw == null) return ApiResponse.success("OK", Map.of("enabled", false, "status", "none", "accountId", id));

            Map<String, Object> session = new LinkedHashMap<>();
            try {
                session = objectMapper.readValue(String.valueOf(raw), Map.class);
            } catch (Exception ignored) {
                session.put("raw", String.valueOf(raw));
            }
            session.put("accountId", id);
            return ApiResponse.success("OK", session);
        } catch (Exception e) {
            log.error("查询巡航状态失败", e);
            return ApiResponse.error("查询巡航状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            List<Object> keys = redisCache.keys("cruise:guard:session:*");
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object k : keys) {
                String key = String.valueOf(k);
                Object raw = redisCache.get(key);
                if (raw == null) continue;
                try {
                    Map<String, Object> session = objectMapper.readValue(String.valueOf(raw), Map.class);
                    session.put("key", key);
                    out.add(session);
                } catch (Exception ignored) {
                    out.add(Map.of("key", key, "raw", String.valueOf(raw)));
                }
            }
            out.sort((a, b) -> Long.compare(
                    toLong(b.get("updatedAtMs")),
                    toLong(a.get("updatedAtMs"))
            ));
            return ApiResponse.success("OK", out);
        } catch (Exception e) {
            log.error("查询巡航列表失败", e);
            return ApiResponse.error("查询巡航列表失败: " + e.getMessage());
        }
    }

    private static long toLong(Object v) {
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Data
    public static class StartRequest {
        private String accountId;
        private Double goalPercent;
        private Double maxLossPercent;
        private String action;
        private Boolean cancelPendingPlans;
        private Boolean closePositions;
        private List<String> botIds;
    }
}
