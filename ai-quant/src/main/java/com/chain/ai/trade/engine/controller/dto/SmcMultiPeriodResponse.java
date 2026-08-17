package com.chain.ai.trade.engine.controller.dto;

import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.engine.data.entity.dto.smc.MultiPeriodSmcData;
import lombok.Data;

import java.util.List;

@Data
public class SmcMultiPeriodResponse {
    private String symbol;
    private List<MatrixItem> matrix;
    private CoreData core;
    private List<CriticalLevel> criticalLevels;

    // ★ 新增：结构评估数据（波次、位置比、混沌特例等）
    private MultiPeriodSmcData structureData;

    @Data
    public static class MatrixItem {
        private String period;
        private String direction;
    }

    @Data
    public static class CoreData {
        private String institutionResonance;
        private String marketGenre;
        private String trendState;
        /** 21种复合状态（看板展示用，独立于 trendState） */
        private String compositeState;
        private String updateTime;
    }
}
