package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.entity.LlmConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmConfigMapper extends BaseMapper<LlmConfig> {
}

