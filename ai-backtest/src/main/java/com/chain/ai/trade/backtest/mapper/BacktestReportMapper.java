package com.chain.ai.trade.backtest.mapper;

import com.chain.ai.trade.backtest.entity.dos.BacktestReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测报告Mapper接口
 */
@Mapper
public interface BacktestReportMapper extends BaseMapper<BacktestReport> {
}