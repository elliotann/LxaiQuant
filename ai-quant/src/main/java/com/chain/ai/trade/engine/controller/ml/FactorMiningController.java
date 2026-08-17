package com.chain.ai.trade.engine.controller.ml;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.model.ml.FactorCandidateVO;
import com.chain.ai.trade.engine.model.ml.FactorMiningRequest;
import com.chain.ai.trade.engine.model.ml.FactorMiningTask;
import com.chain.ai.trade.engine.service.ml.factor.FactorMiningTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ml/factor-mining")
@RequiredArgsConstructor
public class FactorMiningController {

    private final FactorMiningTaskService factorMiningTaskService;

    @GetMapping("/terminal-pool")
    public ApiResponse<Map<String, Object>> getTerminalPool(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1H") String interval) {
        return ApiResponse.success(factorMiningTaskService.getTerminalPool(symbol, interval));
    }

    @PostMapping("/tasks")
    public ApiResponse<FactorMiningTask> createTask(@RequestBody FactorMiningRequest request) {
        FactorMiningTask task = factorMiningTaskService.createTask(request);
        return ApiResponse.success(task);
    }

    @PostMapping("/tasks/{taskId}/start")
    public ApiResponse<String> startTask(@PathVariable String taskId) {
        CompletableFuture<Void> future = factorMiningTaskService.startTaskAsync(taskId);
        return ApiResponse.success("task started: " + taskId);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<FactorMiningTask> getTask(@PathVariable String taskId) {
        return ApiResponse.success(factorMiningTaskService.getTaskById(taskId));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<FactorMiningTask>> getRecentTasks(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(factorMiningTaskService.getRecentTasks(limit));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<String> cancelTask(@PathVariable String taskId) {
        factorMiningTaskService.cancelTask(taskId);
        return ApiResponse.success("task cancelled: " + taskId);
    }

    @GetMapping("/candidates/{taskId}")
    public ApiResponse<List<FactorCandidateVO>> getCandidates(@PathVariable String taskId) {
        return ApiResponse.success(factorMiningTaskService.getCandidatesByTaskId(taskId));
    }

    @GetMapping("/candidates/selected")
    public ApiResponse<List<FactorCandidateVO>> getSelectedCandidates() {
        return ApiResponse.success(factorMiningTaskService.getSelectedCandidates());
    }

    @PostMapping("/candidates/{candidateId}/select")
    public ApiResponse<String> selectCandidate(
            @PathVariable String candidateId,
            @RequestParam String customFeatureName) {
        factorMiningTaskService.selectCandidate(candidateId, customFeatureName);
        return ApiResponse.success("candidate selected: " + candidateId);
    }

    @PostMapping("/candidates/{candidateId}/deselect")
    public ApiResponse<String> deselectCandidate(@PathVariable String candidateId) {
        factorMiningTaskService.deselectCandidate(candidateId);
        return ApiResponse.success("candidate deselected: " + candidateId);
    }
}
