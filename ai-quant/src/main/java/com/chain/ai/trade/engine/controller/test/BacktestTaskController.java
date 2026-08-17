package com.chain.ai.trade.engine.controller.test;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.backtest.entity.dto.BacktestTaskDTO;
import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.chain.ai.trade.common.entity.dto.ApiResponse;

import com.chain.ai.trade.order.entity.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 回测任务管理控制器
 * 提供回测任务的分页查询和管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest/tasks")
@ConditionalOnClass(name = "com.chain.ai.trade.backtest.service.BacktestTaskService")
@RequiredArgsConstructor
public class BacktestTaskController {

    private final BacktestTaskService backtestTaskService;

    /**
     * 分页查询回测任务列表
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param createdBy 创建者（可选）
     * @param status 任务状态（可选）
     * @param strategyCode 策略编码（可选）
     * @return 分页结果
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<PageVO<BacktestTaskDTO>>> getTaskListPaged(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategyCode) {

        log.info("收到分页查询请求: page={}, size={}, createdBy={}, status={}, strategyCode={}", page, size, createdBy, status, strategyCode);

        try {
            // 参数校验
            if (page < 1) {
                page = 1;
            }
            if (size < 1 || size > 100) {
                size = 10;
            }

            // 调用服务层方法
            Page<BacktestTaskDTO> result = backtestTaskService.getTaskListPaged(page, size, createdBy, status, strategyCode);

            // 构建分页VO
            PageVO<BacktestTaskDTO> pageVO = PageVO.<BacktestTaskDTO>builder()
                    .current(result.getCurrent())
                    .size(result.getSize())
                    .total(result.getTotal())
                    .pages(result.getPages())
                    .hasNext(result.hasNext())
                    .hasPrevious(result.hasPrevious())
                    .records(result.getRecords())
                    .build();

            log.info("分页查询完成: 总记录数={}, 当前页={}, 每页大小={}, 总页数={}",
                    result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());

            return ResponseEntity.ok(ApiResponse.success("查询成功", pageVO));

        } catch (Exception e) {
            log.error("分页查询回测任务失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取单个回测任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<BacktestTaskDTO>> getTaskDetail(@PathVariable String taskId) {
        log.info("查询任务详情: taskId={}", taskId);

        try {
            BacktestTaskDTO task = backtestTaskService.getTaskDetail(taskId);

            if (task != null) {
                return ResponseEntity.ok(ApiResponse.success("查询成功", task));
            } else {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "任务不存在"));
            }

        } catch (Exception e) {
            log.error("查询任务详情失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 删除回测任务
     * @param taskId 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String taskId) {
        log.info("删除任务: taskId={}", taskId);

        try {
            boolean success = backtestTaskService.deleteTask(taskId);

            if (success) {
                return ResponseEntity.ok(ApiResponse.success("删除成功", null));
            } else {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "删除失败，任务不存在"));
            }

        } catch (Exception e) {
            log.error("删除任务失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }
}
