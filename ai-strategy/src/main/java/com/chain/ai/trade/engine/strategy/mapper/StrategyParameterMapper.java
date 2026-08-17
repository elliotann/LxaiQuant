package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略参数Mapper接口
 */
@Mapper
public interface StrategyParameterMapper extends BaseMapper<StrategyParameter> {
}

