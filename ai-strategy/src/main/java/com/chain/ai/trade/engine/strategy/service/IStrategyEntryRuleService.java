package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.EntryRuleCondition;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyEntryRule;

import java.util.List;
import java.util.Map;

public interface IStrategyEntryRuleService extends IService<StrategyEntryRule> {

    /**
     * 根据策略ID加载入场规则，返回 Map<direction, List<condition>>
     */
    Map<String, List<EntryRuleCondition>> loadEntryRulesByStrategyId(String strategyId);

    StrategyEntryRule getRuleByStrategyAndDirection(String strategyId, String direction);

    /**
     * 保存入场规则到专用表（先删后插）
     *
     * @param strategyId     策略ID
     * @param entryRulesJson 前端传入的 entryRules JSON 字符串
     *                       格式: {"long":{"disabled":false,"conditions":[...]},"short":{...}}
     */
    void saveEntryRules(String strategyId, String entryRulesJson);

    /**
     * 加载入场规则（含 disabled 状态），返回前端回显格式
     * 返回 Map: {"long": {"disabled":true/false, "conditions":[...]}, "short": {...}}
     */
    Map<String, Object> loadEntryRulesResponse(String strategyId);
}
