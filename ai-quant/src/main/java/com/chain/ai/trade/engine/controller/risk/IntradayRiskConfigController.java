package com.chain.ai.trade.engine.controller.risk;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/risk/intraday")
@RequiredArgsConstructor
@Slf4j
public class IntradayRiskConfigController {

    private static final String DEFAULT_CONFIG_KEY = "risk:intraday:config:default";

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig(@RequestParam(value = "memberId", required = false) Long memberId) {
        try {
            String key = memberId != null ? ("risk:intraday:config:member:" + memberId) : DEFAULT_CONFIG_KEY;
            Object v = redisCache.get(key);
            MemberRiskConfig config = null;
            if (v instanceof String s && !s.isBlank()) {
                config = objectMapper.readValue(s, MemberRiskConfig.class);
            } else if (v instanceof MemberRiskConfig c) {
                config = c;
            }
            if (config == null) {
                config = MemberRiskConfig.builder().build();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取日内风控配置失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestParam(value = "memberId", required = false) Long memberId,
                                                          @RequestBody MemberRiskConfig config) {
        try {
            String key = memberId != null ? ("risk:intraday:config:member:" + memberId) : DEFAULT_CONFIG_KEY;
            redisCache.put(key, objectMapper.writeValueAsString(config));
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("保存日内风控配置失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}

