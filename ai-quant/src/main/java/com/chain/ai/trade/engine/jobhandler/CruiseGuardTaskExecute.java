package com.chain.ai.trade.engine.jobhandler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.entity.AiTradePlan;
import com.chain.ai.trade.engine.mapper.AiTradePlanMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.enums.BotStatus;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CruiseGuardTaskExecute {

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;
    private final ITradingAccountService tradingAccountService;
    private final ITradeOrderService tradeOrderService;
    private final ITradingBotService tradingBotService;
    private final AiTradePlanMapper aiTradePlanMapper;

    @Value("${cruise.guard.enabled:true}")
    private boolean enabled;

    @Value("${cruise.guard.maxSessionsPerRun:200}")
    private int maxSessionsPerRun;

    @Value("${cruise.guard.closePositions:true}")
    private boolean closePositionsEnabled;

    @XxlJob("cruiseGuardExecute")
    public void execute() {
        if (!enabled) {
            XxlJobHelper.handleSuccess("disabled");
            return;
        }

        int hitGoal = 0;
        int hitLoss = 0;
        int skipped = 0;
        int processed = 0;

        try {
            List<Object> keys = redisCache.keys("cruise:guard:session:*");
            if (keys == null || keys.isEmpty()) {
                XxlJobHelper.handleSuccess("no sessions");
                return;
            }

            int take = Math.min(Math.max(1, maxSessionsPerRun), 1000);
            for (Object ko : keys) {
                if (processed >= take) break;
                processed++;
                String key = String.valueOf(ko);
                String accountId = parseAccountIdFromKey(key);
                if (accountId.isBlank()) {
                    skipped++;
                    continue;
                }

                CruiseSession session = loadSession(key);
                if (session == null || !session.enabled || !"running".equalsIgnoreCase(session.status)) {
                    skipped++;
                    continue;
                }

                Summary summary = computeSummary(accountId);
                if (summary == null || summary.totalAssetsUsd.compareTo(BigDecimal.ZERO) <= 0) {
                    skipped++;
                    touchSession(session, key, "skipped_no_assets");
                    continue;
                }

                BigDecimal dailyPnlPercent = summary.dailyPnlPercent;
                boolean goalReached = session.goalPercent != null && dailyPnlPercent.compareTo(session.goalPercent) >= 0;
                boolean lossReached = session.maxLossPercent != null && dailyPnlPercent.compareTo(session.maxLossPercent.negate()) <= 0;

                if (!goalReached && !lossReached) {
                    touchSession(session, key, "running");
                    continue;
                }

                if (goalReached) hitGoal++;
                if (lossReached) hitLoss++;

                String reason = goalReached ? "goal_reached" : "loss_reached";
                applyGuardActions(session, summary, reason);
                finishSession(session, key, reason, summary);
            }

            XxlJobHelper.handleSuccess(String.format(
                    "processed=%d hitGoal=%d hitLoss=%d skipped=%d",
                    processed, hitGoal, hitLoss, skipped
            ));
        } catch (Exception e) {
            log.error("巡航守护任务执行失败", e);
            XxlJobHelper.handleFail("failed: " + e.getMessage());
        }
    }

    private String parseAccountIdFromKey(String key) {
        if (key == null) return "";
        int idx = key.lastIndexOf(':');
        if (idx < 0 || idx == key.length() - 1) return "";
        return key.substring(idx + 1).trim();
    }

    private CruiseSession loadSession(String key) {
        try {
            Object raw = redisCache.get(key);
            if (raw == null) return null;
            Map<String, Object> m = objectMapper.readValue(String.valueOf(raw), Map.class);
            CruiseSession s = new CruiseSession();
            s.enabled = Boolean.TRUE.equals(m.get("enabled"));
            s.status = String.valueOf(m.getOrDefault("status", "running"));
            s.accountId = String.valueOf(m.getOrDefault("accountId", ""));
            s.action = String.valueOf(m.getOrDefault("action", "PAUSE")).toUpperCase();
            s.cancelPendingPlans = Boolean.TRUE.equals(m.get("cancelPendingPlans"));
            s.closePositions = Boolean.TRUE.equals(m.get("closePositions"));
            s.goalPercent = toBigDecimal(m.get("goalPercent"));
            s.maxLossPercent = toBigDecimal(m.get("maxLossPercent"));
            s.startedAtMs = toLong(m.get("startedAtMs"));
            s.updatedAtMs = toLong(m.get("updatedAtMs"));
            s.finishedAtMs = toLong(m.get("finishedAtMs"));
            s.lastReason = String.valueOf(m.getOrDefault("lastReason", ""));
            s.botIds = readStringList(m.get("botIds"));
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveSession(String key, CruiseSession s) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", s.enabled);
            m.put("status", s.status);
            m.put("accountId", s.accountId);
            m.put("goalPercent", s.goalPercent);
            m.put("maxLossPercent", s.maxLossPercent);
            m.put("action", s.action);
            m.put("cancelPendingPlans", s.cancelPendingPlans);
            m.put("closePositions", s.closePositions);
            m.put("botIds", s.botIds == null ? List.of() : s.botIds);
            m.put("startedAtMs", s.startedAtMs);
            m.put("updatedAtMs", s.updatedAtMs);
            if (s.finishedAtMs != null && s.finishedAtMs > 0) m.put("finishedAtMs", s.finishedAtMs);
            if (s.lastReason != null && !s.lastReason.isBlank()) m.put("lastReason", s.lastReason);
            if (s.lastSnapshot != null && !s.lastSnapshot.isEmpty()) m.put("lastSnapshot", s.lastSnapshot);
            redisCache.put(key, objectMapper.writeValueAsString(m));
        } catch (Exception ignored) {
        }
    }

    private void touchSession(CruiseSession s, String key, String reason) {
        s.updatedAtMs = System.currentTimeMillis();
        s.lastReason = reason;
        saveSession(key, s);
    }

    private void finishSession(CruiseSession s, String key, String reason, Summary summary) {
        s.enabled = false;
        s.status = "finished";
        s.finishedAtMs = System.currentTimeMillis();
        s.updatedAtMs = s.finishedAtMs;
        s.lastReason = reason;
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("dailyPnLUsd", summary.dailyPnlUsd);
        snap.put("totalAssetsUsd", summary.totalAssetsUsd);
        snap.put("dailyPnLPercent", summary.dailyPnlPercent);
        s.lastSnapshot = snap;
        saveSession(key, s);
    }

    private Summary computeSummary(String accountId) {
        try {
            TradingAccount account = tradingAccountService.getByAccountId(accountId);
            BigDecimal totalAssets = BigDecimal.ZERO;
            if (account != null && account.getBalances() != null) {
                Map<String, BigDecimal> balances = parseBalances(account.getBalances());
                BigDecimal usdt = balances.getOrDefault("USDT", BigDecimal.ZERO);
                BigDecimal usd = balances.getOrDefault("USD", BigDecimal.ZERO);
                BigDecimal usdc = balances.getOrDefault("USDC", BigDecimal.ZERO);
                BigDecimal busd = balances.getOrDefault("BUSD", BigDecimal.ZERO);
                BigDecimal tusd = balances.getOrDefault("TUSD", BigDecimal.ZERO);
                BigDecimal dai = balances.getOrDefault("DAI", BigDecimal.ZERO);
                totalAssets = usdt.add(usd).add(usdc).add(busd).add(tusd).add(dai);
            }

            Date now = new Date();
            Date startOfDay = new Date(LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            BigDecimal dailyPnL = tradeOrderService.getNetProfitByAccountId(accountId, startOfDay, now);
            if (dailyPnL == null) dailyPnL = BigDecimal.ZERO;

            BigDecimal dailyPercent = BigDecimal.ZERO;
            if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                dailyPercent = dailyPnL.multiply(new BigDecimal("100")).divide(totalAssets, 4, RoundingMode.HALF_UP);
            }

            Summary s = new Summary();
            s.accountId = accountId;
            s.totalAssetsUsd = totalAssets;
            s.dailyPnlUsd = dailyPnL;
            s.dailyPnlPercent = dailyPercent;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private void applyGuardActions(CruiseSession session, Summary summary, String reason) {
        String accountId = summary.accountId;
        List<TradingBot> runningBots = tradingBotService.listByStatus(BotStatus.RUNNING.getCode());
        List<TradingBot> targets = new ArrayList<>();
        if (runningBots != null) {
            for (TradingBot b : runningBots) {
                if (b == null) continue;
                if (b.getEnabled() != null && !b.getEnabled()) continue;
                if (b.getAccountId() == null || !b.getAccountId().trim().equals(accountId)) continue;
                if (session.botIds != null && !session.botIds.isEmpty() && !session.botIds.contains(b.getBotId())) continue;
                targets.add(b);
            }
        }

        for (TradingBot b : targets) {
            try {
                if ("STOP".equalsIgnoreCase(session.action)) {
                    tradingBotService.stopBot(b.getBotId());
                } else {
                    tradingBotService.pauseBot(b.getBotId());
                }
            } catch (Exception e) {
                log.warn("巡航守护操作机器人失败: botId={}, reason={}", b.getBotId(), reason);
            }
        }

        if (session.cancelPendingPlans) {
            cancelPendingPlansByAccountId(accountId, reason, summary);
        }

        if (closePositionsEnabled && session.closePositions) {
            closePositionsByBots(accountId, targets, reason);
        }
    }

    private void closePositionsByBots(String accountId, List<TradingBot> targets, String reason) {
        if (targets == null || targets.isEmpty()) return;
        Set<String> symbols = new LinkedHashSet<>();
        for (TradingBot b : targets) {
            String s = b == null ? "" : String.valueOf(b.getTradingPair() == null ? "" : b.getTradingPair()).trim();
            if (!s.isEmpty()) symbols.add(s);
        }
        if (symbols.isEmpty()) return;

        Set<String> closed = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        for (String symbol : symbols) {
            List<OrderVO> orders;
            try {
                orders = tradeOrderService.getPositionOrders(accountId, symbol);
            } catch (Exception e) {
                continue;
            }
            if (orders == null || orders.isEmpty()) continue;
            for (OrderVO o : orders) {
                if (o == null) continue;
                String orderSn = o.getOrderSn();
                if (orderSn == null || orderSn.isBlank()) continue;
                if (closed.contains(orderSn)) continue;
                try {
                    boolean ok = tradeOrderService.closeOrderByOrderSn(orderSn);
                    if (ok) closed.add(orderSn);
                    else failed.add(orderSn);
                } catch (Exception e) {
                    failed.add(orderSn);
                }
            }
        }

        if (!closed.isEmpty() || !failed.isEmpty()) {
            log.info("巡航自动平仓: accountId={}, reason={}, closed={}, failed={}", accountId, reason, closed.size(), failed.size());
        }
    }

    private void cancelPendingPlansByAccountId(String accountId, String reason, Summary summary) {
        List<AiTradePlan> pending = aiTradePlanMapper.selectList(
                new LambdaQueryWrapper<AiTradePlan>()
                        .eq(AiTradePlan::getStatus, "pending")
                        .orderByDesc(AiTradePlan::getUpdateTime)
                        .last("limit 500")
        );
        if (pending == null || pending.isEmpty()) return;

        for (AiTradePlan p : pending) {
            if (p == null) continue;
            String pcJson = p.getPlanContent();
            if (pcJson == null || pcJson.isBlank()) continue;
            String planAccountId = "";
            try {
                Map<String, Object> pc = objectMapper.readValue(pcJson, Map.class);
                Object v = pc.get("accountId");
                planAccountId = v == null ? "" : String.valueOf(v).trim();
            } catch (Exception ignored) {
            }
            if (!planAccountId.equals(accountId)) continue;

            try {
                p.setStatus("cancelled");
                Map<String, Object> er = new LinkedHashMap<>();
                er.put("type", "cruise_guard");
                er.put("reason", reason);
                er.put("dailyPnLUsd", summary.dailyPnlUsd);
                er.put("dailyPnLPercent", summary.dailyPnlPercent);
                p.setExecutionResult(objectMapper.writeValueAsString(er));
                p.setUpdateTime(new Date());
                p.setUpdateBy("system");
                aiTradePlanMapper.updateById(p);
            } catch (Exception ignored) {
            }
        }
    }

    private Map<String, BigDecimal> parseBalances(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static BigDecimal toBigDecimal(Object v) {
        try {
            if (v == null) return null;
            if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return new BigDecimal(String.valueOf(n));
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long toLong(Object v) {
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<String> readStringList(Object v) {
        try {
            if (v == null) return List.of();
            if (v instanceof List<?> l) {
                List<String> out = new ArrayList<>();
                for (Object o : l) {
                    if (o == null) continue;
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) out.add(s);
                }
                return out.stream().distinct().toList();
            }
            String s = String.valueOf(v);
            if (s.isBlank()) return List.of();
            return objectMapper.readValue(s, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static class CruiseSession {
        boolean enabled;
        String status;
        String accountId;
        BigDecimal goalPercent;
        BigDecimal maxLossPercent;
        String action;
        boolean cancelPendingPlans;
        boolean closePositions;
        List<String> botIds;
        long startedAtMs;
        long updatedAtMs;
        Long finishedAtMs;
        String lastReason;
        Map<String, Object> lastSnapshot;
    }

    private static class Summary {
        String accountId;
        BigDecimal totalAssetsUsd;
        BigDecimal dailyPnlUsd;
        BigDecimal dailyPnlPercent;
    }
}
