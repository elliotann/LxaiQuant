package com.chain.ai.trade.engine.controller.risk;

import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Slf4j
public class RiskEquityController {

    private final BacktestEquityCurveService equityCurveService;
    private final ITradingAccountService tradingAccountService;

    @GetMapping("/equity-curve")
    public ResponseEntity<Map<String, Object>> equityCurve(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to
    ) {
        try {
            ZoneId zone = ZoneId.systemDefault();
            long now = System.currentTimeMillis();
            long f = from != null ? from : startOfDayMs(now, zone);
            long t = to != null ? to : now;

            String s = scope != null ? scope.trim().toUpperCase() : null;
            if ((taskId == null || taskId.isBlank()) && (s == null || s.isBlank())) {
                return ResponseEntity.badRequest().body(fail("缺少 taskId 或 scope"));
            }

            List<Map<String, Object>> points;
            if (taskId != null && !taskId.isBlank()) {
                points = loadCurveByTaskId(taskId, zone, f, t);
            } else if ("ACCOUNT".equals(s)) {
                if (accountId == null || accountId.isBlank()) {
                    return ResponseEntity.badRequest().body(fail("缺少 accountId"));
                }
                points = loadAccountCurveAcrossDays(accountId, zone, f, t);
            } else if ("MEMBER".equals(s)) {
                if (memberId == null) {
                    return ResponseEntity.badRequest().body(fail("缺少 memberId"));
                }
                points = loadMemberCurveAcrossDays(memberId, zone, f, t);
            } else {
                return ResponseEntity.badRequest().body(fail("无效scope"));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("scope", s);
            data.put("accountId", accountId);
            data.put("memberId", memberId);
            data.put("from", f);
            data.put("to", t);
            data.put("points", points);
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("data", data);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            log.error("equity-curve query failed", e);
            return ResponseEntity.internalServerError().body(fail(e.getMessage()));
        }
    }

    @GetMapping("/equity-baseline")
    public ResponseEntity<Map<String, Object>> equityBaseline(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to
    ) {
        try {
            ZoneId zone = ZoneId.systemDefault();
            long now = System.currentTimeMillis();
            long f = from != null ? from : startOfDayMs(now, zone);
            long t = to != null ? to : now;

            String s = scope != null ? scope.trim().toUpperCase() : null;
            List<Map<String, Object>> points;
            if (taskId != null && !taskId.isBlank()) {
                points = loadCurveByTaskId(taskId, zone, f, t);
            } else if ("ACCOUNT".equals(s)) {
                points = loadAccountCurveAcrossDays(accountId, zone, f, t);
            } else if ("MEMBER".equals(s)) {
                points = loadMemberCurveAcrossDays(memberId, zone, f, t);
            } else {
                return ResponseEntity.badRequest().body(fail("缺少 taskId 或 scope/id"));
            }

            if (points == null || points.isEmpty()) {
                Map<String, Object> ok = new HashMap<>();
                ok.put("success", true);
                Map<String, Object> data = new HashMap<>();
                data.put("baselineEquity", null);
                data.put("dayPnl", null);
                data.put("dayPnlRatio", null);
                ok.put("data", data);
                return ResponseEntity.ok(ok);
            }
            BigDecimal baseline = (BigDecimal) points.get(0).get("equity");
            BigDecimal lastEquity = (BigDecimal) points.get(points.size() - 1).get("equity");
            BigDecimal dayPnl = lastEquity.subtract(baseline);
            BigDecimal ratio = baseline.signum() != 0 ? dayPnl.divide(baseline, 8, java.math.RoundingMode.HALF_UP) : null;
            Map<String, Object> data = new HashMap<>();
            data.put("baselineEquity", baseline);
            data.put("dayPnl", dayPnl);
            data.put("dayPnlRatio", ratio);
            Map<String, Object> ok = new HashMap<>();
            ok.put("success", true);
            ok.put("data", data);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            log.error("equity-baseline query failed", e);
            return ResponseEntity.internalServerError().body(fail(e.getMessage()));
        }
    }

    private List<Map<String, Object>> loadCurveByTaskId(String taskId, ZoneId zone, long from, long to) {
        List<BacktestEquityCurve> list = equityCurveService.getEquityCurveByTaskId(taskId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .map(e -> {
                    long ts = e.getTime().atZone(zone).toInstant().toEpochMilli();
                    Map<String, Object> m = new HashMap<>();
                    m.put("ts", ts);
                    m.put("equity", e.getEquity());
                    return m;
                })
                .filter(p -> {
                    long x = ((Number) p.get("ts")).longValue();
                    return x >= from && x <= to;
                })
                .sorted((a, b) -> Long.compare(((Number) a.get("ts")).longValue(), ((Number) b.get("ts")).longValue()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> loadAccountCurveAcrossDays(String accountId, ZoneId zone, long from, long to) {
        List<String> taskIds = buildAccountTaskIds(accountId, zone, from, to);
        List<Map<String, Object>> merged = new ArrayList<>();
        for (String tid : taskIds) {
            merged.addAll(loadCurveByTaskId(tid, zone, from, to));
        }
        merged.sort((a, b) -> Long.compare(((Number) a.get("ts")).longValue(), ((Number) b.get("ts")).longValue()));
        return merged;
    }

    private List<Map<String, Object>> loadMemberCurveAcrossDays(Long memberId, ZoneId zone, long from, long to) {
        List<String> accountIds = listAccountIdsByMemberId(memberId);
        if (accountIds.isEmpty()) return List.of();
        List<String> days = buildDays(zone, from, to);
        Map<Long, BigDecimal> ts2eq = new LinkedHashMap<>();
        for (String day : days) {
            for (String aid : accountIds) {
                String tid = "LIVE:ACCOUNT:" + aid + ":" + day;
                List<BacktestEquityCurve> list = equityCurveService.getEquityCurveByTaskId(tid);
                if (list == null || list.isEmpty()) continue;
                for (BacktestEquityCurve e : list) {
                    long ts = e.getTime().atZone(zone).toInstant().toEpochMilli();
                    if (ts < from || ts > to) continue;
                    BigDecimal equity = e.getEquity();
                    if (equity == null) continue;
                    ts2eq.merge(ts, equity, BigDecimal::add);
                }
            }
        }
        return ts2eq.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("ts", en.getKey());
                    m.put("equity", en.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private List<String> listAccountIdsByMemberId(Long memberId) {
        if (memberId == null) return List.of();
        List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) return List.of();
        String mid = String.valueOf(memberId);
        return accounts.stream()
                .filter(a -> Objects.equals(mid, a.getMemberId()))
                .map(TradingAccount::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> buildAccountTaskIds(String accountId, ZoneId zone, long from, long to) {
        List<String> days = buildDays(zone, from, to);
        return days.stream().map(day -> "LIVE:ACCOUNT:" + accountId + ":" + day).collect(Collectors.toList());
    }

    private List<String> buildDays(ZoneId zone, long from, long to) {
        LocalDate start = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(to).atZone(zone).toLocalDate();
        List<String> days = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            days.add(String.format("%04d%02d%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth()));
            d = d.plusDays(1);
        }
        return days;
    }

    private long startOfDayMs(long now, ZoneId zone) {
        LocalDate d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return d.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", false);
        m.put("message", msg);
        return m;
    }
}
