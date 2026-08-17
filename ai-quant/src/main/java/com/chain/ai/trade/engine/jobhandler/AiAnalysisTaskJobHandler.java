package com.chain.ai.trade.engine.jobhandler;

import com.chain.ai.trade.engine.entity.AiAnalysisTask;
import com.chain.ai.trade.engine.service.AiAnalysisTaskService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisTaskJobHandler {

    private final AiAnalysisTaskService taskService;

    @XxlJob("aiAnalysisTaskScan")
    public void scanAndExecute() {
        int success = 0;
        int failed = 0;

        try {
            List<AiAnalysisTask> dueTasks = taskService.getDueTasks();
            log.info("AI分析任务扫描: 到期任务数={}", dueTasks.size());

            for (AiAnalysisTask task : dueTasks) {
                try {
                    taskService.executeTask(task);
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.error("AI分析任务执行失败: taskId={}", task.getId(), e);
                }
            }

            String result = String.format("scan=%d success=%d failed=%d", dueTasks.size(), success, failed);
            log.info("AI分析任务扫描完成: {}", result);
            XxlJobHelper.handleSuccess(result);
        } catch (Exception e) {
            log.error("AI分析任务扫描异常", e);
            XxlJobHelper.handleFail("failed: " + e.getMessage());
        }
    }

    @XxlJob("aiAnalysisTaskManual")
    public void manualExecute() {
        String jobParam = XxlJobHelper.getJobParam();
        if (jobParam == null || jobParam.isBlank()) {
            XxlJobHelper.handleFail("任务ID参数不能为空");
            return;
        }

        String taskId = jobParam.trim();
        AiAnalysisTask task = taskService.getTask(taskId);
        if (task == null) {
            XxlJobHelper.handleFail("任务不存在: " + taskId);
            return;
        }

        try {
            taskService.executeTask(task);
            XxlJobHelper.handleSuccess("手动执行完成: " + taskId);
        } catch (Exception e) {
            log.error("手动执行AI分析任务失败: taskId={}", taskId, e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }
}
