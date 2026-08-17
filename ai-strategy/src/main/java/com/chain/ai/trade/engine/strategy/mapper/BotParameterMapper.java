package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.BotParameter;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 机器人参数Mapper接口
 */
public interface BotParameterMapper extends BaseMapper<BotParameter> {

    /**
     * 查询机器人某分组的所有参数
     */
    List<BotParameter> selectByBotIdAndGroup(@Param("botId") String botId, @Param("groupName") String groupName);

    /**
     * 查询指定参数值
     */
    BotParameter selectByBotIdGroupAndName(@Param("botId") String botId,
                                           @Param("groupName") String groupName,
                                           @Param("name") String name);

    /**
     * 删除机器人某分组下所有参数
     */
    int deleteByBotIdAndGroup(@Param("botId") String botId, @Param("groupName") String groupName);

    /**
     * 删除机器人的所有参数
     */
    int deleteByBotId(@Param("botId") String botId);
}
