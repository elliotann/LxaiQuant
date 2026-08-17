package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略Mapper接口
 */
@Mapper
public interface StrategyMapper extends BaseMapper<Strategy> {
}

