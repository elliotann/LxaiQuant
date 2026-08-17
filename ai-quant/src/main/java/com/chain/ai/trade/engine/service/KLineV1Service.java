package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import com.chain.ai.trade.engine.controller.dto.KLineLoadRequest;
import com.chain.ai.trade.engine.controller.dto.KLineLoadResponse;
import com.chain.ai.trade.engine.controller.dto.KLineJumpRequest;
import com.chain.ai.trade.engine.controller.dto.KLineJumpResponse;
import com.chain.ai.trade.engine.controller.dto.KLineImportFromExchangeRequest;
import com.chain.ai.trade.engine.controller.dto.KLineImportFromExchangeResponse;
import com.chain.ai.trade.engine.controller.dto.KLineSignalRequest;
import com.chain.ai.trade.engine.controller.dto.KLineSignalResponse;
import com.chain.ai.trade.engine.controller.dto.TickerDTO;
import com.chain.ai.trade.engine.controller.dto.SymbolInfoDTO;

import java.util.List;
import java.util.Map;

/**
 * K线数据V1版本服务接口
 */
public interface KLineV1Service {
    
    /**
     * 获取K线历史数据
     */
    KLineHistoryResponse getKLineHistory(KLineHistoryRequest request);
    
    /**
     * 按方向加载更多数据
     */
    KLineLoadResponse loadKLineData(KLineLoadRequest request);
    
    /**
     * 批量加载数据
     */
    Map<String, KLineLoadResponse> loadKLineDataBatch(List<KLineLoadRequest> requests);
    
    /**
     * 获取支持的交易对（仅 code 列表）
     */
    List<String> getSupportedSymbols();
    
    /**
     * 获取支持的交易对详情（code + name）
     */
    List<SymbolInfoDTO> getSupportedSymbolDetails();

    /**
     * 搜索交易对
     */
    List<String> searchSymbols(String keyword);
    
    /**
     * 时间跳转 - 跳转到指定时间点
     */
    KLineJumpResponse jumpToTime(KLineJumpRequest request);
    
    /**
     * 获取K线信号标注
     */
    KLineSignalResponse getKLineSignals(KLineSignalRequest request);

    /**
     * 从交易所导入历史K线到数据库
     */
    KLineImportFromExchangeResponse importFromExchange(KLineImportFromExchangeRequest request);

    /**
     * 获取所有币种的最新K线Ticker
     */
    List<TickerDTO> getLatestTickers(String interval, Integer limit);
}

