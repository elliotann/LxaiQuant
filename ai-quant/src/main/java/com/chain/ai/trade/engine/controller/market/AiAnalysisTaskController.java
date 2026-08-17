package com.chain.ai.trade.engine.controller.market;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chain.ai.trade.engine.controller.dto.AiAnalysisTaskCreateRequest;
import com.chain.ai.trade.engine.controller.dto.AiAnalysisTaskUpdateRequest;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.entity.AiAnalysisTask;
import com.chain.ai.trade.engine.entity.AnalysisReport;
import com.chain.ai.trade.engine.service.AiAnalysisTaskService;
import com.chain.ai.trade.engine.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai/tasks")
@RequiredArgsConstructor
public class AiAnalysisTaskController {

    private final AiAnalysisTaskService taskService;

    @PostMapping
    public ApiResponse<AiAnalysisTask> createTask(@RequestBody AiAnalysisTaskCreateRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        AiAnalysisTask task = taskService.createTask(userId, req);
        return ApiResponse.success(task);
    }

    @GetMapping
    public ApiResponse<IPage<AiAnalysisTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(taskService.listTasks(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiAnalysisTask> getTask(@PathVariable String id) {
        return ApiResponse.success(taskService.getTask(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiAnalysisTask> updateTask(
            @PathVariable String id,
            @RequestBody AiAnalysisTaskUpdateRequest req) {
        return ApiResponse.success(taskService.updateTask(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable String id) {
        taskService.deleteTask(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<Void> executeTask(@PathVariable String id) {
        AiAnalysisTask task = taskService.getTask(id);
        if (task == null) {
            return ApiResponse.error(404, "Task not found");
        }
        taskService.executeTask(task);
        return ApiResponse.success(null);
    }

    @GetMapping("/reports")
    public ApiResponse<IPage<AnalysisReport>> listReports(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(taskService.listReports(taskId, page, size));
    }
}
