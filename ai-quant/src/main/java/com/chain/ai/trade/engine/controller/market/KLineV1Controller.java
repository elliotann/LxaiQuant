package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.controller.dto.*;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.controller.dto.KLineJumpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * K线数据V1版本控制器
 * 提供新的REST API接口，旧接口保持不变
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/kline")
@RequiredArgsConstructor
@Validated
public class KLineV1Controller {
    
    private final KLineV1Service kLineV1Service;
    private static final String DEFAULT_INTERVAL = "OKXMIN15";
    
    /**
     * 获取K线历史数据
     */
    @GetMapping("/history")
    public ApiResponse<KLineHistoryResponse> getKLineHistory(@Valid KLineHistoryRequest request) {
        log.info("获取K线历史数据: symbol={}, interval={}, limit={}", 
                request.getSymbol(), request.getInterval(), request.getLimit());
        
        KLineHistoryResponse response = kLineV1Service.getKLineHistory(request);
        return ApiResponse.success(response);
    }
    
    /**
     * 按方向加载更多数据
     */
    @PostMapping("/load")
    public ApiResponse<KLineLoadResponse> loadKLineData(@Valid @RequestBody KLineLoadRequest request) {
        log.info("加载K线数据: symbol={}, interval={}, direction={}, anchorTime={}", 
                request.getSymbol(), request.getInterval(), request.getDirection(), request.getAnchorTime());
        
        KLineLoadResponse response = kLineV1Service.loadKLineData(request);
        return ApiResponse.success(response);
    }
    
    /**
     * 批量加载数据
     */
    @PostMapping("/load/batch")
    public ApiResponse<Map<String, KLineLoadResponse>> loadKLineDataBatch(
            @Valid @RequestBody List<KLineLoadRequest> requests) {
        
        log.info("批量加载K线数据: count={}", requests.size());
        
        Map<String, KLineLoadResponse> responses = kLineV1Service.loadKLineDataBatch(requests);
        return ApiResponse.success(responses);
    }
    
    /**
     * 计算技术指标
     * TODO: 待实现
     */
    @PostMapping("/indicator")
    public ApiResponse<Map<String, Object>> calculateIndicator(@RequestBody Map<String, Object> request) {
        log.info("计算技术指标: {}", request);
        
        // TODO: 实现技术指标计算
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("indicatorType", request.get("indicatorType"));
        response.put("data", new java.util.ArrayList<>());
        
        return ApiResponse.success(response);
    }
    
    /**
     * 获取支持的交易对
     */
    @GetMapping("/symbols")
    public ApiResponse<List<String>> getSupportedSymbols() {
        List<String> symbols = kLineV1Service.getSupportedSymbols();
        return ApiResponse.success(symbols);
    }

    /**
     * 获取支持的交易对详情（code + name）
     */
    @GetMapping("/symbols/details")
    public ApiResponse<List<SymbolInfoDTO>> getSupportedSymbolDetails() {
        List<SymbolInfoDTO> symbols = kLineV1Service.getSupportedSymbolDetails();
        return ApiResponse.success(symbols);
    }

    /**
     * 搜索交易对
     */
    @GetMapping("/symbol/search")
    public ApiResponse<List<String>> searchSymbols(@RequestParam String keyword) {
        List<String> symbols = kLineV1Service.searchSymbols(keyword);
        return ApiResponse.success(symbols);
    }
    
    /**
     * 获取服务器时间
     */
    @GetMapping("/server-time")
    public ApiResponse<Long> getServerTime() {
        return ApiResponse.success(System.currentTimeMillis() / 1000);
    }
    
    /**
     * 时间跳转 - 跳转到指定时间点
     */
    @PostMapping("/jump")
    public ApiResponse<KLineJumpResponse> jumpToTime(@Valid @RequestBody KLineJumpRequest request) {
        log.info("时间跳转请求: symbol={}, interval={}, time={}, before={}, after={}", 
                request.getSymbol(), request.getInterval(), request.getTime(), 
                request.getBefore(), request.getAfter());
        
        KLineJumpResponse response = kLineV1Service.jumpToTime(request);
        return ApiResponse.success(response);
    }
    
    /**
     * 获取K线信号标注
     */
    @PostMapping("/signals")
    public ApiResponse<KLineSignalResponse> getKLineSignals(@Valid @RequestBody KLineSignalRequest request) {
        log.debug("获取K线信号标注: symbol={}, interval={}, from={}, to={}",
                request.getSymbol(), request.getInterval(), request.getFrom(), request.getTo());
        
        KLineSignalResponse response = kLineV1Service.getKLineSignals(request);
        return ApiResponse.success(response);
    }

    /**
     * 从交易所导入历史K线到数据库（对接数据导入菜单的 API 导入）
     */
    @PostMapping("/import-from-exchange")
    public ApiResponse<KLineImportFromExchangeResponse> importFromExchange(
            @Valid @RequestBody KLineImportFromExchangeRequest request) {
        log.info("API导入K线: exchange={}, symbol={}, interval={}, startTime={}, endTime={}",
                request.getExchange(), request.getSymbol(), request.getInterval(),
                request.getStartTime(), request.getEndTime());
        KLineImportFromExchangeResponse response = kLineV1Service.importFromExchange(request);
        if (response == null || !response.isSuccess()) {
            return ApiResponse.error(400, response != null ? response.getMessage() : "导入失败");
        }
        return ApiResponse.success(response);
    }

    /**
     * 实时交易滚动条用：返回所有币种的最新K线Ticker
     */
    @GetMapping("/latest-tickers")
    public ApiResponse<List<TickerDTO>> getLatestTickers(
            @RequestParam(value = "interval", required = false) String interval,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        String usedInterval = interval == null || interval.isBlank() ? DEFAULT_INTERVAL : interval;
        List<TickerDTO> list = kLineV1Service.getLatestTickers(usedInterval, limit);
        return ApiResponse.success(list);
    }
}

