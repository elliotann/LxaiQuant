package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;

import java.util.List;

/**
 * 策略服务接口
 */
public interface IStrategyService extends IService<Strategy> {

    /**
     * 根据策略ID查询策略
     *
     * @param strategyId 策略唯一标识
     * @return 策略实体
     */
    Strategy getByStrategyId(String strategyId);

    /**
     * 分页查询策略
     *
     * @param page 分页参数
     * @param name 策略名称（可选）
     * @param status 状态（可选）
     * @param strategyType 策略类型（可选）
     * @param categoryId 分类ID（可选）
     * @return 分页结果
     */
    IPage<Strategy> pageStrategies(Page<Strategy> page, String name, String status, 
                                   String strategyType, Long categoryId);

    /**
     * 根据分类ID查询策略列表
     *
     * @param categoryId 分类ID
     * @return 策略列表
     */
    List<Strategy> listByCategoryId(Long categoryId);

    /**
     * 根据所有者ID查询策略列表
     *
     * @param ownerId 所有者ID
     * @return 策略列表
     */
    List<Strategy> listByOwnerId(Long ownerId);

    /**
     * 更新策略状态
     *
     * @param strategyId 策略ID
     * @param status 新状态
     * @return 是否成功
     */
    boolean updateStatus(String strategyId, String status);

    /**
     * 生成策略ID
     *
     * @return 策略ID
     */
    String generateStrategyId();
}

