package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略版本Mapper接口
 */
@Mapper
public interface StrategyVersionMapper extends BaseMapper<StrategyVersion> {
}

