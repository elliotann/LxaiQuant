package com.chain.ai.trade.engine.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.mapper.StrategyMapper;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 策略服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyServiceImpl extends ServiceImpl<StrategyMapper, Strategy> implements IStrategyService {

    @Override
    public Strategy getByStrategyId(String strategyId) {
        LambdaQueryWrapper<Strategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Strategy::getStrategyId, strategyId);
        return this.getOne(wrapper);
    }

    @Override
    public IPage<Strategy> pageStrategies(Page<Strategy> page, String name, String status,
                                         String strategyType, Long categoryId) {
        LambdaQueryWrapper<Strategy> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.isNotEmpty(name)) {
            wrapper.like(Strategy::getName, name);
        }
        if (StringUtils.isNotEmpty(status)) {
            wrapper.eq(Strategy::getStatus, status);
        }
        if (StringUtils.isNotEmpty(strategyType)) {
            wrapper.eq(Strategy::getStrategyType, strategyType);
        }
        if (categoryId != null) {
            wrapper.eq(Strategy::getCategoryId, categoryId);
        }
        
        wrapper.orderByDesc(Strategy::getCreatedAt);
        
        return this.page(page, wrapper);
    }

    @Override
    public List<Strategy> listByCategoryId(Long categoryId) {
        LambdaQueryWrapper<Strategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Strategy::getCategoryId, categoryId);
        wrapper.orderByDesc(Strategy::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public List<Strategy> listByOwnerId(Long ownerId) {
        LambdaQueryWrapper<Strategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Strategy::getOwnerId, ownerId);
        wrapper.orderByDesc(Strategy::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public boolean updateStatus(String strategyId, String status) {
        Strategy strategy = getByStrategyId(strategyId);
        if (strategy == null) {
            return false;
        }
        strategy.setStatus(status);
        return this.updateById(strategy);
    }

    @Override
    public String generateStrategyId() {
        return "STRATEGY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}

