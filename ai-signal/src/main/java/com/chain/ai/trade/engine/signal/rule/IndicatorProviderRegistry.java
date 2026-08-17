package com.chain.ai.trade.engine.signal.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndicatorProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(IndicatorProviderRegistry.class);

    private final Map<String, IndicatorProvider> providerMap = new LinkedHashMap<>();
    private final BuiltInIndicatorProvider builtIn;

    public IndicatorProviderRegistry(List<IndicatorProvider> providers, BuiltInIndicatorProvider builtIn) {
        this.builtIn = builtIn;
        for (IndicatorProvider p : providers) {
            if (p instanceof BuiltInIndicatorProvider) continue;
            registerExternalProvider(p);
        }
    }

    private void registerExternalProvider(IndicatorProvider provider) {
        IndicatorMetadata meta = provider.getMetadata();
        if (meta != null && meta.getId() != null) {
            String id = meta.getId();
            if (builtIn.supports(id)) {
                log.warn("外部 IndicatorProvider [{}] 覆盖内置指标", id);
            }
            providerMap.put(id, provider);
            log.info("注册外部指标: id={}, name={}", id, meta.getName());
        }
    }

    public IndicatorValue resolve(String indicator, WeightRuleContext ctx, Map<String, String> params) {
        IndicatorProvider p = providerMap.get(indicator);
        if (p != null) {
            return p.resolve(ctx, params);
        }
        if (builtIn.supports(indicator)) {
            if (params == null) params = new java.util.HashMap<>();
            params.put("indicator", indicator);
            return builtIn.resolve(ctx, params);
        }
        log.warn("未知指标类型: {}", indicator);
        return null;
    }

    public IndicatorMetadata getMetadata(String indicator) {
        IndicatorProvider p = providerMap.get(indicator);
        if (p != null) return p.getMetadata();
        return builtIn.getMetadataFor(indicator);
    }

    public List<IndicatorMetadata> getAllMetadata() {
        List<IndicatorMetadata> result = new ArrayList<>(builtIn.getAllMetadata());
        for (IndicatorProvider p : providerMap.values()) {
            IndicatorMetadata meta = p.getMetadata();
            if (meta != null) result.add(meta);
        }
        return result;
    }

    public boolean supports(String indicator) {
        return providerMap.containsKey(indicator) || builtIn.supports(indicator);
    }
}
