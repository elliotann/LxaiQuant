package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunResult;
import com.chain.ai.trade.engine.service.ChanLunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缠论指标 REST API
 */
@RestController
@RequestMapping("/api/member/chanlun")
@RequiredArgsConstructor
@Slf4j
public class ChanLunController {

    private final ChanLunService chanLunService;

    /**
     * 获取指定周期的缠论分析结果
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getData(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false, defaultValue = "500") Integer limit) {
        try {
            ChanLunResult result = chanLunService.getData(symbol, interval, limit);
            if (result == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "无足够K线数据或周期参数无效");
                return ResponseEntity.status(404).body(error);
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("缠论分析失败 symbol={}, interval={}", symbol, interval, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "分析失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取当前缠论配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", chanLunService.getConfig());
        return ResponseEntity.ok(resp);
    }
}
