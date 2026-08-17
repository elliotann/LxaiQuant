package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.notifier.entity.NotificationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationLogMapper extends BaseMapper<NotificationLog> {
}
