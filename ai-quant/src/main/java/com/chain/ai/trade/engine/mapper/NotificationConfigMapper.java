package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.notifier.entity.NotificationConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationConfigMapper extends BaseMapper<NotificationConfig> {
}
