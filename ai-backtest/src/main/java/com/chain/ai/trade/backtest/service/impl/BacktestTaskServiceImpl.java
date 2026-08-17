package com.chain.ai.trade.backtest.service.impl;

import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestTaskDTO;
import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.chain.ai.trade.backtest.service.BacktestResultService;
import com.chain.ai.trade.backtest.mapper.BacktestTaskMapper;
import com.chain.ai.trade.backtest.entity.dos.BacktestTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.backtest.entity.dos.BacktestTask;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 回测任务服务实现
 * 提供主要的回测业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestTaskServiceImpl implements BacktestTaskService {

    private final com.chain.ai.trade.backtest.mapper.BacktestTaskMapper backtestTaskMapper;
    private final BacktestResultService backtestResultService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public BacktestTaskDTO createTask(BacktestTaskDTO taskDTO) {
        log.info("开始创建回测任务: inputTaskId={}", taskDTO.getTaskId());
        // 生成任务ID
        String taskId = "TASK_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        log.info("生成新任务ID: newTaskId={}", taskId);

        BacktestTask task = BacktestTask.builder()
                .taskId(taskId)
                .strategyName(taskDTO.getStrategyName())
                .strategyCode(taskDTO.getStrategyCode())
                .strategyVersion(taskDTO.getStrategyVersion())
                .startDate(taskDTO.getStartDate())
                .endDate(taskDTO.getEndDate())
                .initialCapital(taskDTO.getInitialCapital())
                .currency(taskDTO.getCurrency())
                .benchmark(taskDTO.getBenchmark())
                .status("PENDING")
                .progress(0)
                .createdBy(taskDTO.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .partitionKey(taskDTO.getPartitionKey() != null ? taskDTO.getPartitionKey() : java.time.LocalDate.now())
                .robotId(taskDTO.getRobotId())
                .memberId(taskDTO.getMemberId())
                .accountId(taskDTO.getAccountId())
                .leverage(taskDTO.getLeverage())
                .build();

        // 序列化配置
        if (taskDTO.getConfig() != null) {
            try {
                task.setConfig(objectMapper.writeValueAsString(taskDTO.getConfig()));
            } catch (Exception e) {
                log.error("序列化任务配置失败", e);
                task.setConfig("{}");
            }
        }

        // 序列化universe数组
        if (taskDTO.getUniverse() != null) {
            try {
                task.setUniverse(objectMapper.writeValueAsString(taskDTO.getUniverse()));
            } catch (Exception e) {
                log.error("序列化universe失败", e);
                task.setUniverse("[]");
            }
        }

        log.info("正在保存任务到数据库: taskId={}", taskId);
        backtestTaskMapper.insert(task);
        log.info("任务保存成功: taskId={}", taskId);
        BacktestTaskDTO result = convertToDTO(task);
        log.info("转换DTO完成: taskId={}", result.getTaskId());
        return result;
    }

    @Override
    public BacktestTask getById(String taskId) {
        return backtestTaskMapper.selectOne(
                new LambdaQueryWrapper<BacktestTask>()
                        .eq(BacktestTask::getTaskId, taskId));
    }

    @Override
    public boolean executeBacktestTask(String taskId) {
        log.info("开始执行回测任务: {}", taskId);

        try {
            // 更新任务状态为运行中
            boolean statusUpdated = updateTaskStatus(taskId, "RUNNING", null);
            if (!statusUpdated) {
                log.error("更新任务状态失败: {}", taskId);
                return false;
            }

            // TODO: 这里应该调用实际的回测引擎执行逻辑
            // 目前暂时模拟执行过程

            // 模拟执行时间
            Thread.sleep(1000);

            // 模拟生成结果数据
            // 注意：如果有真实的 BacktestEngine.BacktestResult（包含 TradingRecord 和 BarSeries），
            // 应该调用 backtestResultService.calculateEquityCurveAndDrawdown(barSeries, tradingRecord, initialAmount)
            // 来计算权益曲线和回撤序列，然后设置到 resultDTO 中
            BacktestResultDTO mockResult = BacktestResultDTO.builder()
                    .taskId(taskId)
                    .strategyName("TREND_LINE_BOTH")
                    .totalReturn(BigDecimal.valueOf(0.156789))  // 15.68%
                    .maxDrawdown(BigDecimal.valueOf(-0.082345)) // -8.23%
                    .totalTrades(156)
                    .winningTrades(81)                           // 52.34% 的胜率
                    .winRate(BigDecimal.valueOf(0.5234))        // 52.34%
                    .profitFactor(BigDecimal.valueOf(1.85))     // 盈亏比
                    .finalValue(BigDecimal.valueOf(11567.89))   // 最终价值
                    // equityCurve 和 drawdownSeries 应该在有真实回测数据时通过 calculateEquityCurveAndDrawdown 方法计算
                    .build();

            // 保存结果
            boolean resultSaved = backtestResultService.saveResult(mockResult);
            if (!resultSaved) {
                log.error("保存回测结果失败: {}", taskId);
                updateTaskStatus(taskId, "FAILED", "保存结果失败");
                return false;
            }

            // 更新任务状态为完成
            updateTaskStatus(taskId, "COMPLETED", null);

            log.info("回测任务执行完成: {}", taskId);
            return true;

        } catch (Exception e) {
            log.error("执行回测任务失败: {}", taskId, e);
            updateTaskStatus(taskId, "FAILED", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean saveBacktestResult(BacktestResultDTO resultDTO) {
        log.info("保存回测结果: {}", resultDTO.getTaskId());
        return backtestResultService.saveResult(resultDTO);
    }

    @Override
    public BacktestResultDTO getBacktestResult(String taskId) {
        log.info("获取回测结果: {}", taskId);
        return backtestResultService.getResult(taskId);
    }

    @Override
    public boolean updateTaskStatus(String taskId, String status, String errorMessage) {
        // 使用task_id字段查询
        LambdaQueryWrapper<BacktestTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BacktestTask::getTaskId, taskId);
        BacktestTask task = backtestTaskMapper.selectOne(wrapper);
        if (task == null) {
            return false;
        }

        task.setStatus(status);
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }

        if ("RUNNING".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        } else if (("COMPLETED".equals(status) || "FAILED".equals(status)) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
            if (task.getStartedAt() != null) {
                task.setDurationSeconds((int) java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).getSeconds());
            }
        }

        return backtestTaskMapper.updateById(task) > 0;
    }

    @Override
    public boolean updateTaskConfig(String taskId, java.util.Map<String, Object> config) {
        LambdaQueryWrapper<BacktestTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BacktestTask::getTaskId, taskId);
        BacktestTask task = backtestTaskMapper.selectOne(wrapper);
        if (task == null) {
            return false;
        }
        try {
            task.setConfig(objectMapper.writeValueAsString(config != null ? config : java.util.Collections.emptyMap()));
        } catch (Exception e) {
            log.error("序列化任务配置失败", e);
            return false;
        }
        return backtestTaskMapper.updateById(task) > 0;
    }

    @Override
    public BacktestTaskDTO getTaskDetail(String taskId) {
        log.info("查询任务详情: taskId={}", taskId);

        // 使用task_id字段查询，而不是id字段
        LambdaQueryWrapper<BacktestTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BacktestTask::getTaskId, taskId);

        BacktestTask task = backtestTaskMapper.selectOne(wrapper);
        log.info("数据库查询结果: task={}, taskId={}", task != null ? "found" : "null", taskId);
        return task != null ? convertToDTO(task) : null;
    }

    @Override
    public List<BacktestTaskDTO> getTaskList(String createdBy, String status) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BacktestTask> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (createdBy != null) {
            wrapper.eq("created_by", createdBy);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");

        List<BacktestTask> tasks = backtestTaskMapper.selectList(wrapper);
        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public Page<BacktestTaskDTO> getTaskListPaged(Integer page, Integer size, String createdBy, String status, String strategyCode) {
        log.info("分页查询任务列表: page={}, size={}, createdBy={}, status={}, strategyCode={}", page, size, createdBy, status, strategyCode);

        // 创建分页对象
        Page<BacktestTask> pageObj = new Page<>(page, size);

        // 创建查询条件
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BacktestTask> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (createdBy != null && !createdBy.trim().isEmpty()) {
            wrapper.eq("created_by", createdBy);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status);
        }
        if (strategyCode != null && !strategyCode.trim().isEmpty()) {
            wrapper.eq("strategy_code", strategyCode);
        }
        wrapper.orderByDesc("created_at");

        // 执行分页查询
        Page<BacktestTask> result = backtestTaskMapper.selectPage(pageObj, wrapper);

        // 转换为DTO分页结果
        Page<BacktestTaskDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(result.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));

        log.info("分页查询完成: 总记录数={}, 当前页={}, 每页大小={}, 页数={}",
                dtoPage.getTotal(), dtoPage.getCurrent(), dtoPage.getSize(), dtoPage.getPages());

        return dtoPage;
    }

    @Override
    public boolean deleteTask(String taskId) {
        // 使用task_id字段删除
        LambdaQueryWrapper<BacktestTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BacktestTask::getTaskId, taskId);
        return backtestTaskMapper.delete(wrapper) > 0;
    }

    private BacktestTaskDTO convertToDTO(BacktestTask task) {
        BacktestTaskDTO dto = BacktestTaskDTO.builder()
                .id(task.getId())
                .taskId(task.getTaskId())
                .strategyName(task.getStrategyName())
                .strategyCode(task.getStrategyCode())
                .strategyVersion(task.getStrategyVersion())
                .startDate(task.getStartDate())
                .endDate(task.getEndDate())
                .initialCapital(task.getInitialCapital())
                .currency(task.getCurrency())
                .benchmark(task.getBenchmark())
                .status(task.getStatus())
                .progress(task.getProgress())
                .errorMessage(task.getErrorMessage())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .durationSeconds(task.getDurationSeconds())
                .partitionKey(task.getPartitionKey())
                .build();

        // 反序列化配置
        if (task.getConfig() != null) {
            try {
                dto.setConfig(objectMapper.readValue(task.getConfig(), java.util.Map.class));
            } catch (Exception e) {
                log.error("反序列化任务配置失败", e);
                dto.setConfig(new java.util.HashMap<>());
            }
        }

        // 反序列化universe
        if (task.getUniverse() != null) {
            try {
                dto.setUniverse(objectMapper.readValue(task.getUniverse(), String[].class));
            } catch (Exception e) {
                log.error("反序列化universe失败", e);
                dto.setUniverse(new String[0]);
            }
        }

        return dto;
    }
}
