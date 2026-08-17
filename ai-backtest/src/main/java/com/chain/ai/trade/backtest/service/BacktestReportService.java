package com.chain.ai.trade.backtest.service;

import com.chain.ai.trade.backtest.entity.dto.BacktestReportDTO;

/**
 * 回测报告服务接口
 */
public interface BacktestReportService {

    /**
     * 生成回测报告
     * @param taskId 任务ID
     * @return 报告DTO
     */
    BacktestReportDTO generateReport(String taskId);

    /**
     * 获取回测报告
     * @param taskId 任务ID
     * @return 报告DTO
     */
    BacktestReportDTO getReport(String taskId);

    /**
     * 保存回测报告
     * @param reportDTO 报告数据
     * @return 保存是否成功
     */
    boolean saveReport(BacktestReportDTO reportDTO);

    /**
     * 更新报告笔记
     * @param taskId 任务ID
     * @param notes 笔记内容
     * @return 更新是否成功
     */
    boolean updateNotes(String taskId, String notes);

    /**
     * 删除报告
     * @param taskId 任务ID
     * @return 删除是否成功
     */
    boolean deleteReport(String taskId);
}