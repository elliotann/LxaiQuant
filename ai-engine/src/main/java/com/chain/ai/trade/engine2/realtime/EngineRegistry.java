package com.chain.ai.trade.engine2.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 实盘引擎注册中心。
 * <p>
 * 职责：管理所有运行中的 LiveEngine 实例（注册/注销/查询）。
 * K 线数据由调度器直接调用 engine.syncBar() 推送。
 * </p>
 * <p>
 * 注册 key 格式：symbol_intervalCode，如 "ETH-USDT-SWAP_15m"
 * </p>
 */
@Slf4j
@Component
public class EngineRegistry {

    private final Map<String, RealtimeEngine> engines = new ConcurrentHashMap<>();

    /**
     * 注册引擎
     */
    public void register(String key, RealtimeEngine engine) {
        RealtimeEngine existing = engines.get(key);
        if (existing != null && existing.isRunning()) {
            log.warn("引擎已存在且运行中，先停止旧引擎: key={}", key);
            existing.stop();
        }
        engines.put(key, engine);
        log.info("引擎已注册: key={}", key);
    }

    /**
     * 注销引擎
     */
    public void unregister(String key) {
        RealtimeEngine removed = engines.remove(key);
        if (removed != null) {
            removed.stop();
            log.info("引擎已注销并停止: key={}", key);
        } else {
            log.warn("引擎不存在，注销忽略: key={}", key);
        }
    }

    /**
     * 按 key 获取引擎
     */
    public RealtimeEngine get(String key) {
        return engines.get(key);
    }

    /**
     * 引擎是否在运行
     */
    public boolean isRunning(String key) {
        RealtimeEngine engine = engines.get(key);
        return engine != null && engine.isRunning();
    }

    /**
     * 获取所有注册引擎的 key（仅返回 isRunning 的）
     */
    public Set<String> getActiveKeys() {
        return engines.entrySet().stream()
                .filter(e -> e.getValue().isRunning())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取所有运行中的引擎（返回副本，防止外部修改）
     */
    public Map<String, RealtimeEngine> getAllRunning() {
        return new ConcurrentHashMap<>(engines);
    }

    /**
     * 获取注册引擎总数
     */
    public int size() {
        return engines.size();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return engines.isEmpty();
    }
}