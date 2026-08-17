package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter;

import java.util.List;

/**
 * 策略参数服务接口
 */
public interface IStrategyParameterService extends IService<StrategyParameter> {

    /**
     * 根据策略ID查询参数列表
     *
     * @param strategyId 策略ID
     * @return 参数列表
     */
    List<StrategyParameter> listByStrategyId(String strategyId);

    /**
     * 根据策略ID和分组名称查询参数列表
     *
     * @param strategyId 策略ID
     * @param groupName 分组名称
     * @return 参数列表
     */
    List<StrategyParameter> listByStrategyIdAndGroup(String strategyId, String groupName);

    /**
     * 生成参数ID
     *
     * @return 参数ID
     */
    String generateParamId();

    /**
     * 保存策略参数列表
     * 先删除该策略的旧参数，再保存新参数
     *
     * @param strategyId 策略ID
     * @param parameters 参数列表
     */
    void saveStrategyParameters(String strategyId, List<StrategyParameter> parameters);

    /**
     * 根据策略ID、分组和参数名称查询单个参数值
     *
     * @param strategyId 策略ID
     * @param groupName  分组名称
     * @param name       参数名称
     * @return 参数值（defaultValue），不存在时返回null
     */
    String getParameterValue(String strategyId, String groupName, String name);
}

