package com.chain.ai.trade.engine.signal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.engine.signal.entity.dos.WeightRuleVersion;
import com.chain.ai.trade.engine.signal.mapper.WeightRuleVersionMapper;
import com.chain.ai.trade.engine.signal.rule.WeightRuleConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightRuleVersionService {

    private final WeightRuleVersionMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createSnapshot(Long configId, WeightRuleConfig config, String remark, String createdBy) {
        if (configId == null || config == null) return;

        Integer latestVersion = getLatestVersion(configId);
        int newVersion = (latestVersion != null ? latestVersion : 0) + 1;

        String configJson;
        try {
            configJson = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("序列化权重规则配置失败: configId={}", configId, e);
            return;
        }

        WeightRuleVersion version = new WeightRuleVersion();
        version.setConfigId(configId);
        version.setVersion(newVersion);
        version.setConfigJson(configJson);
        version.setStatus(config.getStatus() != null ? config.getStatus() : "ACTIVE");
        version.setRemark(remark);
        version.setCreatedBy(createdBy != null ? createdBy : "system");
        version.setCreateTime(new Date());

        mapper.insert(version);
        log.info("创建权重规则版本快照: configId={}, version={}", configId, newVersion);
    }

    private Integer getLatestVersion(Long configId) {
        LambdaQueryWrapper<WeightRuleVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeightRuleVersion::getConfigId, configId)
                .orderByDesc(WeightRuleVersion::getVersion)
                .last("limit 1");
        WeightRuleVersion latest = mapper.selectOne(wrapper);
        return latest != null ? latest.getVersion() : null;
    }

    public List<WeightRuleVersion> listVersions(Long configId) {
        LambdaQueryWrapper<WeightRuleVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeightRuleVersion::getConfigId, configId)
                .orderByDesc(WeightRuleVersion::getVersion);
        return mapper.selectList(wrapper);
    }

    public WeightRuleConfig restoreVersion(Long configId, Integer version) {
        LambdaQueryWrapper<WeightRuleVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WeightRuleVersion::getConfigId, configId)
                .eq(WeightRuleVersion::getVersion, version)
                .last("limit 1");
        WeightRuleVersion record = mapper.selectOne(wrapper);
        if (record == null) return null;
        try {
            return objectMapper.readValue(record.getConfigJson(), WeightRuleConfig.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化权重规则版本失败: configId={}, version={}", configId, version, e);
            return null;
        }
    }
}
