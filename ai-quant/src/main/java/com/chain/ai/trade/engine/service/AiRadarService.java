package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.AiRadarOpportunityDTO;
import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import com.chain.ai.trade.engine.data.entity.dos.UserFavorite;
import com.chain.ai.trade.engine.data.service.ISymbolsService;
import com.chain.ai.trade.engine.data.service.IUserFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRadarService {

    private final MarketAnalysisService marketAnalysisService;
    private final ISymbolsService symbolsService;
    private final IUserFavoriteService userFavoriteService;

    /** 雷达扫描线程池（8线程），避免串行逐个分析 */
    private static final ExecutorService radarExecutor = Executors.newFixedThreadPool(8);

    private static final String DEFAULT_INTERVAL = "OKXMIN15";

    /**
     * 扫描交易机会，数据源为热门标的 + 用户自选股（双源合并去重）
     * @param userId 用户ID，为空时只扫描热门标的
     * @return 信号排序后的机会列表（最多30条）
     */
    public List<AiRadarOpportunityDTO> scanOpportunities(String userId) {
        // 1. 获取热门标的
        List<Symbol> hotSymbols = symbolsService.getHotSymbols(null);
        Set<String> symbolCodes = new HashSet<>();
        for (Symbol s : hotSymbols) {
            if (s.getSymbol() != null) {
                symbolCodes.add(s.getSymbol());
            }
        }

        // 2. 获取用户自选股（如果有 userId）
        if (userId != null && !userId.isEmpty()) {
            List<UserFavorite> favorites = userFavoriteService.getByUserId(userId);
            if (!favorites.isEmpty()) {
                Set<Integer> favSymbolIds = favorites.stream()
                        .map(UserFavorite::getSymbolId)
                        .collect(Collectors.toSet());
                // 批量查询自选股对应的 Symbol 实体
                List<Symbol> favSymbols = symbolsService.listByIds(favSymbolIds);
                for (Symbol s : favSymbols) {
                    if (s != null && s.getSymbol() != null && Boolean.TRUE.equals(s.getActive())) {
                        symbolCodes.add(s.getSymbol());
                    }
                }
            }
        }

        if (symbolCodes.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("AI雷达扫描开始，标的数量：{}", symbolCodes.size());

        // 3. 从symbols表批量查询market映射，替代正则推断
        Map<String, String> marketMap = symbolsService.listBySymbols(new ArrayList<>(symbolCodes))
                .stream()
                .filter(s -> s.getMarket() != null)
                .collect(Collectors.toMap(Symbol::getSymbol, Symbol::getMarket, (a, b) -> a));

        // 4. 并发提交每个标的的分析任务
        List<CompletableFuture<AiRadarOpportunityDTO>> futures = symbolCodes.stream()
                .map(code -> CompletableFuture.supplyAsync(() -> analyzeTicker(code, marketMap), radarExecutor))
                .collect(Collectors.toList());

        // 5. 收集结果，跳过 null（分析失败或信号不足）
        List<AiRadarOpportunityDTO> opportunities = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        log.debug("Radar analysis task failed: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 5. 按信号强度 + 24h涨跌幅绝对值排序
        opportunities.sort((a, b) -> {
            int cmp = compareStrength(b.getStrength()) - compareStrength(a.getStrength());
            if (cmp != 0) return cmp;
            return b.getChange24h().abs().compareTo(a.getChange24h().abs());
        });

        return opportunities.stream().limit(30).collect(Collectors.toList());
    }

    /** 分析单个标的，返回 null 表示跳过 */
    private AiRadarOpportunityDTO analyzeTicker(String symbolCode, Map<String, String> marketMap) {
        try {
            MarketAnalysisDTO analysis = marketAnalysisService.analyze(
                    symbolCode, DEFAULT_INTERVAL, 240);
            if (analysis == null) return null;

            String market = resolveMarket(symbolCode, marketMap);
            List<String> signals = generateSignals(analysis);
            if (signals.isEmpty()) return null;

            String signal = signals.get(0);
            String strength = computeStrength(analysis);
            String impact = computeImpact(signal, analysis);
            String reason = buildReason(signal, analysis);

            return AiRadarOpportunityDTO.builder()
                    .symbol(symbolCode)
                    .name(symbolCode)
                    .price(analysis.getPrice())
                    .change24h(analysis.getChangePercent())
                    .signal(signal)
                    .strength(strength)
                    .reason(reason)
                    .impact(impact)
                    .market(market)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();
        } catch (Exception e) {
            log.debug("Skipping symbol {}: {}", symbolCode, e.getMessage());
            return null;
        }
    }

    /** 优先从DB的market映射表获取，未命中则回退到正则推断 */
    private String resolveMarket(String symbol, Map<String, String> marketMap) {
        if (symbol != null && marketMap != null && marketMap.containsKey(symbol)) {
            return marketMap.get(symbol);
        }
        return classifyMarket(symbol);
    }

    private String classifyMarket(String symbol) {
        if (symbol == null || symbol.isBlank()) return "Crypto";
        String upper = symbol.toUpperCase();
        if (upper.endsWith("/USDT") || upper.endsWith("-USDT") || upper.endsWith("USDT")) {
            return "Crypto";
        }
        if (upper.startsWith("^") || upper.endsWith(".NS") || upper.endsWith(".SS")) {
            return "CNStock";
        }
        if (upper.endsWith(".HK")) {
            return "HKStock";
        }
        if (upper.contains("=X") || isForexPair(upper)) {
            return "Forex";
        }
        if (upper.matches("[A-Z]{1,4}")) {
            return "USStock";
        }
        return "Crypto";
    }

    private boolean isForexPair(String symbol) {
        String[] forex = {"EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD", "NZD/USD"};
        for (String pair : forex) {
            if (symbol.contains(pair.replace("/", "")) || symbol.contains(pair)) {
                return true;
            }
        }
        return false;
    }

    private List<String> generateSignals(MarketAnalysisDTO a) {
        List<String> signals = new ArrayList<>();
        if (a.getRsi14() == null) return signals;

        double rsi = a.getRsi14().doubleValue();
        if (rsi > 75) signals.add("overbought");
        else if (rsi < 25) signals.add("oversold");
        else if (rsi > 65) signals.add("overbought");
        else if (rsi < 35) signals.add("oversold");

        if ("bullish".equals(a.getTrendLabel())) {
            signals.add("bullish_momentum");
        } else if ("bearish".equals(a.getTrendLabel())) {
            signals.add("bearish_momentum");
        }

        if (signals.isEmpty()) {
            signals.add("consolidation");
        }

        return signals;
    }

    private String computeStrength(MarketAnalysisDTO a) {
        int score = 0;
        if (a.getRsi14() != null) {
            double rsi = a.getRsi14().doubleValue();
            if (rsi > 75 || rsi < 25) score += 2;
            else if (rsi > 65 || rsi < 35) score += 1;
        }
        if (a.getTrendStrength() != null) {
            score += Math.min(a.getTrendStrength() / 20, 2);
        }
        if (a.getBollingerWidthPercent() != null && a.getBollingerWidthPercent().doubleValue() > 0.05) {
            score += 1;
        }
        if (score >= 3) return "strong";
        if (score >= 2) return "medium";
        return "weak";
    }

    private String computeImpact(String signal, MarketAnalysisDTO a) {
        if ("oversold".equals(signal)) return "bullish";
        if ("overbought".equals(signal)) return "bearish";
        if ("bullish_momentum".equals(signal)) return "bullish";
        if ("bearish_momentum".equals(signal)) return "bearish";
        if (a.getSentimentScore() != null && a.getSentimentScore() > 60) return "bullish";
        if (a.getSentimentScore() != null && a.getSentimentScore() < 40) return "bearish";
        return "neutral";
    }

    private String buildReason(String signal, MarketAnalysisDTO a) {
        StringBuilder sb = new StringBuilder();
        switch (signal) {
            case "overbought":
                sb.append("RSI(").append(a.getRsi14()).append(")进入超买区");
                if (a.getBollingerWidthPercent() != null && a.getBollingerWidthPercent().doubleValue() > 0.03) {
                    sb.append("，布林带扩张");
                }
                break;
            case "oversold":
                sb.append("RSI(").append(a.getRsi14()).append(")进入超卖区");
                if (a.getBollingerWidthPercent() != null && a.getBollingerWidthPercent().doubleValue() > 0.03) {
                    sb.append("，布林带扩张");
                }
                break;
            case "bullish_momentum":
                sb.append("趋势转为看涨");
                if (a.getEma9() != null && a.getEma21() != null) {
                    sb.append("(EMA9>EMA21)");
                }
                break;
            case "bearish_momentum":
                sb.append("趋势转为看跌");
                if (a.getEma9() != null && a.getEma21() != null) {
                    sb.append("(EMA9<EMA21)");
                }
                break;
            default:
                sb.append("市场处于盘整状态");
                break;
        }
        if (a.getChangePercent() != null && a.getChangePercent().abs().doubleValue() > 2) {
            sb.append("，价格波动剧烈(").append(a.getChangePercent().setScale(2, RoundingMode.HALF_UP)).append("%)");
        }
        return sb.toString();
    }

    private int compareStrength(String s) {
        return switch (s) {
            case "strong" -> 3;
            case "medium" -> 2;
            case "weak" -> 1;
            default -> 0;
        };
    }
}
