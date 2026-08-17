package com.chain.ai.trade.engine.controller.ml;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.mapper.AutoSearchResultMapper;
import com.chain.ai.trade.engine.model.ml.ApplyFeatureRequest;
import com.chain.ai.trade.engine.model.ml.AutoSearchRequest;
import com.chain.ai.trade.engine.model.ml.AutoSearchResult;
import com.chain.ai.trade.engine.service.ml.AutoSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ml/auto-search")
@RequiredArgsConstructor
public class AutoSearchController {

    private final AutoSearchService autoSearchService;
    private final AutoSearchResultMapper autoSearchResultMapper;

    @GetMapping("/feature-pool")
    public ApiResponse<Map<String, Object>> getFeaturePool() {
        return ApiResponse.success(autoSearchService.getFeaturePoolInfo());
    }

    @PostMapping
    public ApiResponse<AutoSearchResult> startSearch(@RequestBody AutoSearchRequest request) {
        AutoSearchResult result = autoSearchService.startSearch(request);
        return ApiResponse.success("搜索任务已启动", result);
    }

    @GetMapping("/{searchId}")
    public ApiResponse<AutoSearchResult> getProgress(@PathVariable String searchId) {
        AutoSearchResult result = autoSearchResultMapper.findBySearchId(searchId);
        if (result == null) {
            return ApiResponse.error(404, "搜索任务不存在");
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/stop/{searchId}")
    public ApiResponse<String> stopSearch(@PathVariable String searchId) {
        autoSearchService.stopSearch(searchId);
        return ApiResponse.success("停止指令已发送");
    }

    @PostMapping("/apply")
    public ApiResponse<String> applyFeature(@RequestBody ApplyFeatureRequest request) {
        if (request.getSymbol() == null || request.getFeatureNames() == null || request.getFeatureNames().isEmpty()) {
            return ApiResponse.error(400, "参数不完整");
        }
        String config = autoSearchService.applyFeatureCombination(request.getSymbol(), request.getFeatureNames());
        return ApiResponse.success("特征组合已应用", config);
    }

    @GetMapping("/history/{symbol}")
    public ApiResponse<List<AutoSearchResult>> getHistory(@PathVariable String symbol) {
        return ApiResponse.success(autoSearchResultMapper.findBySymbol(symbol));
    }
}
