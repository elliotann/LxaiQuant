package com.chain.ai.trade.engine.controller.advice;

import com.chain.ai.trade.engine.controller.LlmGenerateController;
import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.engine.entity.TradingAdvice;
import com.chain.ai.trade.engine.mapper.TradingAdviceMapper;
import com.chain.ai.trade.engine.service.LlmConfigService;
import com.chain.ai.trade.engine.service.prompt.MarkdownPromptTemplateService;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletOutputStream;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class LiveAdviceController {

    private static final String DEFAULT_INTERVAL = "3m";
    private static final int MAX_ADVICE_STORE_SIZE = 600;
    private static final Pattern TRADEPLAN_FENCE_PATTERN = Pattern.compile(
            "(?s)(?:^|\\n)```{3,}tradeplan[^\\n]*\\n(.*?)\\n```{3,}(?:\\s*(?:\\n|$))"
    );
    private static final ConcurrentHashMap<String, AdviceRecord> ADVICE_STORE = new ConcurrentHashMap<>();
    private static volatile AutoSignalConfig AUTO_SIGNAL_CONFIG = new AutoSignalConfig(false, List.of(), null, null, null);

    private final ITradeOrderService tradeOrderService;
    private final LlmGenerateController llmGenerateController;
    private final MarkdownPromptTemplateService markdownPromptTemplateService;
    private final LlmConfigService llmConfigService;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ICandlestickService candlestickService;
    private final ITradingAccountService tradingAccountService;
    private final ITechnicalSignalService technicalSignalService;
    private final TradingAdviceMapper tradingAdviceMapper;

    public static AdviceRecord getAdviceRecord(String adviceId) {
        if (adviceId == null || adviceId.isBlank()) return null;
        return ADVICE_STORE.get(adviceId);
    }

    public static AutoSignalConfig getAutoSignalConfig() {
        return AUTO_SIGNAL_CONFIG;
    }

    public static void setAutoSignalConfig(AutoSignalConfig config) {
        if (config == null) {
            AUTO_SIGNAL_CONFIG = new AutoSignalConfig(false, List.of(), null, null, null);
            return;
        }
        boolean enabled = Boolean.TRUE.equals(config.enabled);
        List<String> allowed = config.allowedActions != null ? config.allowedActions : List.of();
        AUTO_SIGNAL_CONFIG = new AutoSignalConfig(enabled, allowed, config.maxRiskPercent, config.onlySimulation, config.defaultSignalStrength);
    }

    @PostMapping("/live")
    public Object live(@RequestBody LiveAdviceRequest req, HttpServletResponse response) throws Exception {
        String raw = req == null ? "" : String.valueOf(req.symbolText == null ? "" : req.symbolText).trim();
        if (raw.isBlank()) {
            response.setStatus(400);
            return Map.of("error", "请输入标的");
        }
        String accountId = req == null ? "" : String.valueOf(req.accountId == null ? "" : req.accountId).trim();
        if (accountId.isBlank()) {
            response.setStatus(400);
            return Map.of("error", "请先选择账户");
        }

        String symbol = normalizeSymbol(raw);
        if (!isValidSymbol(symbol)) {
            response.setStatus(400);
            return Map.of("error", "symbol 格式不正确: " + raw);
        }

        String interval = req != null && req.interval != null && !req.interval.trim().isEmpty()
                ? req.interval.trim()
                : DEFAULT_INTERVAL;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("symbol", symbol);
        ctx.put("interval", interval);
        ctx.put("accountId", accountId);
        if (req != null && req.robotId != null && !req.robotId.trim().isEmpty()) {
            ctx.put("robotId", req.robotId.trim());
        }

        KlineSlice h1 = loadKlinesFromDb(symbol, CandlestickIntervalEnum.OKXMIN60, CandlestickIntervalEnum.MIN60, 50);
        KlineSlice m15 = loadKlinesFromDb(symbol, CandlestickIntervalEnum.OKXMIN15, CandlestickIntervalEnum.MIN15, 50);
        Map<String, Object> ticker = buildDbTicker(symbol, m15);
        ctx.put("ticker", ticker);
        ctx.put("orderbook", Map.of("source", "db_kline", "bids", List.of(), "asks", List.of()));
        ctx.put("recentTrades", List.of());
        ctx.put("kline1h", h1 != null ? h1.rows : List.of());
        ctx.put("kline15m", m15 != null ? m15.rows : List.of());
        ctx.put("kline1hMeta", h1 != null ? h1.meta : Map.of());
        ctx.put("kline15mMeta", m15 != null ? m15.meta : Map.of());

        List<OrderVO> positions = new ArrayList<>();
        try {
            positions = tradeOrderService.getPositionOrders(accountId, symbol);
        } catch (Exception ignored) {
            positions = new ArrayList<>();
        }
        if (positions != null && !positions.isEmpty()) {
            int take = Math.min(8, positions.size());
            ctx.put("positions", positions.subList(0, take));
        } else {
            ctx.put("positions", List.of());
        }

        String question = extractQuestion(raw, symbol).isBlank() ? "给出实时交易建议（可观望）。" : extractQuestion(raw, symbol);
        LlmConfig active = llmConfigService.getActiveSelection();
        String provider = active != null && active.getProvider() != null ? active.getProvider() : "ollama";
        AccountBalances balances = resolveAccountBalances(accountId);

        String systemPrompt;
        String userPrompt;
        if ("openclaw".equals(provider)) {
            MarkdownPromptTemplateService.LoadedTemplate tpl = markdownPromptTemplateService.loadLiveAdviceTemplate(environment);
            Map<String, String> vars = new HashMap<>();
            vars.put("symbol", symbol);
            vars.put("interval", interval);
            vars.put("accountId", accountId);
            vars.put("robotId", String.valueOf(ctx.getOrDefault("robotId", "")));
            vars.put("question", question);
            vars.put("snapshot", buildSnapshot(ctx));
            systemPrompt = markdownPromptTemplateService.render(tpl.system, vars);
            userPrompt = markdownPromptTemplateService.render(tpl.user, vars);
        } else {
            String snapshot = buildSnapshot(ctx);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            String nowText = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String priceText = resolveTickerLastPrice(ticker);

            BigDecimal availableBalance = balances != null ? balances.availableBalance : null;
            BigDecimal totalAsset = balances != null ? balances.totalAsset : null;
            String trendText = resolveTrendText(h1);
            Levels levels = resolveSupportResistance(h1, priceText);
            String rsiText = resolveRsiText(m15);
            String avgVolumeText = resolveAvgVolumeText(m15, 20);
            String positionsText = resolvePositionsText(positions);
            boolean executablePlan = availableBalance != null
                    && availableBalance.compareTo(BigDecimal.ZERO) > 0
                    && priceText != null
                    && !priceText.isBlank();

            MarkdownPromptTemplateService.LoadedTemplate tpl = markdownPromptTemplateService.loadLiveAdviceNonOpenclawTemplate(environment);
            Map<String, String> vars = new HashMap<>();
            vars.put("symbol", symbol);
            vars.put("intervalText", "1h/15m");
            vars.put("accountId", accountId);
            vars.put("robotId", String.valueOf(ctx.getOrDefault("robotId", "")));
            vars.put("question", question);
            vars.put("snapshot", snapshot);
            vars.put("nowText", nowText);
            vars.put("priceText", priceText);
            vars.put("trendText", trendText);
            vars.put("supportList", levels.supportList);
            vars.put("resistanceList", levels.resistanceList);
            vars.put("rsiText", rsiText);
            vars.put("avgVolumeText", avgVolumeText);
            vars.put("balanceText", formatNullableDecimal(availableBalance));
            vars.put("totalAssetText", formatNullableDecimal(totalAsset));
            vars.put("positionsText", positionsText);
            vars.put("executablePlan", String.valueOf(executablePlan));
            systemPrompt = markdownPromptTemplateService.render(tpl.system, vars);
            userPrompt = markdownPromptTemplateService.render(tpl.user, vars);
        }

        boolean streamRequested = req != null && Boolean.TRUE.equals(req.stream);

        LlmGenerateController.GenerateRequest gen = new LlmGenerateController.GenerateRequest();
        gen.stream = streamRequested ? Boolean.FALSE : (req != null ? req.stream : null);
        gen.messages = new ArrayList<>();

        LlmGenerateController.Message sys = new LlmGenerateController.Message();
        sys.role = "system";
        sys.content = systemPrompt;
        gen.messages.add(sys);

        if (req != null && req.history != null) {
            for (LlmGenerateController.Message m : req.history) {
                if (m == null) continue;
                if (m.role == null || m.role.isBlank()) continue;
                String c = m.content == null ? "" : m.content;
                if (c.isBlank()) continue;
                LlmGenerateController.Message item = new LlmGenerateController.Message();
                item.role = m.role;
                item.content = c;
                gen.messages.add(item);
            }
        }

        LlmGenerateController.Message user = new LlmGenerateController.Message();
        user.role = "user";
        user.content = userPrompt;
        gen.messages.add(user);

        Object out = llmGenerateController.generate(gen, response);
        if (!(out instanceof Map)) return out;
        Map<?, ?> m = (Map<?, ?>) out;
        if (m.containsKey("error")) return out;
        Object respObj = m.get("response");
        if (respObj == null) return out;
        String respText = String.valueOf(respObj);
        TradeplanExtract extract = extractTradeplan(respText);

        Map<String, Object> next = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e == null || e.getKey() == null) continue;
            next.put(String.valueOf(e.getKey()), e.getValue());
        }
        String naturalReport = stripMethodLine(stripTradeplan(respText));
        next.put("response", naturalReport);
        next.put("naturalReport", naturalReport);
        String adviceId = "adv_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        next.put("adviceId", adviceId);

        if (extract == null) {
            InferredTradeplan inferred = inferTradeplanFromReport(naturalReport, symbol, accountId, balances, positions, ticker);
            if (inferred != null) {
                next.put("tradeplanRaw", inferred.tradeplanRaw);
                next.put("tradeplan", inferred.tradeplan);
                List<String> errors = validateTradeplan(inferred.tradeplan);
                next.put("tradeplanValid", errors.isEmpty());
                next.put("tradeplanErrors", errors);
                storeAdvice(new AdviceRecord(adviceId, symbol, accountId, naturalReport, inferred.tradeplanRaw, inferred.tradeplan, errors.isEmpty(), errors, System.currentTimeMillis()));
                persistTradingAdvice(adviceId, symbol, naturalReport, inferred.tradeplanRaw);
                if (errors.isEmpty()) {
                    maybeAutoGenerateSignals(adviceId, inferred.tradeplan, errors, req != null ? req.leverage : null);
                }
            } else {
                next.put("tradeplan", null);
                next.put("tradeplanValid", false);
                next.put("tradeplanErrors", List.of("tradeplan 缺失"));
                next.put("tradeplanRaw", null);
                storeAdvice(new AdviceRecord(adviceId, symbol, accountId, naturalReport, null, null, false, List.of("tradeplan 缺失"), System.currentTimeMillis()));
                persistTradingAdvice(adviceId, symbol, naturalReport, null);
            }
            if (streamRequested) {
                streamAdviceResponse(response, naturalReport, next);
                return null;
            }
            return next;
        }

        next.put("tradeplanRaw", extract.json);
        try {
            Map<String, Object> tradeplan = objectMapper.readValue(extract.json, new TypeReference<Map<String, Object>>() {});
            List<String> errors = validateTradeplan(tradeplan);
            next.put("tradeplan", tradeplan);
            next.put("tradeplanValid", errors.isEmpty());
            next.put("tradeplanErrors", errors);
            storeAdvice(new AdviceRecord(adviceId, symbol, accountId, naturalReport, extract.json, tradeplan, errors.isEmpty(), errors, System.currentTimeMillis()));
            persistTradingAdvice(adviceId, symbol, naturalReport, extract.json);
            maybeAutoGenerateSignals(adviceId, tradeplan, errors, req != null ? req.leverage : null);
        } catch (Exception e) {
            next.put("tradeplan", null);
            next.put("tradeplanValid", false);
            next.put("tradeplanErrors", List.of("tradeplan JSON 解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            storeAdvice(new AdviceRecord(adviceId, symbol, accountId, naturalReport, extract.json, null, false, List.of("tradeplan JSON 解析失败"), System.currentTimeMillis()));
            persistTradingAdvice(adviceId, symbol, naturalReport, extract.json);
        }
        if (streamRequested) {
            streamAdviceResponse(response, naturalReport, next);
            return null;
        }
        return next;
    }

    private void streamAdviceResponse(HttpServletResponse response, String naturalReport, Map<String, Object> meta) throws Exception {
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();

            String text = naturalReport == null ? "" : naturalReport;
            int chunkSize = 80;
            for (int i = 0; i < text.length(); i += chunkSize) {
                String part = text.substring(i, Math.min(text.length(), i + chunkSize));
                String payload = objectMapper.writeValueAsString(Map.of("response", part));
                out.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                response.flushBuffer();
            }

            Map<String, Object> done = new LinkedHashMap<>();
            if (meta != null) done.putAll(meta);
            done.remove("response");
            done.put("done", true);
            String endPayload = objectMapper.writeValueAsString(done);
            out.write(("data: " + endPayload + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
        }
    }

    private void persistTradingAdvice(String adviceId, String symbol, String naturalReport, String tradeplanJson) {
        if (tradingAdviceMapper == null) {
            return;
        }
        if (adviceId == null || adviceId.isBlank()) {
            return;
        }
        TradingAdvice row = new TradingAdvice();
        row.setAdviceId(adviceId);
        row.setSymbol(symbol);
        row.setNaturalReport(naturalReport);
        row.setTradeplanJson(tradeplanJson);
        row.setCreatedAt(new Date());

        LambdaQueryWrapper<TradingAdvice> q = new LambdaQueryWrapper<>();
        q.eq(TradingAdvice::getAdviceId, adviceId);
        TradingAdvice existing = tradingAdviceMapper.selectOne(q);
        if (existing != null && existing.getId() != null) {
            row.setId(existing.getId());
            tradingAdviceMapper.updateById(row);
            return;
        }
        tradingAdviceMapper.insert(row);
    }

    private void storeAdvice(AdviceRecord record) {
        if (record == null || record.adviceId == null || record.adviceId.isBlank()) return;
        if (ADVICE_STORE.size() > MAX_ADVICE_STORE_SIZE) {
            long cutoff = System.currentTimeMillis() - Duration.ofHours(72).toMillis();
            ADVICE_STORE.entrySet().removeIf(e -> e.getValue() == null || e.getValue().createdAtMs < cutoff);
            if (ADVICE_STORE.size() > MAX_ADVICE_STORE_SIZE) {
                ADVICE_STORE.clear();
            }
        }
        ADVICE_STORE.put(record.adviceId, record);
    }

    private void maybeAutoGenerateSignals(String adviceId, Map<String, Object> tradeplan, List<String> errors) {
        maybeAutoGenerateSignals(adviceId, tradeplan, errors, null);
    }

    private void maybeAutoGenerateSignals(String adviceId, Map<String, Object> tradeplan, List<String> errors, BigDecimal leverage) {
        AutoSignalConfig cfg = AUTO_SIGNAL_CONFIG;
        if (cfg == null || !cfg.enabled) return;
        if (errors != null && !errors.isEmpty()) return;
        if (tradeplan == null) return;

        Set<String> allowed = new HashSet<>();
        if (cfg.allowedActions != null) {
            for (String a : cfg.allowedActions) {
                if (a == null) continue;
                String v = a.trim();
                if (!v.isEmpty()) allowed.add(v);
            }
        }
        if (allowed.isEmpty()) return;
        if (allowed.contains("all_signals")) {
            createAllSignalsFromAdvice(adviceId, tradeplan, leverage);
            return;
        }

        for (String a : allowed) {
            if (a == null) continue;
            String action = a.trim();
            if (action.isEmpty()) continue;
            try {
                createTechnicalSignalFromAdvice(adviceId, tradeplan, action, leverage);
            } catch (Exception ignored) {
            }
        }
    }

    private List<Long> createAllSignalsFromAdvice(String adviceId, Map<String, Object> tradeplan, BigDecimal leverage) {
        List<Long> ids = new ArrayList<>();
        for (String action : List.of("limit_signal", "cond_signal", "hedge_signal", "close_signal")) {
            try {
                Long id = createTechnicalSignalFromAdvice(adviceId, tradeplan, action, leverage);
                if (id != null) ids.add(id);
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    public Long createTechnicalSignalFromAdvice(String adviceId, Map<String, Object> tradeplan, String action) {
        return createTechnicalSignalFromAdvice(adviceId, tradeplan, action, null);
    }

    public Long createTechnicalSignalFromAdvice(String adviceId, Map<String, Object> tradeplan, String action, BigDecimal leverage) {
        if (tradeplan == null) {
            throw new IllegalArgumentException("tradeplan 为空");
        }
        String a = action == null ? "" : action.trim();
        if (!"limit_signal".equals(a) && !"cond_signal".equals(a) && !"hedge_signal".equals(a) && !"close_signal".equals(a)) {
            throw new IllegalArgumentException("不支持的 action: " + a);
        }

        Map<String, Object> facts = asMap(tradeplan.get("facts"));
        if (facts == null) {
            throw new IllegalArgumentException("facts 缺失");
        }
        Map<String, Object> advice = resolveAdviceForAction(tradeplan, a);
        if (advice == null) {
            throw new IllegalArgumentException("tradeplan 中未找到可用的 advice");
        }

        String symbol = getString(facts.get("symbol"));
        String intervalText = getString(facts.get("interval"));
        String timeframe = normalizeTimeframe(intervalText);
        ZonedDateTime snapshotTs = parseIsoTs(getString(facts.get("snapshotTs")));
        long klineTs = snapshotTs != null ? snapshotTs.toInstant().toEpochMilli() : System.currentTimeMillis();
        String klineTime = snapshotTs != null
                ? snapshotTs.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        BigDecimal latestPrice = toBigDecimal(facts.get("latestPrice"));
        if (latestPrice == null) {
            throw new IllegalArgumentException("facts.latestPrice 缺失");
        }

        String technicalDirection = resolveTechnicalDirectionForAction(a, facts, advice);
        BigDecimal signalStrength = resolveSignalStrengthForAdvice(advice);
        String extraParams = buildExtraParams(adviceId, tradeplan, advice, leverage);

        OrderPriceType entryType = resolveEntryTypeForAction(a, advice);
        BigDecimal limitPrice = resolveLimitPrice(entryType, advice);
        String dataSource = resolveDataSourceFromFacts(facts);

        TechnicalSignalDTO dto = new TechnicalSignalDTO();
        dto.setDataSource(dataSource);
        dto.setSignalSource("DEEPSEEK");
        dto.setSourceAdviceId(adviceId);
        dto.setSymbol(symbol);
        dto.setTimeframe(timeframe);
        dto.setKlineTime(klineTime);
        dto.setKlineTimestamp(klineTs);
        dto.setIndicator("AI_STRATEGY");
        dto.setStrategyName("DeepSeek_TrendFollowing");
        dto.setTechnicalDirection(technicalDirection);
        dto.setSignalStrength(signalStrength);
        dto.setCurrentPrice(latestPrice);
        dto.setIndicatorValues(Map.of());
        dto.setSignalHash("tmp");
        dto.setExtraParams(extraParams);
        dto.setMarketTrend(null);
        dto.setEntryType(entryType);
        dto.setLimitPrice(limitPrice);

        return technicalSignalService.saveTechnicalSignal(dto);
    }

    private String resolveDataSourceFromFacts(Map<String, Object> facts) {
        String fallback = "OKX";
        if (facts == null) {
            return fallback;
        }
        String accountId = getString(facts.get("accountId"));
        if (accountId.isBlank()) {
            return fallback;
        }
        TradingAccount account;
        try {
            account = tradingAccountService.getByAccountId(accountId);
        } catch (Exception e) {
            account = null;
        }
        if (account == null || account.getMemberPlatform() == null) {
            return fallback;
        }
        String name = account.getMemberPlatform().getName();
        return name == null || name.isBlank() ? fallback : name;
    }

    private Map<String, Object> resolveAdviceForAction(Map<String, Object> tradeplan, String action) {
        if (tradeplan == null) {
            return null;
        }
        if ("cond_signal".equals(action)) {
            return asMap(tradeplan.get("alternativeAdvice"));
        }
        return asMap(tradeplan.get("advice"));
    }

    private String resolveTechnicalDirectionForAction(String action, Map<String, Object> facts, Map<String, Object> advice) {
        if ("hedge_signal".equals(action) || "close_signal".equals(action)) {
            String side = resolvePrimaryPositionSide(facts);
            if (!"LONG".equals(side) && !"SHORT".equals(side)) {
                throw new IllegalArgumentException("facts.riskStatus.positions 缺失或无法识别持仓方向");
            }
            if ("hedge_signal".equals(action)) {
                return "LONG".equals(side) ? "SHORT" : "LONG";
            }
            return "LONG".equals(side) ? "CLOSE_SHORT" : "CLOSE_LONG";
        }

        String direction = getString(advice.get("direction")).toUpperCase(Locale.ROOT);
        if ("LONG".equals(direction)) {
            return "LONG";
        }
        if ("SHORT".equals(direction)) {
            return "SHORT";
        }
        if ("NO_TRADE".equals(direction)) {
            throw new IllegalArgumentException("direction=NO_TRADE，不生成信号");
        }
        throw new IllegalArgumentException("不支持的 direction: " + direction);
    }

    private String resolvePrimaryPositionSide(Map<String, Object> facts) {
        if (facts == null) {
            return "";
        }
        Map<String, Object> riskStatus = asMap(facts.get("riskStatus"));
        if (riskStatus == null) {
            return "";
        }
        Object positionsObj = riskStatus.get("positions");
        if (!(positionsObj instanceof List)) {
            return "";
        }
        List<?> positions = (List<?>) positionsObj;
        for (Object o : positions) {
            Map<String, Object> p = asMap(o);
            if (p == null) continue;
            String side = getString(p.get("side")).toUpperCase(Locale.ROOT);
            if ("LONG".equals(side) || "SHORT".equals(side)) {
                return side;
            }
        }
        return "";
    }

    private BigDecimal resolveSignalStrengthForAdvice(Map<String, Object> advice) {
        BigDecimal v = advice != null ? toBigDecimal(advice.get("signalStrength")) : null;
        if (v == null) {
            AutoSignalConfig cfg = AUTO_SIGNAL_CONFIG;
            v = cfg != null ? cfg.defaultSignalStrength : null;
        }
        if (v == null) {
            v = BigDecimal.ONE;
        }
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            v = BigDecimal.ZERO;
        } else if (v.compareTo(new BigDecimal("2")) > 0) {
            v = new BigDecimal("2");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private OrderPriceType resolveEntryTypeForAction(String action, Map<String, Object> advice) {
        if ("hedge_signal".equals(action) || "close_signal".equals(action)) {
            return OrderPriceType.MARKET;
        }
        Map<String, Object> entry = advice != null ? asMap(advice.get("entry")) : null;
        if (entry == null) {
            throw new IllegalArgumentException("advice.entry 缺失");
        }
        String t = getString(entry.get("type")).toUpperCase(Locale.ROOT);
        if ("LIMIT".equals(t)) {
            if ("cond_signal".equals(action)) {
                throw new IllegalArgumentException("cond_signal 需要 entry.type=CONDITION");
            }
            return OrderPriceType.LIMIT;
        }
        if ("MARKET".equals(t)) {
            if ("cond_signal".equals(action)) {
                throw new IllegalArgumentException("cond_signal 需要 entry.type=CONDITION");
            }
            return OrderPriceType.MARKET;
        }
        if ("CONDITION".equals(t)) {
            if ("limit_signal".equals(action)) {
                throw new IllegalArgumentException("limit_signal 不支持 entry.type=CONDITION");
            }
            return OrderPriceType.CONDITION;
        }
        throw new IllegalArgumentException("不支持的 entry.type=" + t);
    }

    private BigDecimal resolveLimitPrice(OrderPriceType entryType, Map<String, Object> advice) {
        if (entryType == null) {
            return null;
        }
        if (entryType != OrderPriceType.LIMIT && entryType != OrderPriceType.CONDITION) {
            return null;
        }
        Map<String, Object> entry = advice != null ? asMap(advice.get("entry")) : null;
        if (entry == null) {
            return null;
        }
        if (entryType == OrderPriceType.LIMIT) {
            return toBigDecimal(entry.get("price"));
        }
        String condition = getString(entry.get("condition"));
        if (condition.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(condition);
        if (!m.find()) {
            return null;
        }
        return toBigDecimal(m.group(1));
    }

    private String normalizeTimeframe(String intervalText) {
        String s = intervalText == null ? "" : intervalText.trim();
        if (s.isEmpty()) return "15m";
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("/")) {
            String[] parts = lower.split("/");
            String last = parts[parts.length - 1].trim();
            return last.isEmpty() ? "15m" : last;
        }
        return lower;
    }

    private String buildExtraParams(String adviceId, Map<String, Object> tradeplan, Map<String, Object> advice, BigDecimal leverage) {
        Map<String, Object> facts = asMap(tradeplan.get("facts"));
        Map<String, Object> entry = advice != null ? asMap(advice.get("entry")) : null;

        BigDecimal latestPrice = facts != null ? toBigDecimal(facts.get("latestPrice")) : null;
        BigDecimal entryPrice = entry != null ? toBigDecimal(entry.get("price")) : null;
        BigDecimal stopLoss = advice != null ? toBigDecimal(advice.get("stopLoss")) : null;
        List<?> takeProfit = advice != null && advice.get("takeProfit") instanceof List ? (List<?>) advice.get("takeProfit") : List.of();

        List<Map<String, Object>> priceTargets = new ArrayList<>();
        for (int i = 0; i < takeProfit.size(); i++) {
            Object item = takeProfit.get(i);
            Map<String, Object> tp = asMap(item);
            if (tp == null) continue;
            BigDecimal levelPrice = toBigDecimal(tp.get("level"));
            if (levelPrice == null || latestPrice == null) continue;
            BigDecimal distanceFromCurrent = levelPrice.subtract(latestPrice).abs();
            BigDecimal rr = null;
            if (entryPrice != null && stopLoss != null) {
                BigDecimal risk = entryPrice.subtract(stopLoss).abs();
                BigDecimal reward = levelPrice.subtract(entryPrice).abs();
                if (risk.compareTo(BigDecimal.ZERO) > 0) {
                    rr = reward.divide(risk, 2, RoundingMode.HALF_UP);
                }
            }
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("level", i + 1);
            pt.put("price", levelPrice);
            pt.put("probability", 0.7);
            pt.put("description", "DeepSeek 第" + (i + 1) + "止盈目标");
            pt.put("basedOn", "DeepSeek_AI");
            pt.put("distanceFromCurrent", distanceFromCurrent.setScale(2, RoundingMode.HALF_UP));
            pt.put("riskRewardRatio", rr);
            priceTargets.add(pt);
        }

        BigDecimal riskPercent = null;
        if (advice != null) {
            Map<String, Object> positionSize = asMap(advice.get("positionSize"));
            if (positionSize != null) {
                riskPercent = toBigDecimal(positionSize.get("riskPercent"));
            }
        }
        if (riskPercent == null) riskPercent = new BigDecimal("1.5");

        List<Map<String, Object>> stopLossLevels = new ArrayList<>();
        if (stopLoss != null) {
            Map<String, Object> sl = new LinkedHashMap<>();
            sl.put("level", 1);
            sl.put("price", stopLoss);
            sl.put("type", "固定止损");
            sl.put("description", "DeepSeek 建议止损位");
            sl.put("basedOn", "AI分析");
            sl.put("riskPercentage", riskPercent);
            sl.put("primary", true);
            stopLossLevels.add(sl);
        }

        BigDecimal optimalTakeProfit = !priceTargets.isEmpty() ? toBigDecimal(priceTargets.get(0).get("price")) : null;

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("priceTargets", priceTargets);
        extra.put("stopLossLevels", stopLossLevels);
        extra.put("optimalStopLoss", stopLoss);
        extra.put("optimalTakeProfit", optimalTakeProfit);
        if (leverage != null) {
            extra.put("leverage", leverage);
        }
        extra.put("_deepseek", Map.of("adviceId", adviceId, "tradeplan", tradeplan));
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            return String.valueOf(extra);
        }
    }

    private TradeplanExtract extractTradeplan(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = TRADEPLAN_FENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            String json = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (json.isBlank()) return null;
            return new TradeplanExtract(json, matcher.start(), matcher.end());
        }
        return extractTradeplanLooseJson(text);
    }

    private String stripMethodLine(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.startsWith("方法：") || t.startsWith("方法:")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private String stripTradeplan(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher matcher = TRADEPLAN_FENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            String stripped = matcher.replaceAll("\n");
            return stripped.trim();
        }
        TradeplanExtract loose = extractTradeplanLooseJson(text);
        if (loose == null) return text.trim();
        String stripped = text.substring(0, loose.startIndex) + "\n" + text.substring(loose.endIndex);
        return stripped.trim();
    }

    private TradeplanExtract extractTradeplanLooseJson(String text) {
        int typeIdx = text.indexOf("\"type\"");
        if (typeIdx < 0) return null;
        int liveIdx = text.indexOf("\"live_advice_v1\"", typeIdx);
        if (liveIdx < 0) return null;

        int start = text.lastIndexOf("\n{", liveIdx);
        if (start >= 0) {
            start = start + 1;
        } else {
            start = -1;
            int braceBalance = 0;
            for (int i = liveIdx; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '}') braceBalance++;
                if (c == '{') {
                    if (braceBalance == 0) {
                        start = i;
                        break;
                    }
                    braceBalance--;
                }
            }
        }
        if (start < 0) return null;
        int end = findJsonObjectEnd(text, start);
        if (end <= start) return null;
        String json = text.substring(start, end).trim();
        if (json.isBlank()) return null;
        return new TradeplanExtract(json, start, end);
    }

    private static class InferredTradeplan {
        final String tradeplanRaw;
        final Map<String, Object> tradeplan;

        InferredTradeplan(String tradeplanRaw, Map<String, Object> tradeplan) {
            this.tradeplanRaw = tradeplanRaw;
            this.tradeplan = tradeplan;
        }
    }

    private InferredTradeplan inferTradeplanFromReport(
            String naturalReport,
            String symbol,
            String accountId,
            AccountBalances balances,
            List<OrderVO> positions,
            Map<String, Object> ticker
    ) {
        if (naturalReport == null || naturalReport.isBlank()) return null;
        String text = naturalReport;

        String entryType = findFirstGroup(text, "订单类型\\s*[:：]\\s*(LIMIT|MARKET|CONDITION)");
        String directionText = findFirstGroup(text, "机会方向\\s*[:：]\\s*(多头|空头|观望)");
        String direction;
        if ("多头".equals(directionText)) direction = "LONG";
        else if ("空头".equals(directionText)) direction = "SHORT";
        else direction = "NO_TRADE";

        Map<String, Object> entry = new LinkedHashMap<>();
        String entryTypeUpper = entryType == null ? "" : entryType.trim().toUpperCase(Locale.ROOT);
        if (!"LIMIT".equals(entryTypeUpper) && !"MARKET".equals(entryTypeUpper) && !"CONDITION".equals(entryTypeUpper)) {
            entryTypeUpper = "LIMIT";
        }
        entry.put("type", entryTypeUpper);

        BigDecimal entryPrice = null;
        String entryCondition = null;
        if ("LIMIT".equals(entryTypeUpper)) {
            entryPrice = toBigDecimal(findFirstGroup(text, "入场价格\\s*([0-9]+(?:\\.[0-9]+)?)"));
        } else if ("CONDITION".equals(entryTypeUpper)) {
            entryCondition = findFirstGroup(text, "condition\\s*[:：]\\s*[“\"]?([^”\"\\n]+)");
        }
        entry.put("price", entryPrice);
        entry.put("condition", entryCondition);

        BigDecimal stopLoss = toBigDecimal(findFirstGroup(text, "止损(?:设置)?\\s*[:：]\\s*([0-9]+(?:\\.[0-9]+)?)"));

        String mainBlock = sliceBetween(text, "主策略", "备选策略");
        if (mainBlock == null || mainBlock.isBlank()) {
            mainBlock = text;
        }

        List<Map<String, Object>> takeProfit = new ArrayList<>();
        List<BigDecimal> tpLevels = findAllNumbersAfterLabel(mainBlock, "目标");
        List<BigDecimal> tpRatios = findAllRatios(mainBlock);
        int count = Math.min(tpLevels.size(), tpRatios.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> tp = new LinkedHashMap<>();
            tp.put("level", tpLevels.get(i));
            tp.put("ratio", tpRatios.get(i));
            takeProfit.add(tp);
        }
        if (takeProfit.isEmpty() && !tpLevels.isEmpty()) {
            Map<String, Object> tp = new LinkedHashMap<>();
            tp.put("level", tpLevels.get(0));
            tp.put("ratio", BigDecimal.ONE);
            takeProfit.add(tp);
        }
        if (takeProfit.isEmpty()) {
            takeProfit = List.of();
        } else {
            BigDecimal sum = BigDecimal.ZERO;
            for (Map<String, Object> tp : takeProfit) {
                BigDecimal ratio = toBigDecimal(tp.get("ratio"));
                if (ratio != null) sum = sum.add(ratio);
            }
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                for (Map<String, Object> tp : takeProfit) {
                    BigDecimal ratio = toBigDecimal(tp.get("ratio"));
                    if (ratio != null) {
                        tp.put("ratio", ratio.divide(sum, 4, RoundingMode.HALF_UP));
                    }
                }
            }
        }

        String calcBasis = findFirstGroup(mainBlock, "计算依据\\s*[:：]\\s*(.+)");
        if (calcBasis == null || calcBasis.isBlank()) {
            String basisLine = findFirstGroup(mainBlock, "仓位建议\\s*[:：]\\s*(.+)");
            if (basisLine != null && !basisLine.isBlank()) {
                calcBasis = basisLine;
            }
        }
        if (calcBasis == null || calcBasis.isBlank()) {
            calcBasis = "从自然语言报告中提取，缺少完整计算依据，请结合账户风险状态复核。";
        }

        BigDecimal riskPercent = toBigDecimal(findFirstGroup(mainBlock, "riskPercent\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)"));
        Integer suggestedContracts = null;
        String contractsText = findFirstGroup(mainBlock, "suggestedContracts\\s*=\\s*(\\d+)");
        if (contractsText != null && !contractsText.isBlank()) {
            try {
                suggestedContracts = Integer.parseInt(contractsText);
            } catch (Exception ignored) {
                suggestedContracts = null;
            }
        }

        Map<String, Object> positionSize = new LinkedHashMap<>();
        positionSize.put("suggestedContracts", suggestedContracts);
        positionSize.put("riskPercent", riskPercent);
        positionSize.put("calculationBasis", calcBasis);

        List<String> monitorConditions = new ArrayList<>();
        String monitorBlock = sliceBetween(text, "下一步监控信号", "执行清单");
        if (monitorBlock != null && !monitorBlock.isBlank()) {
            for (String line : monitorBlock.split("\\R")) {
                String t = line == null ? "" : line.trim();
                if (t.isBlank()) continue;
                if (t.startsWith("-")) t = t.substring(1).trim();
                if (t.isBlank()) continue;
                monitorConditions.add(t);
                if (monitorConditions.size() >= 6) break;
            }
        }
        if (monitorConditions.isEmpty()) {
            monitorConditions.add("观察价格是否达到入场条件后再执行。");
        }

        String reason = findFirstGroup(text, "当前价格应如何操作\\s*[:：]\\s*(.+)");
        if (reason == null || reason.isBlank()) {
            reason = "从自然语言报告中提取，建议结合关键价位与风控条件执行。";
        }

        ZonedDateTime snapshotTs = resolveSnapshotTs(ticker);
        String snapshotTsIso = snapshotTs.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String validUntil = snapshotTs.plusHours(24).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        BigDecimal latestPrice = ticker != null ? toBigDecimal(ticker.get("last")) : null;
        if (latestPrice == null) {
            latestPrice = toBigDecimal(findFirstGroup(text, "当前价格\\s*[:：]\\s*([0-9]+(?:\\.[0-9]+)?)"));
        }
        if (latestPrice == null) return null;

        Integer maxLeverage = null;
        String lev = findFirstGroup(text, "杠杆\\s*(\\d+)\\s*倍");
        if (lev != null && !lev.isBlank()) {
            try {
                maxLeverage = Integer.parseInt(lev);
            } catch (Exception ignored) {
                maxLeverage = null;
            }
        }
        if (maxLeverage == null) maxLeverage = 10;

        Map<String, Object> riskStatus = new LinkedHashMap<>();
        BigDecimal balance = balances != null ? balances.availableBalance : null;
        riskStatus.put("balance", balance);
        riskStatus.put("positions", List.of());
        riskStatus.put("marginRate", 0);
        riskStatus.put("maxLeverage", maxLeverage);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("symbol", symbol);
        facts.put("interval", "1h/15m");
        facts.put("accountId", accountId);
        facts.put("snapshotTs", snapshotTsIso);
        facts.put("latestPrice", latestPrice);
        facts.put("riskStatus", riskStatus);

        Map<String, Object> advice = new LinkedHashMap<>();
        advice.put("direction", direction);
        advice.put("entry", entry);
        advice.put("stopLoss", stopLoss);
        advice.put("takeProfit", takeProfit);
        advice.put("positionSize", positionSize);
        advice.put("signalStrength", 1.0);
        advice.put("validUntil", validUntil);
        advice.put("monitorConditions", monitorConditions);
        advice.put("reason", reason);

        Map<String, Object> tradeplan = new LinkedHashMap<>();
        tradeplan.put("type", "live_advice_v1");
        tradeplan.put("facts", facts);
        tradeplan.put("advice", advice);
        tradeplan.put("alternativeAdvice", null);
        tradeplan.put("tradePlanDraft", null);

        try {
            String raw = objectMapper.writeValueAsString(tradeplan);
            return new InferredTradeplan(raw, tradeplan);
        } catch (Exception e) {
            return null;
        }
    }

    private ZonedDateTime resolveSnapshotTs(Map<String, Object> ticker) {
        String ts = ticker == null ? "" : String.valueOf(ticker.getOrDefault("time", ""));
        if (ts != null && !ts.isBlank()) {
            String v = ts.trim();
            try {
                LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return ldt.atZone(ZoneId.of("Asia/Shanghai"));
            } catch (Exception ignored) {
            }
        }
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private String sliceBetween(String text, String startToken, String endToken) {
        if (text == null || text.isBlank()) return null;
        int s = text.indexOf(startToken);
        if (s < 0) return null;
        int e = endToken == null ? -1 : text.indexOf(endToken, s + startToken.length());
        if (e < 0) e = text.length();
        return text.substring(s, e);
    }

    private String findFirstGroup(String text, String regex) {
        if (text == null || text.isBlank()) return null;
        if (regex == null || regex.isBlank()) return null;
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String g = m.groupCount() >= 1 ? m.group(1) : null;
        return g == null ? null : g.trim();
    }

    private List<BigDecimal> findAllNumbersAfterLabel(String text, String label) {
        if (text == null || text.isBlank()) return List.of();
        Pattern p = Pattern.compile(label + "[^0-9\\n]*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        List<BigDecimal> out = new ArrayList<>();
        while (m.find()) {
            BigDecimal v = toBigDecimal(m.group(1));
            if (v != null) out.add(v);
            if (out.size() >= 4) break;
        }
        return out;
    }

    private List<BigDecimal> findAllRatios(String text) {
        if (text == null || text.isBlank()) return List.of();
        Pattern p = Pattern.compile("ratio\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        List<BigDecimal> out = new ArrayList<>();
        while (m.find()) {
            BigDecimal v = toBigDecimal(m.group(1));
            if (v != null) out.add(v);
            if (out.size() >= 4) break;
        }
        return out;
    }

    private int findJsonObjectEnd(String text, int startIndex) {
        boolean inString = false;
        boolean escape = false;
        int brace = 0;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                brace++;
                continue;
            }
            if (c == '}') {
                brace--;
                if (brace == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private List<String> validateTradeplan(Map<String, Object> tradeplan) {
        if (tradeplan == null || tradeplan.isEmpty()) return List.of("tradeplan 为空");
        List<String> errors = new ArrayList<>();

        String type = getString(tradeplan.get("type"));
        if (!"live_advice_v1".equals(type)) {
            errors.add("type 必须为 live_advice_v1");
        }

        Map<String, Object> facts = asMap(tradeplan.get("facts"));
        if (facts == null) {
            errors.add("facts 缺失");
        } else {
            String symbol = getString(facts.get("symbol"));
            if (symbol.isBlank()) errors.add("facts.symbol 缺失");
            String interval = getString(facts.get("interval"));
            if (interval.isBlank()) errors.add("facts.interval 缺失");
            String accountId = getString(facts.get("accountId"));
            if (accountId.isBlank()) errors.add("facts.accountId 缺失");
            String snapshotTs = getString(facts.get("snapshotTs"));
            if (snapshotTs.isBlank()) errors.add("facts.snapshotTs 缺失");
            BigDecimal latestPrice = toBigDecimal(facts.get("latestPrice"));
            if (latestPrice == null) errors.add("facts.latestPrice 缺失或非数字");

            Map<String, Object> riskStatus = asMap(facts.get("riskStatus"));
            if (riskStatus == null) {
                errors.add("facts.riskStatus 缺失");
            } else {
                Object positions = riskStatus.get("positions");
                if (!(positions instanceof List)) errors.add("facts.riskStatus.positions 必须为数组");
                Object maxLeverage = riskStatus.get("maxLeverage");
                if (maxLeverage == null) errors.add("facts.riskStatus.maxLeverage 缺失");
            }
        }

        Map<String, Object> advice = asMap(tradeplan.get("advice"));
        if (advice == null) {
            errors.add("advice 缺失");
            return errors;
        }
        validateAdviceBlock("advice", advice, facts, errors);

        Object altObj = tradeplan.get("alternativeAdvice");
        if (altObj != null) {
            Map<String, Object> alternativeAdvice = asMap(altObj);
            if (alternativeAdvice == null) {
                errors.add("alternativeAdvice 必须为对象或 null");
            } else {
                validateAdviceBlock("alternativeAdvice", alternativeAdvice, facts, errors);
            }
        }

        return errors;
    }

    private void validateAdviceBlock(String prefix, Map<String, Object> advice, Map<String, Object> facts, List<String> errors) {
        if (advice == null) {
            errors.add(prefix + " 缺失");
            return;
        }
        String direction = getString(advice.get("direction"));
        if (!"LONG".equals(direction) && !"SHORT".equals(direction) && !"NO_TRADE".equals(direction)) {
            errors.add(prefix + ".direction 必须为 LONG/SHORT/NO_TRADE");
        }

        Map<String, Object> entry = asMap(advice.get("entry"));
        if (entry == null) {
            errors.add(prefix + ".entry 缺失");
        } else {
            String entryType = getString(entry.get("type"));
            Object entryPrice = entry.get("price");
            Object entryCondition = entry.get("condition");
            if (!"LIMIT".equals(entryType) && !"CONDITION".equals(entryType) && !"MARKET".equals(entryType)) {
                errors.add(prefix + ".entry.type 必须为 LIMIT/CONDITION/MARKET");
            } else if ("LIMIT".equals(entryType)) {
                if (toBigDecimal(entryPrice) == null) errors.add("LIMIT 时 entry.price 必须为数值");
                if (entryCondition != null) errors.add("LIMIT 时 entry.condition 必须为 null");
            } else if ("CONDITION".equals(entryType)) {
                if (getString(entryCondition).isBlank()) errors.add("CONDITION 时 entry.condition 必须为字符串");
                if (entryPrice != null) errors.add("CONDITION 时 entry.price 必须为 null");
            } else if ("MARKET".equals(entryType)) {
                if (entryPrice != null) errors.add("MARKET 时 entry.price 必须为 null");
                if (entryCondition != null) errors.add("MARKET 时 entry.condition 必须为 null");
            }
        }

        Map<String, Object> positionSize = asMap(advice.get("positionSize"));
        if (positionSize == null) {
            errors.add(prefix + ".positionSize 缺失");
        } else {
            String basis = getString(positionSize.get("calculationBasis"));
            if (basis.isBlank()) errors.add(prefix + ".positionSize.calculationBasis 必须填写");
        }

        BigDecimal strength = toBigDecimal(advice.get("signalStrength"));
        if (strength != null) {
            if (strength.compareTo(BigDecimal.ZERO) < 0 || strength.compareTo(new BigDecimal("2")) > 0) {
                errors.add(prefix + ".signalStrength 必须在 0~2 范围内");
            }
        }

        Object monitor = advice.get("monitorConditions");
        if (!(monitor instanceof List) || ((List<?>) monitor).isEmpty()) {
            errors.add(prefix + ".monitorConditions 至少 1 条");
        }

        Object tp = advice.get("takeProfit");
        if (tp instanceof List) {
            BigDecimal sum = BigDecimal.ZERO;
            List<?> list = (List<?>) tp;
            for (Object o : list) {
                Map<String, Object> item = asMap(o);
                if (item == null) continue;
                BigDecimal ratio = toBigDecimal(item.get("ratio"));
                if (ratio != null) sum = sum.add(ratio);
            }
            if (!list.isEmpty()) {
                BigDecimal delta = sum.subtract(BigDecimal.ONE).abs();
                if (delta.compareTo(new BigDecimal("0.0001")) > 0) {
                    errors.add(prefix + ".takeProfit ratio 之和必须为 1.0");
                }
            }
        } else {
            errors.add(prefix + ".takeProfit 必须为数组");
        }

        if (facts != null) {
            ZonedDateTime snapshotTs = parseIsoTs(getString(facts.get("snapshotTs")));
            ZonedDateTime validUntil = parseIsoTs(getString(advice.get("validUntil")));
            if (snapshotTs == null) errors.add("facts.snapshotTs 必须为 ISO8601");
            if (validUntil == null) errors.add(prefix + ".validUntil 必须为 ISO8601");
            if (snapshotTs != null && validUntil != null) {
                ZonedDateTime expected = snapshotTs.plusHours(24);
                Duration diff = Duration.between(expected, validUntil).abs();
                if (diff.compareTo(Duration.ofMinutes(5)) > 0) {
                    errors.add(prefix + ".validUntil 必须为 snapshotTs + 24 小时");
                }
            }
        }
    }

    private ZonedDateTime parseIsoTs(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ZonedDateTime.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getString(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private Map<String, Object> asMap(Object v) {
        if (!(v instanceof Map)) return null;
        Map<?, ?> raw = (Map<?, ?>) v;
        Map<String, Object> next = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e == null || e.getKey() == null) continue;
            next.put(String.valueOf(e.getKey()), e.getValue());
        }
        return next;
    }

    private String resolveTickerLastPrice(Map<String, Object> ticker) {
        if (ticker == null) return "";
        Object last = ticker.get("last") != null ? ticker.get("last") : ticker.get("price");
        if (last == null) return "";
        String s = String.valueOf(last).trim();
        return s.equals("0") ? "" : s;
    }

    private String formatNullableDecimal(BigDecimal v) {
        if (v == null) return "null";
        return formatDecimal(v);
    }

    private String formatDecimal(BigDecimal v) {
        if (v == null) return "";
        BigDecimal normalized = v.stripTrailingZeros();
        String s = normalized.toPlainString();
        return s.equals("-0") ? "0" : s;
    }

    private AccountBalances resolveAccountBalances(String accountId) {
        if (accountId == null || accountId.isBlank()) return new AccountBalances(null, null);
        TradingAccount account;
        try {
            account = tradingAccountService.getByAccountId(accountId);
        } catch (Exception e) {
            account = null;
        }
        if (account == null) return new AccountBalances(null, null);
        String balancesJson = account.getBalances();
        if (balancesJson == null || balancesJson.isBlank()) return new AccountBalances(null, null);

        Map<String, BigDecimal> balances;
        try {
            balances = objectMapper.readValue(balancesJson, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            balances = Collections.emptyMap();
        }
        if (balances == null || balances.isEmpty()) return new AccountBalances(null, null);

        BigDecimal usdt = balances.getOrDefault("USDT", BigDecimal.ZERO);
        BigDecimal usd = balances.getOrDefault("USD", BigDecimal.ZERO);
        BigDecimal usdc = balances.getOrDefault("USDC", BigDecimal.ZERO);
        BigDecimal busd = balances.getOrDefault("BUSD", BigDecimal.ZERO);
        BigDecimal tusd = balances.getOrDefault("TUSD", BigDecimal.ZERO);
        BigDecimal dai = balances.getOrDefault("DAI", BigDecimal.ZERO);
        BigDecimal totalAsset = usdt.add(usd).add(usdc).add(busd).add(tusd).add(dai);
        BigDecimal availableBalance = usdt.compareTo(BigDecimal.ZERO) == 0 ? null : usdt;
        BigDecimal total = totalAsset.compareTo(BigDecimal.ZERO) == 0 ? null : totalAsset;
        return new AccountBalances(availableBalance, total);
    }

    private String resolvePositionsText(List<OrderVO> positions) {
        if (positions == null || positions.isEmpty()) return "无";
        List<String> items = new ArrayList<>();
        int take = Math.min(5, positions.size());
        for (int i = 0; i < take; i++) {
            OrderVO p = positions.get(i);
            if (p == null) continue;
            String side = "BUY".equalsIgnoreCase(p.getOrderSide()) ? "LONG" : "SELL".equalsIgnoreCase(p.getOrderSide()) ? "SHORT" : "";
            BigDecimal qty = p.getAmount() != null ? p.getAmount() : p.getVolume();
            BigDecimal entry = p.getOpenPrice() != null ? p.getOpenPrice()
                    : p.getBuyAvgPrice() != null ? p.getBuyAvgPrice()
                    : p.getBuyPrice() != null ? p.getBuyPrice()
                    : p.getSellPrice();
            String qtyText = qty != null ? formatDecimal(qty) : "?";
            String entryText = entry != null ? formatDecimal(entry) : "?";
            items.add(side + " " + qtyText + "张 开仓价" + entryText);
        }
        return String.join("；", items);
    }

    private String resolveTrendText(KlineSlice h1) {
        if (h1 == null || h1.rows == null || h1.rows.size() < 30) return "未知";
        List<BigDecimal> closes = new ArrayList<>();
        for (Map<String, Object> r : h1.rows) {
            BigDecimal c = toBigDecimal(r != null ? r.get("close") : null);
            if (c != null) closes.add(c);
        }
        if (closes.size() < 30) return "未知";
        BigDecimal latest = closes.get(closes.size() - 1);
        BigDecimal base = closes.get(Math.max(0, closes.size() - 25));
        if (base == null || latest == null || base.compareTo(BigDecimal.ZERO) == 0) return "未知";
        BigDecimal changePct = latest.subtract(base)
                .multiply(new BigDecimal("100"))
                .divide(base, 4, RoundingMode.HALF_UP);
        BigDecimal threshold = new BigDecimal("1.2");
        if (changePct.compareTo(threshold) >= 0) return "多头";
        if (changePct.compareTo(threshold.negate()) <= 0) return "空头";
        return "震荡";
    }

    private Levels resolveSupportResistance(KlineSlice h1, String priceText) {
        BigDecimal current = parseDecimal(priceText);
        if (h1 == null || h1.rows == null || h1.rows.size() < 10 || current == null) {
            return new Levels("未知", "未知");
        }

        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        for (Map<String, Object> r : h1.rows) {
            BigDecimal hi = toBigDecimal(r != null ? r.get("high") : null);
            BigDecimal lo = toBigDecimal(r != null ? r.get("low") : null);
            if (hi != null) highs.add(hi);
            if (lo != null) lows.add(lo);
        }
        if (highs.size() < 10 || lows.size() < 10) return new Levels("未知", "未知");

        List<BigDecimal> resistCandidates = pickSwingHighs(highs);
        List<BigDecimal> supportCandidates = pickSwingLows(lows);

        List<BigDecimal> resistances = filterLevels(resistCandidates, current, true);
        List<BigDecimal> supports = filterLevels(supportCandidates, current, false);

        String resistanceList = formatLevelList(resistances);
        String supportList = formatLevelList(supports);
        return new Levels(
                supportList.isBlank() ? "未知" : supportList,
                resistanceList.isBlank() ? "未知" : resistanceList
        );
    }

    private List<BigDecimal> pickSwingHighs(List<BigDecimal> highs) {
        List<BigDecimal> levels = new ArrayList<>();
        for (int i = 1; i < highs.size() - 1; i++) {
            BigDecimal prev = highs.get(i - 1);
            BigDecimal cur = highs.get(i);
            BigDecimal next = highs.get(i + 1);
            if (prev == null || cur == null || next == null) continue;
            if (cur.compareTo(prev) > 0 && cur.compareTo(next) > 0) levels.add(cur);
        }
        return levels;
    }

    private List<BigDecimal> pickSwingLows(List<BigDecimal> lows) {
        List<BigDecimal> levels = new ArrayList<>();
        for (int i = 1; i < lows.size() - 1; i++) {
            BigDecimal prev = lows.get(i - 1);
            BigDecimal cur = lows.get(i);
            BigDecimal next = lows.get(i + 1);
            if (prev == null || cur == null || next == null) continue;
            if (cur.compareTo(prev) < 0 && cur.compareTo(next) < 0) levels.add(cur);
        }
        return levels;
    }

    private List<BigDecimal> filterLevels(List<BigDecimal> candidates, BigDecimal current, boolean above) {
        if (candidates == null || candidates.isEmpty() || current == null) return List.of();
        List<BigDecimal> filtered = new ArrayList<>();
        for (BigDecimal v : candidates) {
            if (v == null) continue;
            if (above && v.compareTo(current) <= 0) continue;
            if (!above && v.compareTo(current) >= 0) continue;
            filtered.add(v);
        }
        filtered.sort((a, b) -> {
            BigDecimal da = a.subtract(current).abs();
            BigDecimal db = b.subtract(current).abs();
            return da.compareTo(db);
        });

        List<BigDecimal> deduped = new ArrayList<>();
        BigDecimal dedupePct = new BigDecimal("0.25");
        for (BigDecimal v : filtered) {
            boolean tooClose = false;
            for (BigDecimal ex : deduped) {
                BigDecimal pct = v.subtract(ex).abs()
                        .multiply(new BigDecimal("100"))
                        .divide(ex, 4, RoundingMode.HALF_UP);
                if (pct.compareTo(dedupePct) <= 0) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) deduped.add(v);
            if (deduped.size() >= 3) break;
        }
        return deduped;
    }

    private String formatLevelList(List<BigDecimal> levels) {
        if (levels == null || levels.isEmpty()) return "";
        List<String> items = new ArrayList<>();
        for (BigDecimal v : levels) {
            if (v == null) continue;
            items.add(formatDecimal(v));
        }
        return String.join(" → ", items);
    }

    private String resolveRsiText(KlineSlice m15) {
        if (m15 == null || m15.rows == null || m15.rows.size() < 20) return "N/A";
        List<BigDecimal> closes = new ArrayList<>();
        for (Map<String, Object> r : m15.rows) {
            BigDecimal c = toBigDecimal(r != null ? r.get("close") : null);
            if (c != null) closes.add(c);
        }
        if (closes.size() < 15) return "N/A";
        Double rsi = computeRsi14(closes);
        if (rsi == null) return "N/A";
        return BigDecimal.valueOf(rsi).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private Double computeRsi14(List<BigDecimal> closes) {
        if (closes == null || closes.size() < 15) return null;
        int period = 14;
        int start = closes.size() - (period + 1);
        if (start < 0) start = 0;

        double gainSum = 0.0;
        double lossSum = 0.0;
        for (int i = start + 1; i <= start + period; i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal cur = closes.get(i);
            if (prev == null || cur == null) continue;
            double diff = cur.subtract(prev).doubleValue();
            if (diff >= 0) gainSum += diff;
            else lossSum += -diff;
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private String resolveAvgVolumeText(KlineSlice m15, int n) {
        if (m15 == null || m15.rows == null || m15.rows.isEmpty() || n <= 0) return "N/A";
        List<BigDecimal> vols = new ArrayList<>();
        for (Map<String, Object> r : m15.rows) {
            BigDecimal v = toBigDecimal(r != null ? r.get("volume") : null);
            if (v != null) vols.add(v);
        }
        if (vols.isEmpty()) return "N/A";
        int take = Math.min(n, vols.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = vols.size() - take; i < vols.size(); i++) {
            BigDecimal v = vols.get(i);
            if (v != null) sum = sum.add(v);
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(take), 4, RoundingMode.HALF_UP);
        return formatDecimal(avg);
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSnapshot(Map<String, Object> ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ticker", ctx.get("ticker"));
        snapshot.put("orderbook", ctx.get("orderbook"));
        snapshot.put("recentTrades", ctx.get("recentTrades"));
        snapshot.put("positions", ctx.get("positions"));
        snapshot.put("kline1h", ctx.get("kline1h"));
        snapshot.put("kline15m", ctx.get("kline15m"));
        snapshot.put("kline1hMeta", ctx.get("kline1hMeta"));
        snapshot.put("kline15mMeta", ctx.get("kline15mMeta"));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (Exception e) {
            return String.valueOf(snapshot);
        }
    }

    private String extractQuestion(String rawInput, String normalizedSymbol) {
        String s = rawInput == null ? "" : rawInput.trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("^实时建议\\s*[:：]\\s*", "");
        s = s.replaceAll("^标的\\s*[:：]\\s*", "");
        s = s.replaceAll("^symbol\\s*[:：]\\s*", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            s = s.replace(normalizedSymbol, "").trim();
            String sym2 = normalizedSymbol.replace("-", "");
            if (!sym2.isBlank()) s = s.replace(sym2, "").trim();
        }
        return s;
    }

    private KlineSlice loadKlinesFromDb(String symbol, CandlestickIntervalEnum primary, CandlestickIntervalEnum secondary, int size) {
        Candlestick latest = resolveLatestCandlestick(symbol, primary);
        CandlestickIntervalEnum intervalUsed = primary;
        if (latest == null && secondary != null) {
            latest = resolveLatestCandlestick(symbol, secondary);
            intervalUsed = secondary;
        }
        if (latest == null || latest.getId() == null) {
            return null;
        }

        KlineParam p = KlineParam.builder()
                .symbol(latest.getSymbol())
                .klineInterval(intervalUsed)
                .endTime(latest.getId())
                .size(size)
                .build();
        List<Candlestick> list = candlestickService.listByLeId(p);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list != null) {
            for (Candlestick c : list) {
                if (c == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("time", c.getTimeStr());
                row.put("open", c.getOpenPrice());
                row.put("high", c.getHighPrice());
                row.put("low", c.getLowPrice());
                row.put("close", c.getClosePrice());
                row.put("volume", c.getVolume());
                rows.add(row);
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("symbol", latest.getSymbol());
        meta.put("interval", intervalUsed.name());
        meta.put("latestTime", latest.getTimeStr());
        meta.put("latestClose", latest.getClosePrice());
        meta.put("source", "db_kline");
        return new KlineSlice(rows, meta, latest);
    }

    private Candlestick resolveLatestCandlestick(String symbol, CandlestickIntervalEnum intervalEnum) {
        if (symbol == null || symbol.isBlank() || intervalEnum == null) return null;
        CandlestickRequest r = CandlestickRequest.builder()
                .symbol(symbol)
                .interval(intervalEnum)
                .size(1)
                .build();
        List<Candlestick> list = candlestickService.getByQry(r);
        if (list == null || list.isEmpty()) return null;
        return list.get(list.size() - 1);
    }

    private Map<String, Object> buildDbTicker(String symbol, KlineSlice m15) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("symbol", symbol);
        if (m15 != null && m15.latest != null) {
            t.put("last", m15.latest.getClosePrice());
            t.put("time", m15.latest.getTimeStr());
        } else {
            t.put("last", 0);
            t.put("time", "");
        }
        t.put("source", "db_kline");
        return t;
    }

    private static class KlineSlice {
        final List<Map<String, Object>> rows;
        final Map<String, Object> meta;
        final Candlestick latest;

        KlineSlice(List<Map<String, Object>> rows, Map<String, Object> meta, Candlestick latest) {
            this.rows = rows;
            this.meta = meta;
            this.latest = latest;
        }
    }

    private boolean isValidSymbol(String sym) {
        if (sym == null) return false;
        String s = normalizeDashes(sym);
        return s.matches("^[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?$");
    }

    private String normalizeSymbol(String symbol) {
        String s = String.valueOf(symbol == null ? "" : symbol).trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("^实时建议\\s*[:：]\\s*", "");
        s = s.replaceAll("^标的\\s*[:：]\\s*", "");
        s = s.replaceAll("^symbol\\s*[:：]\\s*", "");
        s = normalizeDashes(s);
        s = s.trim().replaceAll("\\s+", "").replace("/", "-").toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?")
                .matcher(s);
        if (m.find()) return m.group();
        if (s.matches("^[A-Z0-9]{2,12}$")) return s + "-USDT-SWAP";
        return s;
    }

    private String normalizeDashes(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        return s
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2015', '-')
                .replace('\u2212', '-')
                .replace('\uFE63', '-')
                .replace('\uFF0D', '-');
    }

    private static class AccountBalances {
        final BigDecimal availableBalance;
        final BigDecimal totalAsset;

        AccountBalances(BigDecimal availableBalance, BigDecimal totalAsset) {
            this.availableBalance = availableBalance;
            this.totalAsset = totalAsset;
        }
    }

    private static class Levels {
        final String supportList;
        final String resistanceList;

        Levels(String supportList, String resistanceList) {
            this.supportList = supportList;
            this.resistanceList = resistanceList;
        }
    }

    private static class TradeplanExtract {
        final String json;
        final int startIndex;
        final int endIndex;

        TradeplanExtract(String json, int startIndex, int endIndex) {
            this.json = json;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    public static class AdviceRecord {
        public final String adviceId;
        public final String symbol;
        public final String accountId;
        public final String naturalReport;
        public final String tradeplanRaw;
        public final Map<String, Object> tradeplan;
        public final boolean tradeplanValid;
        public final List<String> tradeplanErrors;
        public final long createdAtMs;

        public AdviceRecord(
                String adviceId,
                String symbol,
                String accountId,
                String naturalReport,
                String tradeplanRaw,
                Map<String, Object> tradeplan,
                boolean tradeplanValid,
                List<String> tradeplanErrors,
                long createdAtMs) {
            this.adviceId = adviceId;
            this.symbol = symbol;
            this.accountId = accountId;
            this.naturalReport = naturalReport;
            this.tradeplanRaw = tradeplanRaw;
            this.tradeplan = tradeplan;
            this.tradeplanValid = tradeplanValid;
            this.tradeplanErrors = tradeplanErrors != null ? tradeplanErrors : List.of();
            this.createdAtMs = createdAtMs;
        }
    }

    public static class AutoSignalConfig {
        public final boolean enabled;
        public final List<String> allowedActions;
        public final BigDecimal maxRiskPercent;
        public final Boolean onlySimulation;
        public final BigDecimal defaultSignalStrength;

        public AutoSignalConfig(Boolean enabled, List<String> allowedActions, BigDecimal maxRiskPercent, Boolean onlySimulation, BigDecimal defaultSignalStrength) {
            this.enabled = Boolean.TRUE.equals(enabled);
            this.allowedActions = allowedActions != null ? allowedActions : List.of();
            this.maxRiskPercent = maxRiskPercent;
            this.onlySimulation = onlySimulation;
            this.defaultSignalStrength = defaultSignalStrength;
        }
    }

    public static class LiveAdviceRequest {
        public Boolean stream;
        public String symbolText;
        public String accountId;
        public String robotId;
        public String interval;
        public BigDecimal leverage;
        public List<LlmGenerateController.Message> history;
    }
}
