package com.chain.ai.trade.backtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.backtest.entity.dos.BacktestResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测结果Mapper接口
 */
@Mapper
public interface BacktestResultMapper extends BaseMapper<BacktestResult> {
}
