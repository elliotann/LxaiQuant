package com.chain.ai.trade.backtest.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.backtest.entity.dos.BacktestTask;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestTaskDTO;

import java.util.List;

/**
 * 回测任务服务主要接口
 * 提供给外部模块调用的主要业务接口
 */
public interface BacktestTaskService {

    /**
     * 根据 taskId 获取任务实体
     * @param taskId 任务ID
     * @return 任务实体
     */
    BacktestTask getById(String taskId);

    /**
     * 创建回测任务
     * @param taskDTO 任务参数
     * @return 创建的任务DTO
     */
    BacktestTaskDTO createTask(BacktestTaskDTO taskDTO);

    /**
     * 创建回测任务（别名方法）
     * @param taskDTO 任务参数
     * @return 创建的任务DTO
     */
    default BacktestTaskDTO createBacktestTask(BacktestTaskDTO taskDTO) {
        return createTask(taskDTO);
    }

    /**
     * 执行回测任务
     * @param taskId 任务ID
     * @return 执行结果
     */
    boolean executeBacktestTask(String taskId);

    /**
     * 保存回测结果
     * @param resultDTO 结果数据
     * @return 保存是否成功
     */
    boolean saveBacktestResult(BacktestResultDTO resultDTO);

    /**
     * 获取回测结果
     * @param taskId 任务ID
     * @return 回测结果
     */
    BacktestResultDTO getBacktestResult(String taskId);

    /**
     * 更新任务状态
     * @param taskId 任务ID
     * @param status 状态
     * @param errorMessage 错误信息（可选）
     * @return 更新是否成功
     */
    boolean updateTaskStatus(String taskId, String status, String errorMessage);

    /**
     * 更新任务配置
     * @param taskId 任务ID
     * @param config 配置内容
     * @return 更新是否成功
     */
    boolean updateTaskConfig(String taskId, java.util.Map<String, Object> config);

    /**
     * 获取任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    BacktestTaskDTO getTaskDetail(String taskId);

    /**
     * 获取任务列表
     * @param createdBy 创建者
     * @param status 状态
     * @return 任务列表
     */
    List<BacktestTaskDTO> getTaskList(String createdBy, String status);

    /**
     * 分页查询任务列表
     * @param page 页码
     * @param size 每页大小
     * @param createdBy 创建者（可选）
     * @param status 状态（可选）
     * @param strategyCode 策略编码（可选）
     * @return 分页结果
     */
    Page<BacktestTaskDTO> getTaskListPaged(Integer page, Integer size, String createdBy, String status, String strategyCode);

    /**
     * 删除回测任务
     * @param taskId 任务ID
     * @return 删除是否成功
     */
    boolean deleteTask(String taskId);

    /**
     * 删除回测任务及相关数据（别名方法）
     * @param taskId 任务ID
     * @return 删除是否成功
     */
    default boolean deleteBacktestTask(String taskId) {
        return deleteTask(taskId);
    }
}
