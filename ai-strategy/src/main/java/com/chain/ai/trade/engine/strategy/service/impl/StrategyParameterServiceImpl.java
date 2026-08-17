package com.chain.ai.trade.engine.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter;
import com.chain.ai.trade.engine.strategy.mapper.StrategyParameterMapper;
import com.chain.ai.trade.engine.strategy.service.IStrategyParameterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 策略参数服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyParameterServiceImpl extends ServiceImpl<StrategyParameterMapper, StrategyParameter> 
        implements IStrategyParameterService {

    @Override
    public List<StrategyParameter> listByStrategyId(String strategyId) {
        LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrategyParameter::getStrategyId, strategyId);
        wrapper.orderByAsc(StrategyParameter::getDisplayOrder);
        return this.list(wrapper);
    }

    @Override
    public List<StrategyParameter> listByStrategyIdAndGroup(String strategyId, String groupName) {
        LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrategyParameter::getStrategyId, strategyId);
        if (groupName != null && !groupName.trim().isEmpty()) {
            wrapper.eq(StrategyParameter::getGroupName, groupName);
        }
        wrapper.orderByAsc(StrategyParameter::getDisplayOrder);
        return this.list(wrapper);
    }

    @Override
    public String generateParamId() {
        return "PARAM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 保存策略参数列表
     * 先删除该策略的旧参数，再保存新参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveStrategyParameters(String strategyId, List<StrategyParameter> parameters) {
        // 删除该策略的旧参数
        LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrategyParameter::getStrategyId, strategyId);
        this.remove(wrapper);

        // 保存新参数
        if (parameters != null && !parameters.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < parameters.size(); i++) {
                StrategyParameter param = parameters.get(i);
                param.setStrategyId(strategyId);
                param.setParamId(generateParamId());
                param.setDisplayOrder(i);
                param.setCreatedAt(now);
                param.setUpdatedAt(now);
                // 如果可见性未设置，默认为可见
                if (param.getIsVisible() == null) {
                    param.setIsVisible(true);
                }
            }
            this.saveBatch(parameters);
            log.info("保存策略参数成功: strategyId={}, count={}", strategyId, parameters.size());
        }
    }

    @Override
    public String getParameterValue(String strategyId, String groupName, String name) {
        LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrategyParameter::getStrategyId, strategyId);
        wrapper.eq(StrategyParameter::getGroupName, groupName);
        wrapper.eq(StrategyParameter::getName, name);
        StrategyParameter param = this.getOne(wrapper);
        return param != null ? param.getDefaultValue() : null;
    }
}

