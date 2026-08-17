package com.chain.ai.trade.engine.jobhandler;

import com.chain.ai.trade.engine.mapper.FactorMiningTaskMapper;
import com.chain.ai.trade.engine.model.ml.FactorMiningTask;
import com.chain.ai.trade.engine.service.ml.factor.FactorMiningTaskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FactorMiningJobHandler {

    private final FactorMiningTaskMapper taskMapper;
    private final FactorMiningTaskService factorMiningTaskService;

    @XxlJob("factorMiningJob")
    public void factorMiningJob() {
        log.info("因子自动挖掘定时任务开始执行");
        List<FactorMiningTask> pendingTasks = taskMapper.findByStatus("PENDING");
        for (FactorMiningTask task : pendingTasks) {
            try {
                log.info("因子挖掘任务开始执行: taskId={}, name={}", task.getId(), task.getTaskName());
                factorMiningTaskService.startTask(task.getId());
                log.info("因子挖掘任务完成: taskId={}", task.getId());
            } catch (Exception e) {
                log.error("因子挖掘任务失败: taskId={}", task.getId(), e);
            }
        }
        log.info("因子自动挖掘定时任务执行完毕");
    }
}
