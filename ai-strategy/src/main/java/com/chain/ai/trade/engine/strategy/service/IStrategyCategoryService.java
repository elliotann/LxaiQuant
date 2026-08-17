package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyCategory;

import java.util.List;

/**
 * 策略分类服务接口
 */
public interface IStrategyCategoryService extends IService<StrategyCategory> {

    /**
     * 根据分类代码查询分类
     *
     * @param code 分类代码
     * @return 分类实体
     */
    StrategyCategory getByCode(String code);

    /**
     * 查询所有启用的分类
     *
     * @return 分类列表
     */
    List<StrategyCategory> listActiveCategories();

    /**
     * 根据父分类ID查询子分类列表
     *
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    List<StrategyCategory> listByParentId(Long parentId);

    /**
     * 获取分类树
     *
     * @return 分类树列表（顶级分类）
     */
    List<StrategyCategory> getCategoryTree();
}

