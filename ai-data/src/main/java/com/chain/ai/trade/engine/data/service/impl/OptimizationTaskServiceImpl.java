package com.chain.ai.trade.engine.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chain.ai.trade.engine.data.entity.dos.OptimizationTask;
import com.chain.ai.trade.engine.data.service.IOptimizationTaskService;
import com.chain.ai.trade.engine.mapper.OptimizationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class OptimizationTaskServiceImpl implements IOptimizationTaskService {
    private final OptimizationTaskMapper taskMapper;

    @Override
    public OptimizationTask create(OptimizationTask task) {
        task.setCreatedAt(new Date());
        task.setUpdatedAt(task.getCreatedAt());
        taskMapper.insert(task);
        return task;
    }

    @Override
    public boolean updateProgress(String taskId, int progress, int totalCombinations, String status) {
        OptimizationTask existing = getByTaskId(taskId);
        if (existing == null) return false;
        existing.setProgress(progress);
        existing.setTotalCombinations(totalCombinations);
        existing.setStatus(status);
        existing.setUpdatedAt(new Date());
        return taskMapper.updateById(existing) > 0;
        }

    @Override
    public OptimizationTask getByTaskId(String taskId) {
        return taskMapper.selectOne(new QueryWrapper<OptimizationTask>().eq("task_id", taskId));
    }
}
