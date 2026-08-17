package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.engine.controller.dto.MarketAnalysisBatchRequest;
import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.MarketAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trading/market-analysis")
@RequiredArgsConstructor
public class MarketAnalysisController {
    private final MarketAnalysisService marketAnalysisService;

    @GetMapping
    public ApiResponse<MarketAnalysisDTO> analyze(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "3m") String interval,
            @RequestParam(required = false) Integer limit
    ) {
        try {
            MarketAnalysisDTO dto = marketAnalysisService.analyze(symbol, interval, limit);
            if (dto == null) return ApiResponse.error(404, "无足够本地K线数据，无法分析");
            return ApiResponse.success("OK", dto);
        } catch (Exception e) {
            log.error("市场分析失败: symbol={}, interval={}", symbol, interval, e);
            return ApiResponse.error("市场分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResponse<List<MarketAnalysisDTO>> analyzeBatch(@RequestBody MarketAnalysisBatchRequest req) {
        try {
            List<String> symbols = req != null ? req.getSymbols() : null;
            String interval = req != null ? req.getInterval() : null;
            Integer limit = req != null ? req.getLimit() : null;
            if (symbols == null || symbols.isEmpty()) {
                return ApiResponse.error(400, "symbols不能为空");
            }
            int take = Math.min(40, symbols.size());
            List<MarketAnalysisDTO> out = new ArrayList<>();
            for (int i = 0; i < take; i++) {
                String s = symbols.get(i);
                if (s == null || s.isBlank()) continue;
                MarketAnalysisDTO dto = marketAnalysisService.analyze(s, interval, limit);
                if (dto != null) out.add(dto);
            }
            return ApiResponse.success("OK", out);
        } catch (Exception e) {
            log.error("批量市场分析失败", e);
            return ApiResponse.error("批量市场分析失败: " + e.getMessage());
        }
    }
}

