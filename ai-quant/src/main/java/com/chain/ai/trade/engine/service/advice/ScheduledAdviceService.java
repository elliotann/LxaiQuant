package com.chain.ai.trade.engine.service.advice;

import cn.hutool.core.util.StrUtil;
import com.chain.ai.trade.agent.service.AgentService;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.entity.TradingAdvice;
import com.chain.ai.trade.engine.mapper.TradingAdviceMapper;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledAdviceService {

    private final ICandlestickService candlestickService;
    private final ITechnicalSignalService technicalSignalService;
    private final ITradeSignalService tradeSignalService;
    private final TradingAdviceMapper tradingAdviceMapper;
    private final AgentService agentService;
    private final SignalPushService signalPushService;
    private final ObjectMapper objectMapper;

    public TechnicalSignalDTO generateForSymbol(String symbol, String accountId, String interval, BigDecimal leverage) {
        String adviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[定时建议] 开始分析 symbol={} accountId={} interval={} leverage={} adviceId={}",
                symbol, accountId, interval, leverage, adviceId);

        if (hasActiveSignal(symbol)) {
            log.info("[定时建议] symbol={} 存在未完成的交易信号，跳过本次生成", symbol);
            return null;
        }

        try {
            String userPrompt = "请对 " + symbol + " 做实时行情分析，给出交易建议。杠杆倍数：" + (leverage != null ? leverage : BigDecimal.ONE);

            String llmResponse = agentService.chat("live-advice", userPrompt, null);
            if (StrUtil.isBlank(llmResponse)) {
                log.warn("[定时建议] symbol={} AgentService返回空，跳过", symbol);
                return null;
            }

            Map<String, Object> tradeplan = parseTradeplan(llmResponse);
            if (tradeplan == null) {
                log.warn("[定时建议] symbol={} 解析tradeplan失败，跳过", symbol);
                return null;
            }

            String naturalReport = extractNaturalReport(llmResponse, tradeplan);
            storeAdvice(adviceId, symbol, naturalReport, objectMapper.writeValueAsString(tradeplan));

            return createSignal(adviceId, tradeplan, symbol, interval, leverage);

        } catch (Exception e) {
            log.error("[定时建议] symbol={} 异常", symbol, e);
            return null;
        }
    }

    private Map<String, Object> parseTradeplan(String llmResponse) {
        if (StrUtil.isBlank(llmResponse)) return null;
        String text = llmResponse.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        }
        int braceStart = text.indexOf("{");
        int braceEnd = text.lastIndexOf("}");
        if (braceStart >= 0 && braceEnd > braceStart) {
            text = text.substring(braceStart, braceEnd + 1);
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            if (parsed == null || parsed.isEmpty()) return null;
            Map<String, Object> advice = parseNested(parsed, "advice");
            if (advice == null) return null;
            String direction = getString(advice.get("direction"));
            if (!"LONG".equals(direction) && !"SHORT".equals(direction) && !"NO_TRADE".equals(direction)) {
                return null;
            }
            return parsed;
        } catch (Exception e) {
            log.warn("解析tradeplan失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractNaturalReport(String llmResponse, Map<String, Object> tradeplan) {
        Map<String, Object> advice = parseNested(tradeplan, "advice");
        if (advice != null) {
            String reason = getString(advice.get("reason"));
            if (StrUtil.isNotBlank(reason)) return reason;
        }
        String trimmed = llmResponse.trim();
        int braceStart = trimmed.indexOf("{");
        if (braceStart > 0) return trimmed.substring(0, braceStart).trim();
        return "无分析内容";
    }

    private void storeAdvice(String adviceId, String symbol, String naturalReport, String tradeplanJson) {
        try {
            TradingAdvice row = new TradingAdvice();
            row.setAdviceId(adviceId);
            row.setSymbol(symbol);
            row.setNaturalReport(naturalReport);
            row.setTradeplanJson(tradeplanJson);
            row.setCreatedAt(new Date());
            tradingAdviceMapper.insert(row);
        } catch (Exception e) {
            log.warn("存储TradingAdvice失败: {}", e.getMessage());
        }
    }

    private TechnicalSignalDTO createSignal(String adviceId, Map<String, Object> tradeplan, String symbol,
                                             String interval, BigDecimal leverage) {
        try {
            Map<String, Object> advice = parseNested(tradeplan, "advice");
            if (advice == null) return null;

            String direction = getString(advice.get("direction"));
            if (!"LONG".equals(direction) && !"SHORT".equals(direction)) return null;

            String technicalDirection = "LONG".equals(direction) ? "LONG" : "SHORT";

            OrderPriceType entryType = parseEntryType(advice);
            Candlestick kline = fetchLatestCandlestick(symbol, interval);
            BigDecimal latestPrice = kline != null ? kline.getClosePrice() : BigDecimal.ZERO;
            double signalStrength = parseSignalStrength(advice);

            TechnicalSignalDTO dto = new TechnicalSignalDTO();
            dto.setDataSource("OKX");
            dto.setSignalSource("DEEPSEEK");
            dto.setSourceAdviceId(adviceId);
            dto.setSymbol(symbol);
            dto.setTimeframe(interval);
            if (kline != null && StrUtil.isNotBlank(kline.getTimeStr())) {
                dto.setKlineTime(kline.getTimeStr());
                dto.setKlineTimestamp(DateUtil.parseTimeString(kline.getTimeStr(), System.currentTimeMillis()));
            } else {
                dto.setKlineTime(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                dto.setKlineTimestamp(System.currentTimeMillis());
            }
            dto.setIndicator("AI_STRATEGY");
            dto.setStrategyName("Scheduled_AI_Advice");
            dto.setTechnicalDirection(technicalDirection);
            dto.setSignalStrength(BigDecimal.valueOf(signalStrength));
            dto.setCurrentPrice(latestPrice);
            dto.setEntryType(entryType);
            if (OrderPriceType.LIMIT == entryType) {
                BigDecimal limitPrice = parseEntryPrice(advice);
                dto.setLimitPrice(limitPrice);
            }
            dto.setExtraParams(buildExtraParams(adviceId, tradeplan, advice, leverage));

            technicalSignalService.saveTechnicalSignal(dto);
            signalPushService.pushSignal(dto);
            log.info("[定时建议] 已创建信号 symbol={} direction={} entryType={}", symbol, direction, entryType);
            return dto;
        } catch (Exception e) {
            log.warn("[定时建议] 创建信号失败: {}", e.getMessage());
            return null;
        }
    }

    private OrderPriceType parseEntryType(Map<String, Object> advice) {
        try {
            Map<String, Object> entry = parseNested(advice, "entry");
            if (entry != null) {
                String typeStr = getString(entry.get("type")).toUpperCase();
                if ("LIMIT".equals(typeStr)) {
                    return OrderPriceType.LIMIT;
                }
            }
        } catch (Exception e) {
            log.warn("解析entry.type失败，默认MARKET: {}", e.getMessage());
        }
        return OrderPriceType.MARKET;
    }

    private BigDecimal parseEntryPrice(Map<String, Object> advice) {
        try {
            Map<String, Object> entry = parseNested(advice, "entry");
            if (entry != null && entry.get("price") instanceof Number) {
                return BigDecimal.valueOf(((Number) entry.get("price")).doubleValue());
            }
        } catch (Exception e) {
            log.warn("解析entry.price失败: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private Candlestick fetchLatestCandlestick(String symbol, String interval) {
        try {
            CandlestickIntervalEnum intervalEnum = CandlestickIntervalEnum.fromCode(interval);
            if (intervalEnum == null) intervalEnum = CandlestickIntervalEnum.OKXMIN3;
            CandlestickRequest req = CandlestickRequest.builder()
                    .symbol(symbol)
                    .interval(intervalEnum)
                    .size(1)
                    .build();
            List<Candlestick> klines = candlestickService.getByQry(req);
            if (klines != null && !klines.isEmpty()) {
                return klines.get(klines.size() - 1);
            }
        } catch (Exception e) {
            log.warn("获取最新K线失败: {}", e.getMessage());
        }
        return null;
    }

    private double parseSignalStrength(Map<String, Object> advice) {
        try {
            Object strength = advice.get("signalStrength");
            if (strength instanceof Number) {
                return ((Number) strength).doubleValue();
            }
        } catch (Exception e) {
            log.warn("解析strength失败，默认0.5: {}", e.getMessage());
        }
        return 0.5;
    }

    private String buildExtraParams(String adviceId, Map<String, Object> tradeplan, Map<String, Object> advice, BigDecimal leverage) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("adviceId", adviceId);
            params.put("tradeplan", tradeplan);
            params.put("leverage", leverage != null ? leverage.intValue() : 1);
            String reason = getString(advice.get("reason"));
            if (StrUtil.isNotBlank(reason)) params.put("reason", reason);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseNested(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return null;
    }

    private String getString(Object val) {
        if (val == null) return "";
        if (val instanceof String) return (String) val;
        return String.valueOf(val);
    }

    private boolean hasActiveSignal(String symbol) {
        List<TradeStatus> activeStatuses = List.of(TradeStatus.PENDING, TradeStatus.EXECUTING, TradeStatus.PARTIALLY_FILLED);
        for (TradeStatus status : activeStatuses) {
            List<TradeSignal> signals = tradeSignalService.queryTradeSignalsBySymbolAndStatus(symbol, status);
            if (signals != null && !signals.isEmpty()) {
                log.debug("[定时建议] symbol={} 存在状态={} 的交易信号(id={}), 跳过", symbol, status, signals.get(0).getId());
                return true;
            }
        }
        return false;
    }
}
