package com.chain.ai.trade.engine.jobhandler;

import cn.hutool.json.JSONObject;
import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.common.utils.RedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEquityCurveFlushJob {

    private final RedisCache redisCache;
    private final BacktestEquityCurveService equityCurveService;

    private static final String CURVE_KEY_PREFIX = "equity:curve:";
    private static final String LAST_SYNC_PREFIX = "equity:last_sync:";
    private static final String ACTIVE_TASKS_KEY = "equity:active_tasks";
    private static final long DEFAULT_TTL_DAYS = 30;

   // @Scheduled(fixedDelay = 180000L, initialDelay = 15000L)
    public void flush() {
        try {
            // 从 Set 读取活跃 taskId（无阻塞，无大 Key 扫描风险）
            Set<Object> taskIdSet = redisCache.setMembers(ACTIVE_TASKS_KEY);
            if (taskIdSet == null || taskIdSet.isEmpty()) return;

            boolean hasRemaining = false;
            for (Object t : taskIdSet) {
                String taskId = String.valueOf(t);
                if (taskId.isBlank()) continue;
                try {
                    String fullKey = CURVE_KEY_PREFIX + taskId;
                    boolean exists = processTaskKeyIfExist(fullKey);
                    if (exists) {
                        hasRemaining = true;
                    } else {
                        // key 已过期，从活跃 Set 中清理
                        redisCache.setRemove(ACTIVE_TASKS_KEY, taskId);
                    }
                } catch (Exception e) {
                    log.warn("刷新任务权益曲线异常, taskId={}", taskId, e);
                }
            }
            // 如果还有活跃任务，续期 Set 的 TTL
            if (hasRemaining) {
                redisCache.expire(ACTIVE_TASKS_KEY, 3, java.util.concurrent.TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("扫描活跃 taskId Set 失败", e);
        }
    }

    /**
     * 处理单个 taskId 的曲线数据
     * @param key 完整 Redis key（equity:curve:{taskId}）
     * @return true=该 key 还有数据，false=key 已过期
     */
    private boolean processTaskKeyIfExist(String key) {
        String taskId = key.substring(CURVE_KEY_PREFIX.length());
        if (taskId.isBlank()) return false;

        // 读取增量同步偏移
        long lastSync = 0L;
        String lastSyncStr = redisCache.getString(LAST_SYNC_PREFIX + taskId);
        if (lastSyncStr != null) {
            try { lastSync = Long.parseLong(lastSyncStr); } catch (Exception ignored) {}
        }

        // 用 ZRANGEBYSCORE 只拉取未处理的数据，避免 HGETALL 大 Key 超时
        Set<ZSetOperations.TypedTuple<Object>> entries = redisCache.zRangeByScore(key, lastSync + 1, Long.MAX_VALUE);
        if (entries == null || entries.isEmpty()) {
            // key 可能已过期，检查是否存在
            return redisCache.hasKey(key);
        }

        long maxTs = lastSync;
        List<BacktestEquityCurve> list = new ArrayList<>();

        for (ZSetOperations.TypedTuple<Object> entry : entries) {
            try {
                String json = Objects.toString(entry.getValue(), null);
                if (json == null || json.isBlank()) continue;

                JSONObject obj = new JSONObject(json);
                Long ts = obj.getLong("ts", null);
                if (ts == null) continue;
                // 从分数中获取时间戳作为兜底
                if (entry.getScore() != null) {
                    ts = entry.getScore().longValue();
                }
                if (ts <= lastSync) continue;

                BigDecimal equity = obj.getBigDecimal("equity");
                if (equity == null) continue;

                LocalDateTime time = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                if (ts > maxTs) maxTs = ts;

                BacktestEquityCurve curve = BacktestEquityCurve.builder()
                        .time(time)
                        .equity(equity)
                        .returnRate(obj.getBigDecimal("returnRate"))
                        .drawdown(obj.getBigDecimal("drawdown"))
                        .build();

                // 从 JSON 数据读取 robotId（实盘/回测均有值），taskId 解析作为兜底
                String robotIdFromData = obj.getStr("robotId");
                if (robotIdFromData != null && !robotIdFromData.isBlank()
                        && !"NA".equals(robotIdFromData) && !"null".equalsIgnoreCase(robotIdFromData)) {
                    curve.setRobotId(robotIdFromData);
                }

                // 解析 taskId：LIVE:{robotId}:{symbol}:{interval} 为实盘，其余为回测纯 ID
                if (taskId.startsWith("LIVE:")) {
                    String[] parts = taskId.split(":", 3);
                    if (curve.getRobotId() == null && parts.length > 1) {
                        curve.setRobotId(parts[1]);
                    }
                    curve.setTaskId(null);
                } else {
                    curve.setTaskId(taskId);
                }

                list.add(curve);
            } catch (Exception e) {
                log.warn("解析权益点失败, value={}", entry.getValue(), e);
            }
        }

        if (!list.isEmpty()) {
            equityCurveService.batchInsertOrUpdate(list);
            redisCache.put(LAST_SYNC_PREFIX + taskId, String.valueOf(maxTs),
                    DEFAULT_TTL_DAYS, TimeUnit.DAYS);
            // 清理已落库的数据，保持 ZSET 只包含未处理数据，防止大 Key 超时
            redisCache.zRemoveRangeByScore(key, 0, maxTs);
            log.info("flush equity curve: robotId={}, taskId={}, size={}",
                    list.get(0).getRobotId(),
                    list.get(0).getTaskId(),
                    list.size());
        }

        // key 还存在数据（不管是否新增），返回 true 保持活跃
        return true;
    }
}
