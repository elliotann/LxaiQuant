package com.chain.ai.trade.engine.data.service;

import com.chain.ai.trade.engine.data.entity.dos.OptimizationResult;
import java.util.List;

public interface IOptimizationResultService {
    void saveBatch(List<OptimizationResult> results);
    List<OptimizationResult> listByTaskId(String taskId, int limit);
}
