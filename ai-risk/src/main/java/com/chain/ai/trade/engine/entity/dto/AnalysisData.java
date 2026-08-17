package com.chain.ai.trade.engine.entity.dto;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析数据（包含K线、指标等）
 */
@Data
@Builder
public class AnalysisData {
    @Builder.Default
    private List<Candlestick> bars = new ArrayList<>(); // K线数据
    @Builder.Default
    private Map<String, Object> indicators = new HashMap<>(); // 技术指标数据
    @Builder.Default
    private Map<String, Object> marketData = new HashMap<>(); // 市场数据（成交量、持仓量等）
}

