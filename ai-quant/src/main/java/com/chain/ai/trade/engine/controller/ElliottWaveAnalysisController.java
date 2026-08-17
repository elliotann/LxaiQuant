package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.controller.dto.ElliottWaveAnalysisDTO;
import com.chain.ai.trade.engine.service.ElliottWaveAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@Slf4j
public class ElliottWaveAnalysisController {

    private final ElliottWaveAnalysisService elliottWaveAnalysisService;

    @Autowired(required = false)
    private RedisCache redisCache;

    @GetMapping("/elliott-wave-analysis")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false, defaultValue = "500") Integer limit,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) Double fibTolerance
    ) {
        try {
            int safeLimit = (limit == null || limit <= 0) ? 500 : Math.min(limit, 2000);
            String cacheKey = buildCacheKey(symbol, interval, safeLimit, degree, fibTolerance);
            ElliottWaveAnalysisDTO cached = getCache(cacheKey);
            if (cached != null) {
                refreshCacheAsync(symbol, interval, safeLimit, degree, fibTolerance, cacheKey);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", cached);
                return ResponseEntity.ok(result);
            }

            ElliottWaveAnalysisDTO data = elliottWaveAnalysisService.analyze(symbol, interval, safeLimit, degree, fibTolerance);
            if (data == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "无足够本地K线数据，无法分析");
                return ResponseEntity.status(404).body(result);
            }

            putCache(cacheKey, data);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            log.error("艾略特波浪分析失败: symbol={}, interval={}", symbol, interval, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "艾略特波浪分析失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private String buildCacheKey(String symbol, String interval, int limit, String degree, Double fibTolerance) {
        String d = degree == null ? "-" : degree;
        String t = fibTolerance == null ? "-" : String.valueOf(fibTolerance);
        return "elliott:analysis:" + symbol + ":" + interval + ":" + limit + ":" + d + ":" + t;
    }

    private ElliottWaveAnalysisDTO getCache(String key) {
        if (redisCache == null) return null;
        Object v = redisCache.get(key);
        if (v instanceof ElliottWaveAnalysisDTO dto) {
            return dto;
        }
        return null;
    }

    private void putCache(String key, ElliottWaveAnalysisDTO data) {
        if (redisCache == null || data == null) return;
        redisCache.put(key, data, 30L);
    }

    private void refreshCacheAsync(String symbol, String interval, int limit, String degree, Double fibTolerance, String key) {
        CompletableFuture.runAsync(() -> {
            try {
                ElliottWaveAnalysisDTO data = elliottWaveAnalysisService.analyze(symbol, interval, limit, degree, fibTolerance);
                putCache(key, data);
            } catch (Exception e) {
                log.debug("艾略特波浪分析缓存刷新失败: {}", e.getMessage());
            }
        });
    }
}

