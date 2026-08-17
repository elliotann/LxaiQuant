package com.chain.ai.trade.engine.risk.evaluator;

import com.chain.ai.trade.engine.entity.dto.AnalysisData;
import com.chain.ai.trade.engine.risk.common.TimeFrame;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 评估上下文
 */
@Data
@Builder
public class EvaluationContext {
    private String symbol;                // 交易对
    private TimeFrame timeFrame;          // 时间框架
    private AnalysisData analysisData;    // 分析数据（K线、指标等）
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>(); // 其他元数据
    
    // 多周期数据支持
    private boolean multiTimeFrameEnabled; // 是否启用多周期分析
    @Builder.Default
    private Map<TimeFrame, AnalysisData> multiTimeFrameData = new HashMap<>(); // 多周期分析数据
    
    /**
     * 检查是否有多周期数据
     */
    public boolean hasMultiTimeFrameData() {
        return multiTimeFrameData != null && !multiTimeFrameData.isEmpty();
    }
    
    /**
     * 获取多周期数据
     */
    public Map<TimeFrame, AnalysisData> getMultiTimeFrameData() {
        return multiTimeFrameData;
    }
    
    /**
     * 添加多周期数据
     */
    public void addMultiTimeFrameData(TimeFrame timeFrame, AnalysisData data) {
        if (multiTimeFrameData == null) {
            multiTimeFrameData = new HashMap<>();
        }
        multiTimeFrameData.put(timeFrame, data);
    }
}

