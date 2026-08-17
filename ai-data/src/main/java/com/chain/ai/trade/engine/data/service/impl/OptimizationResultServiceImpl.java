package com.chain.ai.trade.engine.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chain.ai.trade.engine.data.entity.dos.OptimizationResult;
import com.chain.ai.trade.engine.data.service.IOptimizationResultService;
import com.chain.ai.trade.engine.mapper.OptimizationResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimizationResultServiceImpl implements IOptimizationResultService {
    private final OptimizationResultMapper resultMapper;

    @Override
    public void saveBatch(List<OptimizationResult> results) {
        Date now = new Date();
        for (OptimizationResult r : results) {
            r.setCreatedAt(now);
            resultMapper.insert(r);
        }
    }

    @Override
    public List<OptimizationResult> listByTaskId(String taskId, int limit) {
        return resultMapper.selectList(new QueryWrapper<OptimizationResult>()
                .eq("task_id", taskId)
                .orderByDesc("score")
                .last("limit " + limit));
    }
}
