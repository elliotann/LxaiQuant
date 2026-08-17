package com.chain.ai.trade.engine.data.service;

import com.chain.ai.trade.engine.data.entity.dos.OptimizationTask;

public interface IOptimizationTaskService {
    OptimizationTask create(OptimizationTask task);
    boolean updateProgress(String taskId, int progress, int totalCombinations, String status);
    OptimizationTask getByTaskId(String taskId);
}
