package com.chain.ai.trade.engine.signal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.signal.entity.dos.SignalServiceConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SignalServiceConfigMapper extends BaseMapper<SignalServiceConfig> {
}
