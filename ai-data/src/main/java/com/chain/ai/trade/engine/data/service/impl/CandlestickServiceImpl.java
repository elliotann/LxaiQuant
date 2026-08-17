package com.chain.ai.trade.engine.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.mapper.CandlestickMapper;
import com.chain.ai.trade.engine.data.utils.KlineCacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;


import com.chain.ai.trade.engine.data.service.ICandlestickService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * K线数据服务实现类
 */
@Slf4j
@Service
public class CandlestickServiceImpl extends ServiceImpl<CandlestickMapper, Candlestick>
        implements ICandlestickService {

    @Autowired
    private KlineCacheHelper klineCacheHelper;

    @Override
    public Candlestick saveCandlestick(Candlestick candlestick) {
        try {
            boolean success = super.save(candlestick);
            return success ? candlestick : null;
        } catch (Exception e) {
            log.error("保存K线数据失败", e);
            return null;
        }
    }

    @Override
    public Candlestick getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<Candlestick> list() {
        return super.list();
    }

    @Override
    public boolean batchSave(List<Candlestick> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return true;
        }
        
        try {
            // 分离需要插入和更新的记录
            List<Candlestick> toInsert = new java.util.ArrayList<>();
            List<Candlestick> toUpdate = new java.util.ArrayList<>();
            
            for (Candlestick candlestick : entityList) {
                if (candlestick == null || candlestick.getSymbol() == null || 
                    candlestick.getCandlestickIntervalEnum() == null) {
                    log.warn("K线数据不完整，跳过: symbol={}, interval={}", 
                            candlestick != null ? candlestick.getSymbol() : null,
                            candlestick != null ? candlestick.getCandlestickIntervalEnum() : null);
                    continue;
                }

                // 查询数据库中是否已存在该记录（通过 id + symbol + interval + exchange 唯一确定）
                Candlestick existing = getCandlestick(
                    candlestick.getId(), 
                    candlestick.getSymbol(), 
                    candlestick.getCandlestickIntervalEnum(),
                    candlestick.getExchange()
                );
                
                if (existing != null) {
                    // 存在则更新：将新数据的字段复制到已存在的记录
                    existing.setOpenPrice(candlestick.getOpenPrice());
                    existing.setHighPrice(candlestick.getHighPrice());
                    existing.setLowPrice(candlestick.getLowPrice());
                    existing.setClosePrice(candlestick.getClosePrice());
                    existing.setVolume(candlestick.getVolume());
                    existing.setAmount(candlestick.getAmount());
                    existing.setCount(candlestick.getCount());
                    existing.setConfirm(candlestick.getConfirm());
                    existing.setTimeStr(candlestick.getTimeStr());
                    toUpdate.add(existing);
                } else {
                    // 不存在则插入
                    toInsert.add(candlestick);
                }
            }
            
            // 批量更新已存在的记录（使用复合条件：id + symbol + interval + exchange）
            if (!toUpdate.isEmpty()) {
                for (Candlestick candlestick : toUpdate) {
                    LambdaUpdateWrapper<Candlestick> updateWrapper = new LambdaUpdateWrapper<>();
                    updateWrapper.eq(Candlestick::getId, candlestick.getId())
                            .eq(Candlestick::getSymbol, candlestick.getSymbol())
                            .eq(Candlestick::getCandlestickIntervalEnum, candlestick.getCandlestickIntervalEnum())
                            .eq(Candlestick::getExchange, candlestick.getExchange())
                            .set(Candlestick::getOpenPrice, candlestick.getOpenPrice())
                            .set(Candlestick::getHighPrice, candlestick.getHighPrice())
                            .set(Candlestick::getLowPrice, candlestick.getLowPrice())
                            .set(Candlestick::getClosePrice, candlestick.getClosePrice())
                            .set(Candlestick::getVolume, candlestick.getVolume())
                            .set(Candlestick::getAmount, candlestick.getAmount())
                            .set(Candlestick::getCount, candlestick.getCount())
                            .set(Candlestick::getConfirm, candlestick.getConfirm())
                            .set(Candlestick::getTimeStr, candlestick.getTimeStr());
                    
                    boolean updated = this.update(updateWrapper);
                    if (!updated) {
                        log.warn("更新K线数据失败: id={}, symbol={}, interval={}", 
                                candlestick.getId(), candlestick.getSymbol(), candlestick.getCandlestickIntervalEnum());
                    }
                }
                log.debug("批量更新K线数据完成，数量: {}", toUpdate.size());
            }
            
            // 批量插入新记录（如果插入失败可能是主键冲突，尝试更新）
            if (!toInsert.isEmpty()) {
                try {
                    boolean insertResult = this.saveBatch(toInsert);
                    if (!insertResult) {
                        log.warn("批量插入K线数据失败，尝试逐条处理，数量: {}", toInsert.size());
                        // 如果批量插入失败，逐条处理（可能是主键冲突）
                        for (Candlestick candlestick : toInsert) {
                            try {
                                this.save(candlestick);
                            } catch (Exception e) {
                                // 如果是主键冲突，尝试更新
                                if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                                    Candlestick existing = getCandlestick(
                                        candlestick.getId(), 
                                        candlestick.getSymbol(), 
                                        candlestick.getCandlestickIntervalEnum(),
                                        candlestick.getExchange()
                                    );
                                    if (existing != null) {
                                        // 更新已存在的记录
                                        LambdaUpdateWrapper<Candlestick> updateWrapper = new LambdaUpdateWrapper<>();
                                        updateWrapper.eq(Candlestick::getId, candlestick.getId())
                                                .eq(Candlestick::getSymbol, candlestick.getSymbol())
                                                .eq(Candlestick::getCandlestickIntervalEnum, candlestick.getCandlestickIntervalEnum())
                                                .eq(Candlestick::getExchange, candlestick.getExchange())
                                                .set(Candlestick::getOpenPrice, candlestick.getOpenPrice())
                                                .set(Candlestick::getHighPrice, candlestick.getHighPrice())
                                                .set(Candlestick::getLowPrice, candlestick.getLowPrice())
                                                .set(Candlestick::getClosePrice, candlestick.getClosePrice())
                                                .set(Candlestick::getVolume, candlestick.getVolume())
                                                .set(Candlestick::getAmount, candlestick.getAmount())
                                                .set(Candlestick::getCount, candlestick.getCount())
                                                .set(Candlestick::getConfirm, candlestick.getConfirm())
                                                .set(Candlestick::getTimeStr, candlestick.getTimeStr());
                                        this.update(updateWrapper);
                                    }
                                } else {
                                    log.error("插入K线数据失败: id={}, symbol={}, interval={}", 
                                            candlestick.getId(), candlestick.getSymbol(), candlestick.getCandlestickIntervalEnum(), e);
                                }
                            }
                        }
                    } else {
                        log.debug("批量插入K线数据成功，数量: {}", toInsert.size());
                    }
                } catch (Exception e) {
                    // 如果批量插入时发生主键冲突异常，逐条处理
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                        log.warn("批量插入时发生主键冲突，改为逐条处理，数量: {}", toInsert.size());
                        for (Candlestick candlestick : toInsert) {
                            try {
                                this.save(candlestick);
                            } catch (Exception ex) {
                                // 如果是主键冲突，尝试更新
                                if (ex.getMessage() != null && ex.getMessage().contains("Duplicate entry")) {
                                    LambdaUpdateWrapper<Candlestick> updateWrapper = new LambdaUpdateWrapper<>();
                                    updateWrapper.eq(Candlestick::getId, candlestick.getId())
                                            .eq(Candlestick::getSymbol, candlestick.getSymbol())
                                            .eq(Candlestick::getCandlestickIntervalEnum, candlestick.getCandlestickIntervalEnum())
                                            .eq(Candlestick::getExchange, candlestick.getExchange())
                                            .set(Candlestick::getOpenPrice, candlestick.getOpenPrice())
                                            .set(Candlestick::getHighPrice, candlestick.getHighPrice())
                                            .set(Candlestick::getLowPrice, candlestick.getLowPrice())
                                            .set(Candlestick::getClosePrice, candlestick.getClosePrice())
                                            .set(Candlestick::getVolume, candlestick.getVolume())
                                            .set(Candlestick::getAmount, candlestick.getAmount())
                                            .set(Candlestick::getCount, candlestick.getCount())
                                            .set(Candlestick::getConfirm, candlestick.getConfirm())
                                            .set(Candlestick::getTimeStr, candlestick.getTimeStr());
                                    this.update(updateWrapper);
                                } else {
                                    log.error("处理K线数据失败: id={}, symbol={}, interval={}", 
                                            candlestick.getId(), candlestick.getSymbol(), candlestick.getCandlestickIntervalEnum(), ex);
                                }
                            }
                        }
                    } else {
                        log.error("批量插入K线数据异常，数量: {}", toInsert.size(), e);
                        return false;
                    }
                }
            }
            
            log.info("批量保存K线数据完成，更新: {} 条，插入: {} 条", toUpdate.size(), toInsert.size());

            // Redis 缓存增量更新（实盘开启时生效）
            klineCacheHelper.putAll(toUpdate);
            klineCacheHelper.putAll(toInsert);

            return true;
        } catch (Exception e) {
            log.error("批量保存K线数据失败", e);
            return false;
        }
    }

    @Override
    public boolean batchSave(List<Candlestick> entityList, CandlestickIntervalEnum intervalEnum) {
        try {
            // 设置统一的间隔类型
            entityList.forEach(candlestick -> candlestick.setCandlestickIntervalEnum(intervalEnum));
            boolean result = this.saveBatch(entityList);
            // Redis 缓存增量更新
            klineCacheHelper.putAll(entityList);
            return result;
        } catch (Exception e) {
            log.error("批量保存K线数据失败", e);
            return false;
        }
    }

    @Override
    public boolean batchSaveHistory(List<Candlestick> entityList) {
        try {
            return this.saveBatch(entityList);
        } catch (Exception e) {
            log.error("批量保存历史K线数据失败", e);
            return false;
        }
    }

    @Override
    public List<Candlestick> getByQry(CandlestickRequest request) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();

        if (request.getSymbol() != null) {
            java.util.Set<String> symbolCandidates = buildSymbolCandidates(request.getSymbol());
            queryWrapper.and(w -> {
                boolean first = true;
                for (String s : symbolCandidates) {
                    if (first) {
                        w.eq(Candlestick::getSymbol, s);
                        first = false;
                    } else {
                        w.or().eq(Candlestick::getSymbol, s);
                    }
                }
            });
        }

        if (request.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, request.getExchange());
        }

        if (request.getInterval() != null) {
            queryWrapper.eq(Candlestick::getCandlestickIntervalEnum, request.getInterval());
        }

        // 时间范围查询（id 就是 OKX 毫秒时间戳，直接做范围扫描）
        long fromMs = normalizeEpochMillis(request.getFrom());
        long toMs = normalizeEpochMillis(request.getTo());
        if (fromMs > 0 && toMs > 0) {
            queryWrapper.between(Candlestick::getId, fromMs, toMs);
        } else if (fromMs > 0) {
            queryWrapper.ge(Candlestick::getId, fromMs);
        } else if (toMs > 0) {
            queryWrapper.le(Candlestick::getId, toMs);
        }

        // 限制查询数量
        if (request.getSize() != null && request.getSize() > 0) {
            queryWrapper.last("LIMIT " + request.getSize());
        }

        queryWrapper.orderByDesc(Candlestick::getId);
        List<Candlestick> results =  this.list(queryWrapper);
        java.util.Collections.reverse(results);
        return results;
    }

    private static long normalizeEpochMillis(long epochMaybeSecondsOrMillis) {
        if (epochMaybeSecondsOrMillis <= 0) {
            return 0;
        }
        if (epochMaybeSecondsOrMillis < 1_000_000_000_000L) {
            return epochMaybeSecondsOrMillis * 1000;
        }
        return epochMaybeSecondsOrMillis;
    }

    private static java.util.Set<String> buildSymbolCandidates(String inputSymbol) {
        java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
        String symbol = inputSymbol.trim();
        if (symbol.isEmpty()) {
            return candidates;
        }
        candidates.add(symbol);

        String upper = symbol.toUpperCase();
        candidates.add(upper);

        String noDash = upper.replace("-", "");
        candidates.add(noDash);

        if (upper.endsWith("-SWAP")) {
            String base = upper.substring(0, upper.length() - "-SWAP".length());
            String baseNoDash = base.replace("-", "");
            candidates.add(base);
            candidates.add(baseNoDash);
            candidates.add(baseNoDash + "-SWAP");
            candidates.add(baseNoDash + "SWAP");
        }

        return candidates;
    }

    @Override
    public Candlestick getMaxByQry(String symbol, CandlestickIntervalEnum intervalEnum) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, symbol)
                .eq(Candlestick::getCandlestickIntervalEnum, intervalEnum)
                .orderByDesc(Candlestick::getId)
                .last("LIMIT 1");

        return this.getOne(queryWrapper);
    }

    @Override
    public List<Candlestick> listByGtId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval) {
        return listByGtId(openCandlestickId, symbol, klineInterval, 100);
    }

    @Override
    public List<Candlestick> listByGtId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval, Integer size) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, symbol)
                .eq(Candlestick::getCandlestickIntervalEnum, klineInterval)
                .gt(Candlestick::getId, openCandlestickId)
                .orderByAsc(Candlestick::getId)
                .last("LIMIT " + size);

        return this.list(queryWrapper);
    }

    @Override
    public List<Candlestick> listByLeId(Long openCandlestickId, String symbol, CandlestickIntervalEnum klineInterval, Integer size) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, symbol)
                .eq(Candlestick::getCandlestickIntervalEnum, klineInterval)
                .le(Candlestick::getId, openCandlestickId)
                .orderByDesc(Candlestick::getId)
                .last("LIMIT " + size);

        List<Candlestick> list = this.list(queryWrapper);
        // 反转列表以保持时间顺序
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    public List<Candlestick> listByLeId(KlineParam param) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, param.getSymbol())
                .eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval())
                .le(Candlestick::getId, param.getEndTime())
                .orderByDesc(Candlestick::getId)
                .last("LIMIT " + param.getSize());
        if (param.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, param.getExchange());
        }
        List<Candlestick> list = this.list(queryWrapper);
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    public List<Candlestick> listByLtId(KlineParam param) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, param.getSymbol())
                .eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval())
                .lt(Candlestick::getId, param.getEndTime())
                .orderByDesc(Candlestick::getId)
                .last("LIMIT " + param.getSize());
        if (param.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, param.getExchange());
        }

        List<Candlestick> list = this.list(queryWrapper);
        // 反转列表以保持时间顺序
        java.util.Collections.reverse(list);
        return list;
    }

    @Override
    public boolean isLastKline(Candlestick lastKline) {
        if (lastKline == null) {
            return false;
        }
        return "1".equals(lastKline.getConfirm());
    }

    @Override
    public Candlestick getCandlestick(Long id, String symbol, CandlestickIntervalEnum intervalEnum, Exchange exchange) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getId, id)
                .eq(Candlestick::getSymbol, symbol)
                .eq(Candlestick::getCandlestickIntervalEnum, intervalEnum)
                .eq(Candlestick::getExchange, exchange);

        return this.getOne(queryWrapper);
    }


    @Override
    public List<Candlestick> getKlines(KlineParam param) {
        // 纯"最新N条"查询走Redis缓存
        if (klineCacheHelper.shouldUseCache(param.isTest(), param.getStartTime(), param.getEndTime(), param.getSize())) {
            String cacheKey = klineCacheHelper.buildKey(param.getExchange(), param.getSymbol(), param.getKlineInterval());
            long cacheSize = klineCacheHelper.size(cacheKey);
            if (cacheSize >= Math.min(param.getSize(), 1200)) {
                return klineCacheHelper.getLatest(cacheKey, param.getSize());
            }
        }

        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();

        if (param.getSymbol() != null) {
            queryWrapper.eq(Candlestick::getSymbol, param.getSymbol());
        }

        if (param.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, param.getExchange());
        }

        if (param.getKlineInterval() != null) {
            queryWrapper.eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval());
        }

        // 时间范围查询 (根据ID进行查询，支持无限加载)
        if (param.getStartTime() != null && param.getEndTime() != null) {
            // 两个参数都提供：查询指定时间范围内的数据
            queryWrapper.between(Candlestick::getId, param.getStartTime(), param.getEndTime());
        } else if (param.getStartTime() != null) {
            // 只提供startTime：查询startTime之后的数据（向右加载）
            queryWrapper.gt(Candlestick::getId, param.getStartTime()); // 使用>确保只获取更新的数据
        } else if (param.getEndTime() != null) {
            // 只提供endTime：查询endTime之前的数据（向左加载历史数据）
            queryWrapper.lt(Candlestick::getId, param.getEndTime());
        }

        List<Candlestick> results;
        if (param.getSize() != null && param.getSize() > 0) {
            // 对于向左加载历史数据，需要获取最接近endTime的limit条数据
            if (param.getEndTime() != null && param.getStartTime() == null) {
                // 查询endTime之前的数据，按时间降序排列，取最新的limit条
                LambdaQueryWrapper<Candlestick> historyQuery = new LambdaQueryWrapper<>();
                if (param.getSymbol() != null) {
                    historyQuery.eq(Candlestick::getSymbol, param.getSymbol());
                }
                if (param.getKlineInterval() != null) {
                    historyQuery.eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval());
                }
                historyQuery.lt(Candlestick::getId, param.getEndTime());
                historyQuery.orderByDesc(Candlestick::getId); // 降序，获取最新的数据
                historyQuery.last("LIMIT " + param.getSize());

                List<Candlestick> tempResults = this.list(historyQuery);
                // 反转回升序
                java.util.Collections.reverse(tempResults);
                results = tempResults;
            } else {
                // 取最新limit条：降序取，反转回升序
                queryWrapper.orderByDesc(Candlestick::getId);
                queryWrapper.last("LIMIT " + param.getSize());
                List<Candlestick> temp = this.list(queryWrapper);
                java.util.Collections.reverse(temp);
                results = temp;
            }
        } else {
            // 没有数量限制，直接查询（按时间升序）
            queryWrapper.orderByAsc(Candlestick::getId);
            results = this.list(queryWrapper);
        }

        return results;
    }

    @Override
    public List<Candlestick> getLastKlines(KlineParam param) {
        // 纯"最新N条"查询走Redis缓存
        if (klineCacheHelper.shouldUseCache(param.isTest(), param.getStartTime(), param.getEndTime(), param.getSize())) {
            String cacheKey = klineCacheHelper.buildKey(param.getExchange(), param.getSymbol(), param.getKlineInterval());
            long cacheSize = klineCacheHelper.size(cacheKey);
            if (cacheSize >= Math.min(param.getSize(), 1200)) {
                return klineCacheHelper.getLatest(cacheKey, param.getSize());
            }
        }

        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();

        if (param.getSymbol() != null) {
            queryWrapper.eq(Candlestick::getSymbol, param.getSymbol());
        }

        if (param.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, param.getExchange());
        }

        if (param.getKlineInterval() != null) {
            queryWrapper.eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval());
        }
        // 确保所有查询都按时间升序排列（lightweight-charts要求）
        queryWrapper.orderByDesc(Candlestick::getId);
        queryWrapper.last("LIMIT " + param.getSize());
        List<Candlestick> result = this.list(queryWrapper);
        Collections.reverse(result);
        return result;
    }


    @Override
    public Candlestick getLastKline(String symbol, CandlestickIntervalEnum interval) {
        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Candlestick::getSymbol, symbol)
                .eq(Candlestick::getCandlestickIntervalEnum, interval)
                .orderByDesc(Candlestick::getId)
                .last("LIMIT 1");

        return this.getOne(queryWrapper);
    }

    @Override
    public List<Candlestick> getKlines4KChart(KlineParam param) {
        // 从 Redis 缓存读取（实盘 + 查询最新数据 + 数量 ≤1200）
        if (klineCacheHelper.shouldUseCache(param.isTest(), param.getStartTime(), param.getEndTime(), param.getSize())) {
            String cacheKey = klineCacheHelper.buildKey(param.getExchange(), param.getSymbol(), param.getKlineInterval());
            long cacheSize = klineCacheHelper.size(cacheKey);
            if (cacheSize >= Math.min(param.getSize(), 1200)) {
                return klineCacheHelper.getLatest(cacheKey, param.getSize());
            }
        }

        LambdaQueryWrapper<Candlestick> queryWrapper = new LambdaQueryWrapper<>();

        if (param.getSymbol() != null) {
            queryWrapper.eq(Candlestick::getSymbol, param.getSymbol());
        }

        if (param.getExchange() != null) {
            queryWrapper.eq(Candlestick::getExchange, param.getExchange());
        }

        if (param.getKlineInterval() != null) {
            queryWrapper.eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval());
        }

        // 时间范围查询 (根据ID进行查询，支持无限加载)
        if (param.getStartTime() != null && param.getEndTime() != null) {
            // 两个参数都提供：查询指定时间范围内的数据
            queryWrapper.between(Candlestick::getId, param.getStartTime(), param.getEndTime());
        } else if (param.getStartTime() != null) {
            // 只提供startTime：查询startTime之后的数据（向右加载）
            queryWrapper.gt(Candlestick::getId, param.getStartTime()); // 使用>确保只获取更新的数据
        } else if (param.getEndTime() != null) {
            // 只提供endTime：查询endTime之前的数据（向左加载历史数据）
            queryWrapper.lt(Candlestick::getId, param.getEndTime());
        }

        // 确保所有查询都按时间升序排列（lightweight-charts要求）
        queryWrapper.orderByDesc(Candlestick::getId); // 降序，获取最新的数据

        List<Candlestick> results;
        if (param.getSize() != null && param.getSize() > 0) {
            // 对于向左加载历史数据，需要获取最接近endTime的limit条数据
            if (param.getEndTime() != null && param.getStartTime() == null) {
                // 查询endTime之前的数据，按时间降序排列，取最新的limit条
                LambdaQueryWrapper<Candlestick> historyQuery = new LambdaQueryWrapper<>();
                if (param.getSymbol() != null) {
                    historyQuery.eq(Candlestick::getSymbol, param.getSymbol());
                }
                if (param.getExchange() != null) {
                    historyQuery.eq(Candlestick::getExchange, param.getExchange());
                }
                if (param.getKlineInterval() != null) {
                    historyQuery.eq(Candlestick::getCandlestickIntervalEnum, param.getKlineInterval());
                }
                historyQuery.lt(Candlestick::getId, param.getEndTime());
                historyQuery.orderByDesc(Candlestick::getId); // 降序，获取最新的数据
                historyQuery.last("LIMIT " + param.getSize());

                List<Candlestick> tempResults = this.list(historyQuery);
                // 反转回升序
                java.util.Collections.reverse(tempResults);
                results = tempResults;
            } else {
                // 其他情况：直接按升序取最新的limit条

                queryWrapper.last("LIMIT " + param.getSize());
                results = this.list(queryWrapper);
                // 反转回升序
                java.util.Collections.reverse(results);
            }
        } else {
            // 没有数量限制，直接查询
            results = this.list(queryWrapper);
        }

        return results;

    }
}
