package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略分类Mapper接口
 */
@Mapper
public interface StrategyCategoryMapper extends BaseMapper<StrategyCategory> {
}

