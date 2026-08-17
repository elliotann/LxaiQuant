package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.engine.model.ExitRuleConfig;
import com.chain.ai.trade.engine.strategy.ExitRulesConfigDTO;
import com.chain.ai.trade.engine.strategy.service.IStrategyParameterService;
import cn.hutool.json.JSONUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 出场规则配置加载服务
 */
public class ExitRuleConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(ExitRuleConfigService.class);

    public static ExitRulesConfigDTO loadConfigDTO(String strategyId) {
        if (strategyId == null) {
            return new ExitRulesConfigDTO();
        }
        try {
            IStrategyParameterService svc = SpringContextUtil.getBean(IStrategyParameterService.class);
            if (svc == null) {
                return new ExitRulesConfigDTO();
            }
            String json = svc.getParameterValue(strategyId, "exit_rules_config", "config");
            if (json == null || json.isBlank()) {
                return new ExitRulesConfigDTO();
            }
            // DB 存储的是嵌套格式（stopLoss/takeProfit），需通过 fromExitRulesMap 解析
            Map<String, Object> map = JSONUtil.parseObj(json);
            return ExitRulesConfigDTO.fromExitRulesMap(map);
        } catch (Exception e) {
            LOG.warn("loadExitRulesConfigDTO failed, use default config", e);
            return new ExitRulesConfigDTO();
        }
    }

    public static ExitRuleConfig loadRuleConfig(String strategyId) {
        ExitRuleConfig config = new ExitRuleConfig();
        if (strategyId == null) {
            return config;
        }
        try {
            IStrategyParameterService svc = SpringContextUtil.getBean(IStrategyParameterService.class);
            if (svc == null) {
                return config;
            }
            java.util.List<com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter> params =
                    svc.listByStrategyIdAndGroup(strategyId, "exit_rules");
            if (params == null || params.isEmpty()) {
                return config;
            }
            String stopLossJson = null;
            String takeProfitJson = null;
            String signalReversalEnabledValue = null;
            for (com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter p : params) {
                if (p == null || p.getName() == null) continue;
                String name = p.getName();
                String val = p.getDefaultValue();
                if ("stopLoss".equals(name) && val != null) stopLossJson = val;
                if ("takeProfit".equals(name) && val != null) takeProfitJson = val;
                if ("signalReversalExitEnabled".equals(name) && val != null) signalReversalEnabledValue = val;
            }
            if (signalReversalEnabledValue != null) {
                config.signalReversalExitEnabled = "true".equalsIgnoreCase(signalReversalEnabledValue);
            }
            if (stopLossJson != null && !stopLossJson.isBlank()) {
                JSONObject obj = new JSONObject(stopLossJson);
                config.fixedPercentStopLossEnabled = obj.optBoolean("enabled", false);
                if (obj.has("percent")) {
                    config.fixedPercentStopLossPercent = obj.optDouble("percent");
                }
            }
            if (takeProfitJson != null && !takeProfitJson.isBlank()) {
                JSONObject obj = new JSONObject(takeProfitJson);
                config.takeProfitEnabled = obj.optBoolean("enabled", false);
                config.takeProfitType = obj.optString("type", null);
                if (obj.has("percent")) {
                    config.takeProfitPercent = obj.optDouble("percent");
                }
            }
        } catch (Exception ignored) {
        }
        return config;
    }
}
