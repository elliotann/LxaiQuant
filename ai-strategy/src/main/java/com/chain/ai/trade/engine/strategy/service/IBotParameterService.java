package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.BotParameter;

import java.util.List;
import java.util.Map;

/**
 * 机器人参数服务接口
 */
public interface IBotParameterService extends IService<BotParameter> {

    /**
     * 查询机器人某分组的所有参数
     */
    List<BotParameter> listByBotIdAndGroup(String botId, String groupName);

    /**
     * 查询指定参数值
     */
    String getParameterValue(String botId, String groupName, String name);

    /**
     * 批量保存/更新机器人分组参数，已存在的覆盖，不存在的新增
     */
    void saveParameters(String botId, String groupName, Map<String, String> params);

    /**
     * 删除机器人某分组下所有参数
     */
    void deleteByBotIdAndGroup(String botId, String groupName);

    /**
     * 删除机器人的所有参数
     */
    void deleteByBotId(String botId);
}
