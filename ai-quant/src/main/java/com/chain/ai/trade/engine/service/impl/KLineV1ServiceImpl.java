package com.chain.ai.trade.engine.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.engine.controller.dto.*;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.provider.impl.TestKlineDataProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.service.ISymbolsService;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.xchange.factory.ExchangeWrapFactory;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import org.apache.commons.lang3.StringUtils;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * K线数据V1版本服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KLineV1ServiceImpl implements KLineV1Service {
    
    private final ICandlestickService candlestickService;
    private final ITechnicalSignalService technicalSignalService;
    private final ITradeSignalService tradeSignalService;
    private final TestKlineDataProvider testKlineDataProvider;
    private final ITradingAccountService tradingAccountService;
    private final ISymbolsService symbolsService;
    private final ITradingBotService tradingBotService;

    /** 价格精度（小数位数） */
    private static final int PRICE_SCALE = 2;

    @Override
    public KLineHistoryResponse getKLineHistory(KLineHistoryRequest request) {
        log.info("获取K线历史数据: symbol={}, interval={}, limit={}", 
                request.getSymbol(), request.getInterval(), request.getLimit());
        
        try {
            // 转换时间间隔枚举
            CandlestickIntervalEnum interval = parseInterval(request.getInterval());
            
            // 构建查询参数
            KlineParam.KlineParamBuilder paramBuilder = KlineParam.builder()
                    .symbol(request.getSymbol())
                    .exchange(toExchangeEnum(request.getExchange()))
                    .klineInterval(interval)
                    .size(request.getLimit() != null ? request.getLimit() : 500);
            
            KlineParam queryParam = paramBuilder.build();
            
            // 查询K线数据
            List<Candlestick> candlesticks = candlestickService.getKlines4KChart(queryParam);
            
            // 如果有时间范围限制，进行过滤
            if (request.getStartTime() != null || request.getEndTime() != null) {
                candlesticks = filterByTimeRange(candlesticks, request.getStartTime(), request.getEndTime());
            }
            
            // 转换为DTO
            List<KLineDataDTO> klines = convertToDTO(candlesticks);
            
            // 构建响应
            KLineHistoryResponse response = new KLineHistoryResponse();
            response.setSymbol(request.getSymbol());
            response.setInterval(request.getInterval());
            response.setKlines(klines);
            response.setCurrentTime(System.currentTimeMillis() / 1000);
            
            log.info("K线历史数据查询完成，返回{}条数据", klines.size());
            return response;
            
        } catch (Exception e) {
            log.error("获取K线历史数据失败", e);
            throw new RuntimeException("获取K线历史数据失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public KLineLoadResponse loadKLineData(KLineLoadRequest request) {
        log.debug("加载K线数据: symbol={}, interval={}, direction={}, anchorTime={}",
                request.getSymbol(), request.getInterval(), request.getDirection(), request.getAnchorTime());
        
        try {
            CandlestickIntervalEnum interval = parseInterval(request.getInterval());
            List<Candlestick> candlesticks;
            boolean hasMore = false;
            Long nextAnchorTime = null;
            
            String exchange = request.getExchange();
            
            if ("backward".equals(request.getDirection())) {
                // 向后加载（更早的数据）
                candlesticks = loadBackwardData(request.getSymbol(), interval, 
                        request.getAnchorTime(), request.getLimit(), exchange);
                
                // 检查是否还有更早的数据
                if (!candlesticks.isEmpty()) {
                    Candlestick earliest = candlesticks.get(candlesticks.size() - 1);
                    long earliestTime = candlestickTimeSeconds(earliest);
                    hasMore = hasMoreBackwardData(request.getSymbol(), interval, earliestTime, exchange);
                    nextAnchorTime = earliestTime;
                }
            } else {
                // 向前加载（更新的数据）
                candlesticks = loadForwardData(request.getSymbol(), interval, 
                        request.getAnchorTime(), request.getLimit(), null, exchange);
                
                // 检查是否还有更新的数据
                if (!candlesticks.isEmpty()) {
                    Candlestick latest = candlesticks.get(0);
                    long latestTime = candlestickTimeSeconds(latest);
                    hasMore = hasMoreForwardData(request.getSymbol(), interval, latestTime);
                    nextAnchorTime = latestTime;
                }
            }
            
            // 转换为DTO
            List<KLineDataDTO> klineData = convertToDTO(candlesticks);
            
            // 构建响应
            KLineLoadResponse response = new KLineLoadResponse();
            response.setSymbol(request.getSymbol());
            response.setInterval(request.getInterval());
            response.setDirection(request.getDirection());
            response.setData(klineData);
            response.setHasMore(hasMore);
            response.setNextAnchorTime(nextAnchorTime);
            
            log.info("K线数据加载完成，返回{}条数据，hasMore={}", klineData.size(), hasMore);
            return response;
            
        } catch (Exception e) {
            log.error("加载K线数据失败", e);
            throw new RuntimeException("加载K线数据失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, KLineLoadResponse> loadKLineDataBatch(List<KLineLoadRequest> requests) {
        log.info("批量加载K线数据: count={}", requests.size());
        
        Map<String, KLineLoadResponse> responses = new HashMap<>();
        for (KLineLoadRequest request : requests) {
            try {
                KLineLoadResponse response = loadKLineData(request);
                String requestId = request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString();
                responses.put(requestId, response);
            } catch (Exception e) {
                log.error("批量加载K线数据失败: requestId={}", request.getRequestId(), e);
                // 继续处理其他请求
            }
        }
        
        return responses;
    }
    
    /** 获取支持的标的列表，从 symbols 表查询 active=true 的标的 */
    @Override
    public List<String> getSupportedSymbols() {
        return symbolsService.lambdaQuery()
                .eq(Symbol::getActive, true)
                .list()
                .stream()
                .map(Symbol::getSymbol)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 获取支持的标的详情（code + name），从 symbols 表查询 active=true 的标的 */
    @Override
    public List<SymbolInfoDTO> getSupportedSymbolDetails() {
        return symbolsService.lambdaQuery()
                .eq(Symbol::getActive, true)
                .list()
                .stream()
                .map(s -> new SymbolInfoDTO(s.getSymbol(), s.getName(), s.getExchange()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> searchSymbols(String keyword) {
        List<String> allSymbols = getSupportedSymbols();
        if (keyword == null || keyword.trim().isEmpty()) {
            return allSymbols;
        }
        String lowerKeyword = keyword.toLowerCase();
        return allSymbols.stream()
                .filter(symbol -> symbol.toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
    }
    
    @Override
    public KLineJumpResponse jumpToTime(KLineJumpRequest request) {
        log.info("时间跳转: symbol={}, interval={}, time={}, before={}, after={}", 
                request.getSymbol(), request.getInterval(), request.getTime(), 
                request.getBefore(), request.getAfter());
        
        try {
            CandlestickIntervalEnum intervalEnum = parseInterval(request.getInterval());
            if (intervalEnum == null) {
                log.warn("无效的时间周期: {}", request.getInterval());
                return KLineJumpResponse.builder()
                        .symbol(request.getSymbol())
                        .interval(request.getInterval())
                        .targetTime(request.getTime())
                        .klines(Collections.emptyList())
                        .currentTime(System.currentTimeMillis() / 1000)
                        .hasMoreBefore(false)
                        .hasMoreAfter(false)
                        .build();
            }
            
            // 前端传递的是秒级时间戳
            Long targetTimeSeconds = request.getTime();
            
            String exchange = request.getExchange();
            
            // 1. 查询跳转时间点之前的数据（包括跳转时间点）
            List<Candlestick> beforeEntities = loadBackwardData(
                    request.getSymbol(), 
                    intervalEnum, 
                    targetTimeSeconds, 
                    request.getBefore() != null ? request.getBefore() : 100,
                    exchange
            );
            
            // 2. 查询跳转时间点之后的数据
            // 限制查询范围：最多查询到 targetTime + 1天，避免查询到错误的数据
            long maxQueryTime = targetTimeSeconds + 86400; // 1天后
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long actualMaxTime = Math.min(maxQueryTime, currentTimeSeconds); // 不超过当前时间
            
            // 计算时间周期对应的秒数，用于扩展查询范围，确保包含目标时间
            int intervalSeconds = getIntervalSeconds(intervalEnum);
            // 向前查询时，从 targetTime - intervalSeconds 开始，确保包含目标时间附近的数据
            long forwardStartTime = targetTimeSeconds - intervalSeconds;
            
            log.info("jumpToTime: 查询after数据 - targetTime={}, forwardStartTime={}, maxQueryTime={}, currentTime={}, actualMaxTime={}", 
                    targetTimeSeconds, forwardStartTime, maxQueryTime, currentTimeSeconds, actualMaxTime);
            
            List<Candlestick> afterEntities = loadForwardData(
                    request.getSymbol(), 
                    intervalEnum, 
                    forwardStartTime, // 从目标时间前一个周期开始查询，确保包含目标时间
                    request.getAfter() != null ? request.getAfter() : 100,
                    actualMaxTime, // 传入最大查询时间
                    exchange
            );
            
            // 添加详细日志
            if (!afterEntities.isEmpty()) {
                log.info("jumpToTime: afterEntities数量={}", afterEntities.size());
                for (int i = 0; i < Math.min(5, afterEntities.size()); i++) {
                    Candlestick c = afterEntities.get(i);
                    long time = candlestickTimeSeconds(c);
                    log.info("jumpToTime: afterEntities[{}] - timeStr={}, parsedTime={} ({})", 
                            i, c.getTimeStr(), time, new java.util.Date(time * 1000));
                }
                if (afterEntities.size() > 5) {
                    Candlestick last = afterEntities.get(afterEntities.size() - 1);
                    long lastTime = candlestickTimeSeconds(last);
                    log.info("jumpToTime: afterEntities[last] - timeStr={}, parsedTime={} ({})", 
                            last.getTimeStr(), lastTime, new java.util.Date(lastTime * 1000));
                }
            }
            
            // 3. 合并数据：beforeEntities 反转后为正序（从早到晚），afterEntities 已是正序
            List<Candlestick> allEntities = new ArrayList<>();
            Collections.reverse(beforeEntities);
            allEntities.addAll(beforeEntities);
            allEntities.addAll(afterEntities);
            
            // 过滤掉时间异常的数据（时间大于当前时间的数据）
            // 使用之前定义的 currentTimeSeconds 变量
            allEntities = allEntities.stream()
                    .filter(c -> {
                        long time = candlestickTimeSeconds(c);
                        return time <= currentTimeSeconds;
                    })
                    .collect(Collectors.toList());
            
            // 4. 确保返回的数据以目标时间为中心
            // 计算需要保留的数据范围：目标时间前后各 before/after 条数据
            int beforeCount = request.getBefore() != null ? request.getBefore() : 100;
            int afterCount = request.getAfter() != null ? request.getAfter() : 100;
            
            // 查找目标时间附近的数据，优先保留目标时间前后的数据
            List<Candlestick> targetTimeEntities = allEntities.stream()
                    .filter(c -> {
                        long time = candlestickTimeSeconds(c);
                        // 保留目标时间前后各1天的数据，确保包含目标时间附近的数据
                        long timeDiff = Math.abs(time - targetTimeSeconds);
                        return timeDiff <= 86400; // 1天 = 86400秒
                    })
                    .collect(Collectors.toList());
            
            if (!targetTimeEntities.isEmpty()) {
                log.info("jumpToTime: 找到目标时间附近的数据 {} 条，目标时间={}", 
                        targetTimeEntities.size(), targetTimeSeconds);
                
                // 找到目标时间在数据中的位置（或最接近的位置）
                int targetIndex = -1;
                for (int i = 0; i < targetTimeEntities.size(); i++) {
                    long time = candlestickTimeSeconds(targetTimeEntities.get(i));
                    if (time >= targetTimeSeconds) {
                        targetIndex = i;
                        break;
                    }
                }
                
                // 如果没找到大于等于目标时间的数据，使用最后一条
                if (targetIndex == -1) {
                    targetIndex = targetTimeEntities.size();
                }
                
                // 从目标时间位置向前取 beforeCount 条，向后取 afterCount 条
                int startIndex = Math.max(0, targetIndex - beforeCount);
                int endIndex = Math.min(targetTimeEntities.size(), targetIndex + afterCount);
                
                allEntities = targetTimeEntities.subList(startIndex, endIndex);
                
                log.info("jumpToTime: 过滤后数据范围: startIndex={}, endIndex={}, count={}, 第一条时间={}, 最后一条时间={}", 
                        startIndex, endIndex, allEntities.size(),
                        allEntities.isEmpty() ? "无" : candlestickTimeSeconds(allEntities.get(0)),
                        allEntities.isEmpty() ? "无" : candlestickTimeSeconds(allEntities.get(allEntities.size() - 1)));
            } else {
                log.warn("jumpToTime: 未找到目标时间附近的数据，目标时间={}", targetTimeSeconds);
                // 如果目标时间附近没有数据，尝试返回所有查询到的数据（不进行时间过滤）
                // 这样可以确保至少返回一些历史数据
                if (allEntities.isEmpty()) {
                    log.warn("jumpToTime: allEntities为空，尝试查询数据库中最新的历史数据");
                    // 如果 allEntities 为空，说明数据库可能没有目标时间附近的数据
                    // 直接查询数据库中最新的数据（不设置时间限制，只设置limit）
                    // beforeCount 和 afterCount 已在上面定义（第266-267行）
                    int totalLimit = beforeCount + afterCount;
                    
                    // 查询最新的数据：不设置时间范围，只设置limit，按时间倒序查询
                    CandlestickRequest latestRequest = CandlestickRequest.builder()
                            .symbol(request.getSymbol())
                            .exchange(toExchangeEnum(exchange))
                            .interval(intervalEnum)
                            .size(totalLimit)
                            .build();
                    // 不设置from和to，查询所有数据，然后取最新的
                    
                    List<Candlestick> latestEntities = candlestickService.getByQry(latestRequest);
                    
                    if (!latestEntities.isEmpty()) {
                        // 按时间倒序排列（最新的在前）
                        latestEntities.sort((a, b) -> {
                            long timeA = candlestickTimeSeconds(a);
                            long timeB = candlestickTimeSeconds(b);
                            return Long.compare(timeB, timeA); // 倒序：时间大的在前
                        });
                        
                        // 取最新的 totalLimit 条数据
                        if (latestEntities.size() > totalLimit) {
                            latestEntities = latestEntities.subList(0, totalLimit);
                        }
                        
                        // 反转，变成正序（从早到晚）
                        Collections.reverse(latestEntities);
                        allEntities = latestEntities;
                        
                        log.info("jumpToTime: 查询到数据库中最新的历史数据，count={}, 第一条时间={}, 最后一条时间={}", 
                                allEntities.size(),
                                allEntities.isEmpty() ? "无" : candlestickTimeSeconds(allEntities.get(0)),
                                allEntities.isEmpty() ? "无" : candlestickTimeSeconds(allEntities.get(allEntities.size() - 1)));
                    } else {
                        log.warn("jumpToTime: 数据库中没有数据");
                    }
                } else {
                    // 如果 allEntities 不为空，但 targetTimeEntities 为空，说明目标时间太新或太旧
                    // 这种情况下，返回所有查询到的数据，让前端至少能显示一些数据
                    log.info("jumpToTime: 目标时间附近无数据，返回所有查询到的数据，count={}", allEntities.size());
                }
            }
            
            // 添加日志
            if (!allEntities.isEmpty()) {
                long firstTime = candlestickTimeSeconds(allEntities.get(0));
                long lastTime = candlestickTimeSeconds(allEntities.get(allEntities.size() - 1));
                log.info("jumpToTime: targetTime={}, 合并后数据范围=[{}, {}], count={}", 
                        targetTimeSeconds, firstTime, lastTime, allEntities.size());
            } else {
                log.warn("jumpToTime: 最终返回的数据为空，targetTime={}", targetTimeSeconds);
            }
            
            // 5. 检查数据边界
            boolean hasMoreBefore = false;
            boolean hasMoreAfter = false;
            
            if (!beforeEntities.isEmpty()) {
                Candlestick earliest = beforeEntities.get(0);
                long earliestTime = candlestickTimeSeconds(earliest);
                hasMoreBefore = hasMoreBackwardData(request.getSymbol(), intervalEnum, earliestTime, exchange);
            }
            
            if (!afterEntities.isEmpty()) {
                Candlestick latest = afterEntities.get(afterEntities.size() - 1);
                long latestTime = candlestickTimeSeconds(latest);
                hasMoreAfter = hasMoreForwardData(request.getSymbol(), intervalEnum, latestTime);
            }
            
            // 6. 转换为DTO
            List<KLineDataDTO> klineData = convertToDTO(allEntities);
            
            return KLineJumpResponse.builder()
                    .symbol(request.getSymbol())
                    .interval(request.getInterval())
                    .targetTime(request.getTime())
                    .klines(klineData)
                    .currentTime(System.currentTimeMillis() / 1000)
                    .hasMoreBefore(hasMoreBefore)
                    .hasMoreAfter(hasMoreAfter)
                    .build();
                    
        } catch (Exception e) {
            log.error("时间跳转失败: symbol={}, interval={}, time={}", 
                    request.getSymbol(), request.getInterval(), request.getTime(), e);
            return KLineJumpResponse.builder()
                    .symbol(request.getSymbol())
                    .interval(request.getInterval())
                    .targetTime(request.getTime())
                    .klines(Collections.emptyList())
                    .currentTime(System.currentTimeMillis() / 1000)
                    .hasMoreBefore(false)
                    .hasMoreAfter(false)
                    .build();
        }
    }
    
    // ========== 私有辅助方法 ==========
    
    /**
     * 解析时间间隔枚举
     */
    private CandlestickIntervalEnum parseInterval(String intervalStr) {
        if (intervalStr == null || intervalStr.trim().isEmpty()) {
            return CandlestickIntervalEnum.OKXMIN3;
        }
        try {
            return CandlestickIntervalEnum.valueOf(intervalStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的时间间隔: {}, 使用默认值 OKXMIN3", intervalStr);
            return CandlestickIntervalEnum.OKXMIN3;
        }
    }
    
    /**
     * 将后端格式的interval（如OKXMIN3）转换为前端格式（如3m）
     * 用于查询技术信号，因为数据库中存储的是前端格式
     */
    private String convertIntervalToFrontend(String backendInterval) {
        if (backendInterval == null || backendInterval.trim().isEmpty()) {
            return "3m";
        }
        
        // 映射关系：后端格式 -> 前端格式
        Map<String, String> intervalMap = new HashMap<>();
        intervalMap.put("OKXMIN1", "1m");
        intervalMap.put("OKXMIN3", "3m");
        intervalMap.put("OKXMIN5", "5m");
        intervalMap.put("OKXMIN15", "15m");
        intervalMap.put("OKXMIN30", "30m");
        intervalMap.put("OKXMIN60", "1h");
        intervalMap.put("OKX4HOUR", "4h");
        intervalMap.put("OKX1D", "1d");
        
        String upperInterval = backendInterval.toUpperCase();
        return intervalMap.getOrDefault(upperInterval, backendInterval);
    }
    
    /**
     * 转换Candlestick为DTO
     */
    private List<KLineDataDTO> convertToDTO(List<Candlestick> candlesticks) {
        return candlesticks.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换单个Candlestick为DTO
     */
    private KLineDataDTO convertToDTO(Candlestick candlestick) {
        KLineDataDTO dto = new KLineDataDTO();
        dto.setTime(candlestickTimeSeconds(candlestick));
        dto.setOpen(scalePrice(candlestick.getOpenPrice()));
        dto.setHigh(scalePrice(candlestick.getHighPrice()));
        dto.setLow(scalePrice(candlestick.getLowPrice()));
        dto.setClose(scalePrice(candlestick.getClosePrice()));
        dto.setVolume(candlestick.getVolume());
        dto.setQuoteVolume(candlestick.getAmount());
        dto.setTradeCount(candlestick.getCount() != null ? candlestick.getCount().intValue() : null);
        return dto;
    }

    /**
     * 按 {@link #PRICE_SCALE} 截取价格小数位数
     */
    private BigDecimal scalePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * 解析时间字符串为时间戳（秒）
     */
    /**
     * 获取时间周期对应的秒数
     */
    private int getIntervalSeconds(CandlestickIntervalEnum interval) {
        // 使用枚举的 minNum 字段（分钟数）转换为秒数
        if (interval != null && interval.getMinNum() != null) {
            return interval.getMinNum() * 60;
        }
        // 默认返回3分钟（180秒）
        return 180;
    }

    private long candlestickTimeSeconds(Candlestick candlestick) {
        if (candlestick == null) {
            return System.currentTimeMillis() / 1000;
        }
        Long id = candlestick.getId();
        if (id != null) {
            long v = id;
            if (v >= 1_000_000_000_000_000L) {
                return v / 1_000_000;
            }
            if (v >= 1_000_000_000_000L) {
                return v / 1000;
            }
            if (v >= 1_000_000_000L) {
                return v;
            }
        }
        return parseTimeStr(candlestick.getTimeStr());
    }
    
    private Long parseTimeStr(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return System.currentTimeMillis() / 1000;
        }
        try {
            // 假设timeStr格式为 "yyyy-MM-dd HH:mm:ss" 或时间戳字符串
            // 这里简化处理，实际应该根据实际格式解析
            if (timeStr.matches("\\d{10}")) {
                return Long.parseLong(timeStr);
            } else if (timeStr.matches("\\d{13}")) {
                return Long.parseLong(timeStr) / 1000;
            } else {
                // 尝试解析日期格式 "yyyy-MM-dd HH:mm:ss"
                try {
                    java.time.format.DateTimeFormatter formatter = 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr, formatter);
                    return dateTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
                } catch (Exception e1) {
                    // 尝试其他格式 "yyyy-MM-dd HH:mm"
                    try {
                        java.time.format.DateTimeFormatter formatter2 = 
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr, formatter2);
                        return dateTime.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
                    } catch (Exception e2) {
                        log.warn("无法解析时间字符串: {}", timeStr);
                        return System.currentTimeMillis() / 1000;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析时间字符串失败: {}", timeStr, e);
            return System.currentTimeMillis() / 1000;
        }
    }
    
    /**
     * 按时间范围过滤
     */
    private List<Candlestick> filterByTimeRange(List<Candlestick> candlesticks, 
                                                 Long startTime, Long endTime) {
        return candlesticks.stream()
                .filter(c -> {
                    long time = candlestickTimeSeconds(c);
                    if (startTime != null && time < startTime) {
                        return false;
                    }
                    if (endTime != null && time > endTime) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 向后加载数据（更早的数据）
     */
    private List<Candlestick> loadBackwardData(String symbol, CandlestickIntervalEnum interval, 
                                                 Long anchorTime, Integer limit, String exchange) {
        // 向后加载：查询anchorTime之前的数据
        // 注意：数据库查询可能按ID倒序返回，LIMIT会截取掉目标时间附近的数据
        // 所以需要根据时间范围和周期计算需要查询的数据量
        
        int intervalSeconds = getIntervalSeconds(interval);
        int finalLimit = limit != null ? limit : 100;
        
        // 计算需要查询的时间范围：limit条数据对应的时间范围，再取2倍作为缓冲
        long timeRangeSeconds = (long) finalLimit * intervalSeconds * 2;
        long queryStartTime = anchorTime != null ? anchorTime - timeRangeSeconds : 0;
        
        // 根据时间范围计算需要查询的数据量（向上取整确保足够）
        int queryLimit = (int) Math.ceil((double) timeRangeSeconds / intervalSeconds);
        
        CandlestickRequest request = CandlestickRequest.builder()
                .symbol(symbol)
                .exchange(toExchangeEnum(exchange))
                .interval(interval)
                .size(queryLimit) // 根据时间范围计算的数据量
                .build();
        
        // 设置查询时间范围
        if (anchorTime != null) {
            request.setFrom(queryStartTime * 1000); // 设置起始时间
            request.setTo(anchorTime * 1000); // 设置结束时间为anchorTime
        } else {
            request.setTo(System.currentTimeMillis());
        }
        
        log.debug("loadBackwardData: anchorTime={}, queryStartTime={}, timeRangeSeconds={}, queryLimit={}", 
                anchorTime, queryStartTime, timeRangeSeconds, queryLimit);
        
        List<Candlestick> candlesticks = candlestickService.getByQry(request);
        
        log.debug("loadBackwardData: 数据库返回数据量={}", candlesticks.size());
        
        // 过滤掉时间大于anchorTime的数据
        if (anchorTime != null) {
            candlesticks = candlesticks.stream()
                    .filter(c -> {
                        long time = candlestickTimeSeconds(c);
                        return time < anchorTime;
                    })
                    .collect(Collectors.toList());
        }
        
        log.debug("loadBackwardData: 过滤后数据量={}", candlesticks.size());
        
        // 按时间倒序排列（最新的在前，即最接近anchorTime的在前），然后取前limit条
        candlesticks.sort((a, b) -> {
            long timeA = candlestickTimeSeconds(a);
            long timeB = candlestickTimeSeconds(b);
            return Long.compare(timeB, timeA); // 倒序：时间大的在前
        });
        
        // 限制返回数量（在内存中排序后，取前finalLimit条，这些是最接近anchorTime的数据）
        // finalLimit 已在前面定义
        if (candlesticks.size() > finalLimit) {
            candlesticks = candlesticks.subList(0, finalLimit);
        }
        
        return candlesticks;
    }
    
    /**
     * 向前加载数据（更新的数据）
     */
    private List<Candlestick> loadForwardData(String symbol, CandlestickIntervalEnum interval, 
                                                Long anchorTime, Integer limit, String exchange) {
        return loadForwardData(symbol, interval, anchorTime, limit, null, exchange);
    }
    
    private List<Candlestick> loadForwardData(String symbol, CandlestickIntervalEnum interval, 
                                                Long anchorTime, Integer limit, Long maxTime, String exchange) {
        // 向前加载：查询anchorTime之后的数据
        // 注意：数据库查询可能按ID倒序返回，LIMIT会截取掉目标时间附近的数据
        // 所以需要根据时间范围和周期计算需要查询的数据量
        
        int intervalSeconds = getIntervalSeconds(interval);
        int finalLimit = limit != null ? limit : 100;
        
        // 设置结束时间：优先使用传入的maxTime，否则使用当前时间
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        long actualMaxTime = maxTime != null ? maxTime : currentTimeSeconds;
        
        // 计算需要查询的时间范围：limit条数据对应的时间范围，再取2倍作为缓冲
        long timeRangeSeconds = (long) finalLimit * intervalSeconds * 2;
        long queryEndTime = anchorTime != null ? anchorTime + timeRangeSeconds : actualMaxTime;
        queryEndTime = Math.min(queryEndTime, actualMaxTime); // 不超过实际最大时间
        
        // 根据时间范围计算需要查询的数据量（向上取整确保足够）
        long actualTimeRange = queryEndTime - (anchorTime != null ? anchorTime : 0);
        int queryLimit = (int) Math.ceil((double) actualTimeRange / intervalSeconds);
        
        CandlestickRequest request = CandlestickRequest.builder()
                  .symbol(symbol)
                  .exchange(toExchangeEnum(exchange))
                  .interval(interval)
                  .size(queryLimit) // 根据时间范围计算的数据量
                  .build();
        
        // 设置开始时间为anchorTime之后（毫秒）
        // forward方向应该查询 anchorTime 之后的数据，不包括 anchorTime 本身
        if (anchorTime != null) {
            // 从下一个周期开始查询，避免包含anchorTime本身
            request.setFrom((anchorTime + intervalSeconds) * 1000);
        } else {
            request.setFrom(0);
        }
        
        request.setTo(queryEndTime * 1000);
        
        log.debug("loadForwardData: anchorTime={}, queryStartTime={}, queryEndTime={}, actualMaxTime={}, timeRangeSeconds={}, queryLimit={}", 
                anchorTime, anchorTime != null ? anchorTime + intervalSeconds : 0, queryEndTime, actualMaxTime, timeRangeSeconds, queryLimit);
        
        List<Candlestick> candlesticks = candlestickService.getByQry(request);
        
        log.debug("loadForwardData: 数据库返回数据量={}", candlesticks.size());
        
        // 过滤掉时间异常的数据（时间大于maxTime或小于等于anchorTime的数据）
        // 注意：forward方向应该查询 anchorTime 之后的数据，不包括 anchorTime 本身
        // intervalSeconds 已在前面定义，直接使用
        
        candlesticks = candlesticks.stream()
                .filter(c -> {
                    long time = candlestickTimeSeconds(c);
                    // 只保留 time > anchorTime && time <= actualMaxTime 的数据
                    boolean valid = anchorTime == null || (time > anchorTime && time <= actualMaxTime);
                    if (!valid) {
                        log.debug("loadForwardData: 过滤掉异常时间数据: time={} ({}), anchorTime={}, maxTime={}", 
                                time, new java.util.Date(time * 1000), anchorTime, actualMaxTime);
                    }
                    return valid;
                })
                .collect(Collectors.toList());
        
        log.debug("loadForwardData: 过滤后数据量={}", candlesticks.size());
        
        // 按时间正序排列（从早到晚），然后取前limit条
        candlesticks.sort((a, b) -> {
            long timeA = candlestickTimeSeconds(a);
            long timeB = candlestickTimeSeconds(b);
            return Long.compare(timeA, timeB);
        });
        
        // 限制返回数量（在内存中排序后，取前finalLimit条）
        // finalLimit 已在前面定义
        if (candlesticks.size() > finalLimit) {
            candlesticks = candlesticks.subList(0, finalLimit);
        }
        
        // 添加日志
        if (!candlesticks.isEmpty()) {
            long firstTime = candlestickTimeSeconds(candlesticks.get(0));
            long lastTime = candlestickTimeSeconds(candlesticks.get(candlesticks.size() - 1));
            log.info("loadForwardData: anchorTime={}, maxTime={}, 返回数据范围=[{}, {}], count={}", 
                    anchorTime, actualMaxTime, firstTime, lastTime, candlesticks.size());
        } else {
            log.warn("loadForwardData: anchorTime={}, maxTime={}, 未返回任何数据", anchorTime, actualMaxTime);
        }
        
        return candlesticks;
    }
    
    /**
     * 检查是否还有更早的数据
     */
    private boolean hasMoreBackwardData(String symbol, CandlestickIntervalEnum interval, Long earliestTime, String exchange) {
        // 简化实现：检查是否有比earliestTime更早的数据
        CandlestickRequest request = CandlestickRequest.builder()
                .symbol(symbol)
                .exchange(toExchangeEnum(exchange))
                .interval(interval)
                .size(1)
                .to(earliestTime * 1000)
                .build();
        
        List<Candlestick> candlesticks = candlestickService.getByQry(request);
        return !candlesticks.isEmpty();
    }
    
    /**
     * 检查是否还有更新的数据
     */
    private boolean hasMoreForwardData(String symbol, CandlestickIntervalEnum interval, Long latestTime) {
        // 简化实现：检查是否有比latestTime更新的数据
        long currentTime = System.currentTimeMillis() / 1000;
        return latestTime < currentTime;
    }
    
    @Override
    public KLineSignalResponse getKLineSignals(KLineSignalRequest request) {
        log.debug("获取K线信号标注: symbol={}, interval={}, from={}, to={}, robotId={}, indicator={}",
                request.getSymbol(), request.getInterval(), request.getFrom(), 
                request.getTo(), request.getRobotId(), request.getIndicator());
        
        try {
            List<KLineSignalDTO> signals = new ArrayList<>();

            // 0. 如果传入了 robotId 且 indicator 为空，通过机器人查询策略 ID 作为 indicator
            String resolvedIndicator = request.getIndicator();
            if (StringUtils.isNotBlank(request.getRobotId()) && StringUtils.isBlank(resolvedIndicator)) {
                TradingBot bot = tradingBotService.getByBotId(request.getRobotId());
                if (bot != null && StringUtils.isNotBlank(bot.getStrategyId())) {
                    resolvedIndicator = bot.getStrategyId();
                    log.debug("通过 robotId={} 解析到 strategyId={}, 设置为 indicator", request.getRobotId(), resolvedIndicator);
                }
            }

            // 1. 查询技术信号
            TechnicalSignalQuery technicalQuery = new TechnicalSignalQuery();
            technicalQuery.setSymbol(request.getSymbol());
            // 只有当indicator不为空时才作为indicator过滤，否则查询所有技术信号
            if (StringUtils.isNotEmpty(resolvedIndicator)) {
                technicalQuery.setIndicator(resolvedIndicator);
            }
            // 将后端格式的interval转换为前端格式，因为数据库中存储的是前端格式（如3m） todo:周期暂时不过滤
            /*String frontendInterval = convertIntervalToFrontend(request.getInterval());
            technicalQuery.setTimeframe(frontendInterval);*/
            technicalQuery.setKlineTimestampStart(request.getFrom()*1000);
            technicalQuery.setKlineTimestampEnd(request.getTo()*1000);
            technicalQuery.setPageNum(1);
            technicalQuery.setPageSize(request.getLimit() != null ? request.getLimit() : 100);
            
            // 查询技术信号
            var technicalPageResult = technicalSignalService.pageTechnicalSignals(technicalQuery);
            List<TechnicalSignal> technicalSignals = technicalPageResult.getRecords();

            // 收集当前技术信号的ID集合和extraParams映射
            Set<Long> technicalSignalIds = technicalSignals.stream()
                    .map(TechnicalSignal::getId)
                    .collect(Collectors.toSet());
            Map<Long, String> technicalExtraParams = new HashMap<>();
            Map<Long, BigDecimal> technicalSignalStrengths = new HashMap<>();
            Map<Long, String> technicalSignalMarketTrends = new HashMap<>();
            technicalSignals.stream()
                    .filter(s -> s.getId() != null)
                    .forEach(s -> {
                        technicalExtraParams.put(s.getId(), s.getExtraParams());
                        technicalSignalStrengths.put(s.getId(), s.getSignalStrength());
                        technicalSignalMarketTrends.put(s.getId(), s.getMarketTrend());
                    });

            // 转换为DTO
            for (TechnicalSignal signal : technicalSignals) {
                // 过滤信号类型（如果指定）
                if (request.getSignalType() != null && !request.getSignalType().isEmpty()) {
                    if (!request.getSignalType().equals(signal.getTechnicalDirection())) {
                        continue;
                    }
                }

                KLineSignalDTO dto = KLineSignalDTO.builder()
                        .id(signal.getId())
                        .time(signal.getKlineTimestamp())
                        .signalType(signal.getTechnicalDirection())
                        .price(signal.getClosePrice())
                        .description(buildSignalDescription(signal))
                        .signalStrength(signal.getSignalStrength())
                        .signalSource("technical")
                        .robotId(request.getRobotId())
                        .entryType(signal.getEntryType() != null ? signal.getEntryType().name() : null)
                        .limitPrice(signal.getLimitPrice())
                        .extraParams(signal.getExtraParams())
                        .criticalLevels(parseCriticalLevelsFromExtraParams(signal.getExtraParams()))
                        .marketTrend(signal.getMarketTrend())
                        .build();
                signals.add(dto);
            }

            // 2. 查询交易信号
            if (request.getFrom() != null && request.getTo() != null) {
                String startTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(request.getFrom() * 1000));
                String endTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(request.getTo() * 1000));

                List<TradeSignal> tradeSignals = tradeSignalService.queryTradeSignalsBySymbolAndTimeRange(request.getSymbol(), startTimeStr, endTimeStr);

                // 转换为DTO
                for (TradeSignal signal : tradeSignals) {
                    // 当指定了indicator时，只显示关联到该技术信号的业务信号
                    if (StringUtils.isNotEmpty(resolvedIndicator)) {
                        if (signal.getTechnicalSignalId() == null || !technicalSignalIds.contains(signal.getTechnicalSignalId())) {
                            continue;
                        }
                    }

                    // 过滤信号类型（如果指定）
                    String signalType = convertOrderActionToSignalType(signal.getOrderAction());
                    if (request.getSignalType() != null && !request.getSignalType().isEmpty()) {
                        if (!request.getSignalType().equals(signalType)) {
                            continue;
                        }
                    }

                    String parentExtraParams = signal.getTechnicalSignalId() != null ? technicalExtraParams.get(signal.getTechnicalSignalId()) : null;
                    String parentMarketTrend = signal.getTechnicalSignalId() != null ? technicalSignalMarketTrends.get(signal.getTechnicalSignalId()) : null;

                    KLineSignalDTO dto = KLineSignalDTO.builder()
                            .id(signal.getId())
                            .time(parseKlineTimeToTimestamp(signal.getKlineTime()))
                            .signalType(signalType)
                            .price(signal.getExpectedPrice())
                            .description(buildTradeSignalDescription(signal))
                            .signalStrength(calculateSignalStrength(signal, technicalSignalStrengths))
                            .signalSource("trade")
                            .robotId(request.getRobotId())
                            .orderSn(signal.getOrderSn())
                            .status(signal.getStatus() != null ? signal.getStatus().toString() : null)
                            .entryType(signal.getEntryType())
                            .limitPrice(signal.getLimitPrice())
                            .extraParams(parentExtraParams)
                            .criticalLevels(parseCriticalLevelsFromExtraParams(parentExtraParams))
                            .marketTrend(parentMarketTrend)
                            .build();
                    signals.add(dto);
                }
            }
            
            // 按时间排序
            signals.sort(Comparator.comparing(KLineSignalDTO::getTime));
            
            // 限制返回数量
            int limit = request.getLimit() != null ? request.getLimit() : 100;
            if (signals.size() > limit) {
                signals = signals.subList(0, limit);
            }
            
            return KLineSignalResponse.builder()
                    .symbol(request.getSymbol())
                    .interval(request.getInterval())
                    .from(request.getFrom())
                    .to(request.getTo())
                    .signals(signals)
                    .total(signals.size())
                    .build();
                    
        } catch (Exception e) {
            log.error("获取K线信号标注失败", e);
            return KLineSignalResponse.builder()
                    .symbol(request.getSymbol())
                    .interval(request.getInterval())
                    .from(request.getFrom())
                    .to(request.getTo())
                    .signals(Collections.emptyList())
                    .total(0)
                    .build();
        }
    }
    
    /**
     * 构建技术信号描述
     */
    private String buildSignalDescription(TechnicalSignal signal) {
        if("LONG".equals(signal.getTechnicalDirection()) || "LB".equals(signal.getTechnicalDirection())){
            return "多";
        }
        if("SHORT".equals(signal.getTechnicalDirection()) || "SB".equals(signal.getTechnicalDirection())){
            return "空";
        }
        return signal.getTechnicalDirection() != null ? signal.getTechnicalDirection() : "";
    }
    
    /**
     * 构建交易信号描述
     */
    private String buildTradeSignalDescription(TradeSignal signal) {
        String desc = signal.getOrderAction() != null ? signal.getOrderAction().getDescription() : "";
        if (desc == null || desc.isEmpty()) {
            desc = signal.getDecisionReason();
        }
        if (desc == null || desc.isEmpty()) {
            desc = signal.getOrderAction() != null ? signal.getOrderAction().toString() : "交易信号";
        }
        if (signal.getPnlPercentage() != null) {
            desc += "(" + signal.getPnlPercentage() + ")";
        }
        return desc;
    }
    
    /**
     * 将订单操作转换为信号类型
     */
    private String convertOrderActionToSignalType(OrderAction orderAction) {
        if (orderAction == null) {
            return "UNKNOWN";
        }
        switch (orderAction) {
            case OPEN_LONG:
                return "LONG";
            case OPEN_SHORT:
                return "SHORT";
            case CLOSE_LONG:
                return "CLOSE_LONG";
            case CLOSE_SHORT:
                return "CLOSE_SHORT";
            default:
                return orderAction.toString();
        }
    }
    
    /**
     * 计算信号强度 - 优先取关联技术信号的信号强度（权重），无关联时使用仓位比例估算
     */
    private BigDecimal calculateSignalStrength(TradeSignal signal, Map<Long, BigDecimal> technicalSignalStrengths) {
        if (signal.getTechnicalSignalId() != null && technicalSignalStrengths != null) {
            BigDecimal strength = technicalSignalStrengths.get(signal.getTechnicalSignalId());
            if (strength != null) {
                return strength;
            }
        }
        int strength = 80;
        if (signal.getPositionRatio() != null) {
            strength = Math.max(50, Math.min(100, signal.getPositionRatio().multiply(new BigDecimal(100)).intValue()));
        } else if (signal.getPriority() != null) {
            strength = 45 + (signal.getPriority() * 5);
            strength = Math.max(50, Math.min(100, strength));
        }
        return BigDecimal.valueOf(strength);
    }
    
    /**
     * 从extraParams JSON中解析关键点位
     */
    private List<CriticalLevel> parseCriticalLevelsFromExtraParams(String extraParams) {
        if (extraParams == null || extraParams.isBlank()) return null;
        try {
            JSONObject root = JSONUtil.parseObj(extraParams);
            if (!root.containsKey("criticalLevels")) return null;
            JSONArray arr = root.getJSONArray("criticalLevels");
            return arr.stream()
                    .map(o -> JSONUtil.toBean((JSONObject) o, CriticalLevel.class))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析K线时间字符串为时间戳（秒）
     */
    private Long parseKlineTimeToTimestamp(String klineTime) {
        if (klineTime == null || klineTime.trim().isEmpty()) {
            return System.currentTimeMillis() / 1000;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(klineTime).getTime() / 1000;
        } catch (Exception e) {
            log.warn("解析K线时间失败: {}", klineTime);
            return System.currentTimeMillis() / 1000;
        }
    }

    @Override
    @Transactional(readOnly = false)
    public KLineImportFromExchangeResponse importFromExchange(KLineImportFromExchangeRequest request) {
        log.info("从交易所导入K线: exchange={}, symbol={}, interval={}, start={}, end={}, accountId={}",
                request.getExchange(), request.getSymbol(), request.getInterval(),
                request.getStartTime(), request.getEndTime(), request.getAccountId());
        try {
            if (request.getStartTime() >= request.getEndTime()) {
                return KLineImportFromExchangeResponse.builder()
                        .success(false)
                        .message("开始时间必须小于结束时间")
                        .importedCount(0)
                        .build();
            }
            int count;
            if (StringUtils.isNotBlank(request.getAccountId())) {
                try {
                    count = importFromExchangeWithAccount(request);
                } catch (UnsupportedOperationException e) {
                    log.warn("账户导入不支持该交易所: {}, 回退到公开 API", request.getExchange());
                    count = testKlineDataProvider.importKlinesFromExchange(
                            request.getExchange(),
                            request.getSymbol(),
                            request.getInterval(),
                            request.getStartTime(),
                            request.getEndTime());
                }
            } else {
                count = testKlineDataProvider.importKlinesFromExchange(
                        request.getExchange(),
                        request.getSymbol(),
                        request.getInterval(),
                        request.getStartTime(),
                        request.getEndTime());
            }
            if (count <= 0) {
                return KLineImportFromExchangeResponse.builder()
                        .success(false)
                        .message("未导入到K线数据，请检查交易对/周期/时间范围，或确认交易所接口是否限制历史数据范围")
                        .importedCount(0)
                        .build();
            }
            return KLineImportFromExchangeResponse.builder()
                    .success(true)
                    .message("导入完成")
                    .importedCount(count)
                    .build();
        } catch (Exception e) {
            log.error("从交易所导入K线失败", e);
            return KLineImportFromExchangeResponse.builder()
                    .success(false)
                    .message(e.getMessage() != null ? e.getMessage() : "导入失败")
                    .importedCount(0)
                    .build();
        }
    }

    /**
     * 使用指定交易账户的交易所 API 拉取历史K线并保存
     * 注意：XChange getHistoryCandle(after=ts) 实际返回「早于 ts」的 K 线（往更早翻页），
     * 因此先按「从 end 往 start」拉取所有批次，再反转后按时间正序保存。
     */
    private int importFromExchangeWithAccount(KLineImportFromExchangeRequest request) {
        TradingAccount account = tradingAccountService.getById(request.getAccountId());
        if (account == null) {
            try {
                account = tradingAccountService.getByAccountId(request.getAccountId());
            } catch (Exception ignored) {
            }
        }
        if (account == null) {
            throw new RuntimeException("未找到交易账户: " + request.getAccountId());
        }

        ExchangeTradeService exchangeService = ExchangeWrapFactory.createNoAuthExchangeTradeService(account);
        CandlestickIntervalEnum intervalEnum = parseIntervalForImport(request.getInterval());
        long startSec = request.getStartTime() / 1000;
        long endSec = request.getEndTime() / 1000;
        final int batchSize = 300;

        // 获取K线时间间隔的秒数
        long intervalSeconds = getIntervalSeconds(intervalEnum);

        long currentAfter = endSec + intervalSeconds;
        int total = 0;
        while (currentAfter > startSec) {
            CandlestickRequest req = CandlestickRequest.builder()
                    .symbol(request.getSymbol())
                    .interval(intervalEnum)
                    .to(currentAfter)
                    .from(startSec)
                    .size(batchSize)
                    .build();

            List<Candlestick> batch = exchangeService.getHistoryCandlestick(req);
            if (batch == null || batch.isEmpty()) {
                break;
            }

            List<Candlestick> filteredBatch = batch.stream()
                    .filter(candle -> candle.getId() != null)
                    .filter(candle -> {
                        long sec = candle.getId() / 1000;
                        return sec >= startSec && sec <= endSec;
                    })
                    .collect(Collectors.toList());

            if (!filteredBatch.isEmpty()) {
                candlestickService.batchSave(filteredBatch);
                total += filteredBatch.size();
            }

            long earliestIdMs = batch.stream()
                    .map(Candlestick::getId)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);

            if (earliestIdMs <= 0) {
                break;
            }

            long earliestSec = earliestIdMs / 1000;
            if (earliestSec <= startSec) {
                break;
            }
            if (earliestSec >= currentAfter) {
                currentAfter = currentAfter - 1;
            } else {
                currentAfter = earliestSec - 1;
            }

            log.debug("已获取批次: {}条, 最早时间: {}, 下一批 after(to): {}",
                    batch.size(), new Date(earliestIdMs), new Date(currentAfter * 1000));
        }

        log.info("按账户导入K线完成: accountId={}, symbol={}, interval={}, 共 {} 条",
                request.getAccountId(), request.getSymbol(), intervalEnum, total);
        return total;
    }

    @Override
    public List<TickerDTO> getLatestTickers(String interval, Integer limit) {
        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        List<String> symbols = candlestickService.lambdaQuery()
                .select(Candlestick::getSymbol)
                .groupBy(Candlestick::getSymbol)
                .list()
                .stream()
                .map(Candlestick::getSymbol)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (limit != null && limit > 0 && symbols.size() > limit) {
            symbols = symbols.subList(0, limit);
        }
        List<TickerDTO> result = new ArrayList<>();
        for (String symbol : symbols) {
            KlineParam p = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(intervalEnum)
                    .size(2)
                    .build();
            List<Candlestick> last2 = candlestickService.getLastKlines(p);
            if (last2 == null || last2.isEmpty()) {
                continue;
            }
            Candlestick latest = last2.get(last2.size() - 1);
            Candlestick prev = last2.size() > 1 ? last2.get(last2.size() - 2) : null;
            BigDecimal open = latest.getOpenPrice();
            BigDecimal close = latest.getClosePrice();
            BigDecimal high = latest.getHighPrice();
            BigDecimal low = latest.getLowPrice();
            BigDecimal volume = latest.getVolume();
            BigDecimal changePercent = null;
            if (prev != null && prev.getClosePrice() != null && prev.getClosePrice().compareTo(BigDecimal.ZERO) != 0) {
                changePercent = close.subtract(prev.getClosePrice())
                        .divide(prev.getClosePrice(), 6, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            Long ts = null;
            try {
                ts = Long.parseLong(latest.getTimeStr());
            } catch (Exception ignored) {
            }
            TickerDTO dto = TickerDTO.builder()
                    .symbol(symbol)
                    .interval(intervalEnum != null ? intervalEnum.name() : null)
                    .time(ts)
                    .open(open)
                    .close(close)
                    .high(high)
                    .low(low)
                    .volume(volume)
                    .changePercent(changePercent)
                    .build();
            result.add(dto);
        }
        return result;
    }

    private CandlestickIntervalEnum parseIntervalForImport(String intervalStr) {
        if (intervalStr == null || intervalStr.trim().isEmpty()) {
            return CandlestickIntervalEnum.OKXMIN3;
        }
        return switch (intervalStr.toLowerCase()) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN3;
        };
    }

    private static Exchange toExchangeEnum(String exchange) {
        if (exchange == null) {
            return null;
        }
        try {
            return Exchange.valueOf(exchange);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

