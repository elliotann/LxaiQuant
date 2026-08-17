package com.chain.ai.trade.backtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.backtest.entity.dos.BacktestTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测任务Mapper接口
 */
@Mapper
public interface BacktestTaskMapper extends BaseMapper<BacktestTask> {
}
