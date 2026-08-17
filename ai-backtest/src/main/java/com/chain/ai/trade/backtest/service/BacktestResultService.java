package com.chain.ai.trade.backtest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.backtest.entity.dos.BacktestResult;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;

/**
 * 回测结果服务接口
 */
public interface BacktestResultService extends IService<BacktestResult> {

    /**
     * 保存回测结果
     */
    boolean saveResult(BacktestResultDTO resultDTO);

    /**
     * 获取回测结果
     */
    BacktestResultDTO getResult(String taskId);

    /**
     * 删除回测结果
     */
    boolean deleteResult(String taskId);
}
