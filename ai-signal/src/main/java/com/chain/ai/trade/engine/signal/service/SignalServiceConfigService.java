package com.chain.ai.trade.engine.signal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.engine.signal.entity.dos.SignalServiceConfig;
import com.chain.ai.trade.engine.signal.mapper.SignalServiceConfigMapper;
import com.chain.ai.trade.engine.signal.rule.WeightRuleConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalServiceConfigService {

    private final SignalServiceConfigMapper signalServiceConfigMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getParams(String serviceKey) {
        if (StringUtils.isBlank(serviceKey)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SignalServiceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignalServiceConfig::getServiceKey, serviceKey)
                .eq(SignalServiceConfig::getEnabled, true)
                .orderByDesc(SignalServiceConfig::getUpdatedAt)
                .last("limit 1");
        SignalServiceConfig config = signalServiceConfigMapper.selectOne(wrapper);
        if (config == null || StringUtils.isBlank(config.getParamsJson())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(config.getParamsJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("解析信号服务参数失败: serviceKey={}, error={}", serviceKey, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    public WeightRuleConfig getWeightRules(String serviceKey) {
        if (StringUtils.isBlank(serviceKey)) {
            return null;
        }
        LambdaQueryWrapper<SignalServiceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignalServiceConfig::getServiceKey, serviceKey)
                .eq(SignalServiceConfig::getEnabled, true)
                .orderByDesc(SignalServiceConfig::getUpdatedAt)
                .last("limit 1");
        SignalServiceConfig config = signalServiceConfigMapper.selectOne(wrapper);
        if (config == null || StringUtils.isBlank(config.getWeightRulesJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getWeightRulesJson(), WeightRuleConfig.class);
        } catch (Exception ex) {
            log.warn("解析权重规则失败: serviceKey={}, error={}", serviceKey, ex.getMessage());
            return null;
        }
    }

    public SignalServiceConfig getConfigByServiceKey(String serviceKey) {
        if (StringUtils.isBlank(serviceKey)) return null;
        LambdaQueryWrapper<SignalServiceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignalServiceConfig::getServiceKey, serviceKey)
                .orderByDesc(SignalServiceConfig::getUpdatedAt)
                .last("limit 1");
        return signalServiceConfigMapper.selectOne(wrapper);
    }
}
