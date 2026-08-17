package com.chain.ai.trade.engine.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.strategy.entity.dos.EntryRuleCondition;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyEntryRule;
import com.chain.ai.trade.engine.strategy.mapper.EntryRuleConditionMapper;
import com.chain.ai.trade.engine.strategy.mapper.StrategyEntryRuleMapper;
import com.chain.ai.trade.engine.strategy.service.IStrategyEntryRuleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StrategyEntryRuleServiceImpl extends ServiceImpl<StrategyEntryRuleMapper, StrategyEntryRule>
        implements IStrategyEntryRuleService {

    @Autowired
    private StrategyEntryRuleMapper ruleMapper;

    @Autowired
    private EntryRuleConditionMapper conditionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, List<EntryRuleCondition>> loadEntryRulesByStrategyId(String strategyId) {
        Map<String, List<EntryRuleCondition>> result = new HashMap<>();

        List<StrategyEntryRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<StrategyEntryRule>()
                        .eq(StrategyEntryRule::getStrategyId, strategyId));

        for (StrategyEntryRule rule : rules) {
            if (Boolean.TRUE.equals(rule.getDisabled())) {
                continue;
            }

            List<EntryRuleCondition> conditions = conditionMapper.selectList(
                    new LambdaQueryWrapper<EntryRuleCondition>()
                            .eq(EntryRuleCondition::getRuleId, rule.getRuleId())
                            .orderByAsc(EntryRuleCondition::getSequence));

            result.put(rule.getDirection(), conditions);
        }

        return result;
    }

    @Override
    public StrategyEntryRule getRuleByStrategyAndDirection(String strategyId, String direction) {
        return ruleMapper.selectOne(
                new LambdaQueryWrapper<StrategyEntryRule>()
                        .eq(StrategyEntryRule::getStrategyId, strategyId)
                        .eq(StrategyEntryRule::getDirection, direction));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEntryRules(String strategyId, String entryRulesJson) {
        if (entryRulesJson == null || entryRulesJson.isBlank()) {
            return;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(entryRulesJson,
                    new TypeReference<Map<String, Object>>() {});

            for (String direction : new String[]{"long", "short"}) {
                Object dirObj = root.get(direction);
                if (!(dirObj instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> dirMap = (Map<String, Object>) dirObj;

                boolean disabled = Boolean.TRUE.equals(dirMap.get("disabled"));
                String dirUpper = direction.equals("long") ? "LONG" : "SHORT";

                // 删除该方向旧规则
                StrategyEntryRule oldRule = getRuleByStrategyAndDirection(strategyId, dirUpper);
                if (oldRule != null) {
                    conditionMapper.delete(new LambdaQueryWrapper<EntryRuleCondition>()
                            .eq(EntryRuleCondition::getRuleId, oldRule.getRuleId()));
                    ruleMapper.deleteById(oldRule.getId());
                }

                if (disabled) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> conditions =
                        (List<Map<String, Object>>) dirMap.get("conditions");
                if (conditions == null || conditions.isEmpty()) {
                    continue;
                }

                // 创建新规则
                String ruleId = UUID.randomUUID().toString();
                StrategyEntryRule rule = new StrategyEntryRule();
                rule.setRuleId(ruleId);
                rule.setStrategyId(strategyId);
                rule.setDirection(dirUpper);
                rule.setDisabled(false);
                rule.setVersion(1);
                ruleMapper.insert(rule);

                // 插入条件
                for (int i = 0; i < conditions.size(); i++) {
                    Map<String, Object> cond = conditions.get(i);
                    EntryRuleCondition entry = new EntryRuleCondition();
                    entry.setRuleId(ruleId);
                    entry.setSequence(i + 1);

                    String connector = (String) cond.get("connector");
                    if (i == 0) {
                        entry.setConnector(null);
                    } else {
                        entry.setConnector(connector != null ? connector.toUpperCase() : "AND");
                    }

                    String indicator = (String) cond.get("indicator");
                    entry.setIndicatorType(indicator != null ? indicator.toUpperCase() : null);
                    entry.setIndicatorParams(buildIndicatorParams(indicator, cond));
                    entry.setOperator((String) cond.get("operator"));
                    entry.setThreshold(parseBigDecimal(cond.get("threshold")));
                    conditionMapper.insert(entry);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("保存入场规则失败: " + e.getMessage(), e);
        }
    }

    private String buildIndicatorParams(String indicator, Map<String, Object> cond) {
        Map<String, Object> params = new HashMap<>();
        try {
            if ("rsi".equalsIgnoreCase(indicator)) {
                params.put("period", parseInt(cond.get("period"), 14));
            } else if ("macd".equalsIgnoreCase(indicator)) {
                params.put("fastPeriod", parseInt(cond.get("fastPeriod"), 12));
                params.put("slowPeriod", parseInt(cond.get("slowPeriod"), 26));
                params.put("signalPeriod", parseInt(cond.get("signalPeriod"), 9));
            } else if ("volume".equalsIgnoreCase(indicator)) {
                params.put("period", parseInt(cond.get("period"), 20));
            }
        } catch (Exception ignored) {
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Integer parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private java.math.BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Map<String, Object> loadEntryRulesResponse(String strategyId) {
        Map<String, Object> result = new HashMap<>();
        List<StrategyEntryRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<StrategyEntryRule>()
                        .eq(StrategyEntryRule::getStrategyId, strategyId));

        for (String direction : new String[]{"LONG", "SHORT"}) {
            String key = direction.toLowerCase();
            StrategyEntryRule rule = rules.stream()
                    .filter(r -> direction.equals(r.getDirection()))
                    .findFirst().orElse(null);

            Map<String, Object> dirMap = new HashMap<>();
            List<Map<String, Object>> conditions = new java.util.ArrayList<>();

            if (rule != null) {
                dirMap.put("disabled", Boolean.TRUE.equals(rule.getDisabled()));

                if (!Boolean.TRUE.equals(rule.getDisabled())) {
                    List<EntryRuleCondition> condList = conditionMapper.selectList(
                            new LambdaQueryWrapper<EntryRuleCondition>()
                                    .eq(EntryRuleCondition::getRuleId, rule.getRuleId())
                                    .orderByAsc(EntryRuleCondition::getSequence));
                    for (EntryRuleCondition c : condList) {
                        Map<String, Object> condMap = new HashMap<>();
                        condMap.put("indicator", c.getIndicatorType() != null ? c.getIndicatorType().toLowerCase() : null);
                        // 展开 indicator_params 中的参数到顶层
                        if (c.getIndicatorParams() != null) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> params = objectMapper.readValue(c.getIndicatorParams(), Map.class);
                                condMap.putAll(params);
                            } catch (Exception ignored) {
                            }
                        }
                        condMap.put("operator", c.getOperator());
                        condMap.put("threshold", c.getThreshold());
                        // 连接符：第一条返回 null，后续返回小写
                        if (c.getSequence() != null && c.getSequence() > 1) {
                            condMap.put("connector", c.getConnector() != null ? c.getConnector().toLowerCase() : "and");
                        } else {
                            condMap.put("connector", null);
                        }
                        conditions.add(condMap);
                    }
                }
            } else {
                dirMap.put("disabled", true);
            }

            dirMap.put("conditions", conditions);
            result.put(key, dirMap);
        }
        return result;
    }
}
