package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.entity.MlTrainingJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MlTrainingJobMapper extends BaseMapper<MlTrainingJob> {
}
