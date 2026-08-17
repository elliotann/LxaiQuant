package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyVersion;

import java.util.List;

/**
 * 策略版本服务接口
 */
public interface IStrategyVersionService extends IService<StrategyVersion> {

    /**
     * 根据策略ID查询版本列表
     *
     * @param strategyId 策略ID
     * @return 版本列表
     */
    List<StrategyVersion> listByStrategyId(String strategyId);

    /**
     * 获取当前版本
     *
     * @param strategyId 策略ID
     * @return 当前版本
     */
    StrategyVersion getCurrentVersion(String strategyId);

    /**
     * 分页查询策略版本
     *
     * @param page 分页参数
     * @param strategyId 策略ID
     * @return 分页结果
     */
    IPage<StrategyVersion> pageVersions(Page<StrategyVersion> page, String strategyId);

    /**
     * 生成版本ID
     *
     * @return 版本ID
     */
    String generateVersionId();
}

