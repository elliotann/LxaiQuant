package com.chain.ai.trade.engine.signal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.engine.signal.entity.vo.IndicatorPerformanceVO;
import com.chain.ai.trade.engine.signal.entity.vo.SignalStatisticsVO;
import com.chain.ai.trade.engine.signal.mapper.TechnicalSignalMapper;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.chain.ai.trade.common.entity.constants.Exchange.OKX;

/**
 * 技术信号服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalSignalServiceImpl extends ServiceImpl<TechnicalSignalMapper, TechnicalSignal>
        implements ITechnicalSignalService {

    private static final String OPEN_LONG = "LONG";
    private static final String OPEN_SHORT = "SHORT";

    // 这里可以注入缓存服务、消息队列等
    // private final RedisService redisService;
    // private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTechnicalSignal(TechnicalSignalDTO signalDTO) {
        long t0 = System.currentTimeMillis();
        try {
            if (shouldVoidOnConflictingOpenSignals(signalDTO)) {
                log.warn("同K线出现多空冲突，作废技术信号: symbol={}, timeframe={}, klineTimestamp={}",
                        signalDTO.getSymbol(), signalDTO.getTimeframe(), signalDTO.getKlineTimestamp());
                log.info("信号耗时 - saveTechnicalSignal: {}ms (冲突作废)", System.currentTimeMillis() - t0);
                return null;
            }

            // 生成信号哈希
            String signalHash = generateSignalHash(signalDTO);

            // 检查是否已存在
            if (existsByHash(signalHash)) {
                TechnicalSignal existing = getByHash(signalHash);
                String incomingExtra = signalDTO.getExtraParams();
                if (existing == null) {
                    log.info("信号耗时 - saveTechnicalSignal: {}ms (existByHash-null)", System.currentTimeMillis() - t0);
                    return null;
                }
                if (shouldOverwriteExtraParams(existing.getExtraParams(), incomingExtra)) {
                    existing.setExtraParams(incomingExtra);
                    boolean updated = this.updateById(existing);
                    if (updated) {
                        cacheTechnicalSignal(existing, 3600L);
                        log.info("技术信号已存在，更新 extraParams 成功: id={}, hash={}", existing.getId(), signalHash);
                    }
                }
                log.info("信号耗时 - saveTechnicalSignal: {}ms (已存在)", System.currentTimeMillis() - t0);
                return existing.getId();
            }

            // 语义去重：30分钟窗口内，相同symbol+direction且价格差异<1%则更新
            /*TechnicalSignal deduped = deduplicateBySemantic(signalDTO);
            if (deduped != null) {
                log.info("信号耗时 - saveTechnicalSignal: {}ms (语义去重)", System.currentTimeMillis() - t0);
                return deduped.getId();
            }*/

            // 转换为实体
            TechnicalSignal signal = convertToEntity(signalDTO);
            signal.setSignalHash(signalHash);
            if (StringUtils.isBlank(signal.getDataSource())) {
                signal.setDataSource(OKX.getName());
            } else {
                signal.setDataSource(signal.getDataSource().trim());
            }
            signal.setCreateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));

            // 保存到数据库
            boolean saved = this.save(signal);
            if (saved) {
                // 缓存信号
                cacheTechnicalSignal(signal, 3600L); // 缓存1小时

                Long id = signal.getId();
                if (id == null) {
                    TechnicalSignal inserted = getByHash(signalHash);
                    if (inserted != null) {
                        cacheTechnicalSignal(inserted, 3600L);
                        id = inserted.getId();
                    }
                }

                log.info("技术信号保存成功: id={}, symbol={}, indicator={}",
                        id, signal.getSymbol(), signal.getIndicator());
                log.info("信号耗时 - saveTechnicalSignal: {}ms (新增)", System.currentTimeMillis() - t0);
                return id;
            } else {
                log.error("技术信号保存失败: {}", signalDTO);
                log.info("信号耗时 - saveTechnicalSignal: {}ms (保存失败)", System.currentTimeMillis() - t0);
                return null;
            }
        } catch (Exception e) {
            log.error("保存技术信号异常", e);
            throw new RuntimeException("保存技术信号失败", e);
        }
    }

    private boolean isMeaningfulExtraParams(String extraParams) {
        if (StringUtils.isBlank(extraParams)) {
            return false;
        }
        String v = extraParams.trim();
        if ("null".equalsIgnoreCase(v)) {
            return false;
        }
        return true;
    }

    private boolean shouldOverwriteExtraParams(String existingExtraParams, String incomingExtraParams) {
        if (!isMeaningfulExtraParams(incomingExtraParams)) {
            return false;
        }
        if (!isMeaningfulExtraParams(existingExtraParams)) {
            return true;
        }
        JSONObject incoming = parseExtraParams(incomingExtraParams);
        JSONObject existing = parseExtraParams(existingExtraParams);
        if (incoming == null || existing == null) {
            return false;
        }
        boolean incomingHasSmc = incoming.containsKey("smc") && incoming.get("smc") != null;
        boolean existingHasSmc = existing.containsKey("smc") && existing.get("smc") != null;
        if (incomingHasSmc && !existingHasSmc) {
            return true;
        }
        boolean incomingHasTp = incoming.containsKey("optimalTakeProfit") && incoming.get("optimalTakeProfit") != null;
        boolean existingHasTp = existing.containsKey("optimalTakeProfit") && existing.get("optimalTakeProfit") != null;
        if (incomingHasTp && !existingHasTp) {
            return true;
        }
        boolean incomingHasSl = incoming.containsKey("optimalStopLoss") && incoming.get("optimalStopLoss") != null;
        boolean existingHasSl = existing.containsKey("optimalStopLoss") && existing.get("optimalStopLoss") != null;
        return incomingHasSl && !existingHasSl;
    }

    private JSONObject parseExtraParams(String extraParams) {
        if (!isMeaningfulExtraParams(extraParams)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(extraParams);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchSaveTechnicalSignals(List<TechnicalSignalDTO> signalDTOList) {
        if (signalDTOList == null || signalDTOList.isEmpty()) {
            return 0;
        }

        try {
            Set<String> conflictingKeys = findIncomingOpenSignalConflicts(signalDTOList);
            if (!conflictingKeys.isEmpty()) {
                conflictingKeys.forEach(this::deleteExistingOpenSignalsByKey);
            }

            List<TechnicalSignal> signals = signalDTOList.stream()
                    .filter(dto -> !conflictingKeys.contains(openSignalKey(dto)))
                    .filter(dto -> !shouldVoidOnConflictingOpenSignals(dto))
                    .map(dto -> {
                        TechnicalSignal signal = convertToEntity(dto);
                        signal.setSignalHash(generateSignalHash(dto));
                        signal.setCreateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
                        return signal;
                    })
                    .filter(signal -> !existsByHash(signal.getSignalHash()))
                    .collect(Collectors.toList());

            if (signals.isEmpty()) {
                log.debug("所有信号都已存在，跳过批量保存");
                return 0;
            }

            boolean saved = this.saveBatch(signals);
            if (saved) {
                log.info("批量保存技术信号成功: {} 条", signals.size());

                // 批量缓存
                signals.forEach(signal -> cacheTechnicalSignal(signal, 3600L));

                return signals.size();
            } else {
                log.error("批量保存技术信号失败");
                return 0;
            }
        } catch (Exception e) {
            log.error("批量保存技术信号异常", e);
            throw new RuntimeException("批量保存技术信号失败", e);
        }
    }

    /*private TechnicalSignal deduplicateBySemantic(TechnicalSignalDTO dto) {
        String dir = dto.getTechnicalDirection();
        if (StringUtils.isBlank(dir) || (!"LONG".equals(dir) && !"SHORT".equals(dir) && !"LB".equals(dir) && !"SB".equals(dir))) {
            return null;
        }
        BigDecimal incomingPrice = dto.getCurrentPrice();
        if (incomingPrice == null || incomingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(30);
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSymbol, dto.getSymbol());
        wrapper.eq(TechnicalSignal::getTechnicalDirection, dir);
        wrapper.ge(TechnicalSignal::getCreateTime, since);
        wrapper.orderByDesc(TechnicalSignal::getCreateTime);
        wrapper.last("LIMIT 5");
        List<TechnicalSignal> recent = this.list(wrapper);
        for (TechnicalSignal existing : recent) {
            BigDecimal existingPrice = existing.getClosePrice();
            if (existingPrice == null || existingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal diff = incomingPrice.subtract(existingPrice).abs()
                    .divide(existingPrice, 4, java.math.RoundingMode.HALF_UP);
            if (diff.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                BigDecimal newStrength = dto.getSignalStrength();
                BigDecimal oldStrength = existing.getSignalStrength();
                if (newStrength != null && oldStrength != null && newStrength.compareTo(oldStrength) > 0) {
                    existing.setSignalStrength(newStrength);
                }
                existing.setCreateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
                this.updateById(existing);
                cacheTechnicalSignal(existing, 3600L);
                log.info("信号语义去重命中，更新已有信号: id={}, symbol={}, direction={}, strength={}",
                        existing.getId(), existing.getSymbol(), existing.getTechnicalDirection(), existing.getSignalStrength());
                return existing;
            }
        }
        return null;
    }*/

    private boolean shouldVoidOnConflictingOpenSignals(TechnicalSignalDTO dto) {
        if (dto == null) {
            return false;
        }
        if (isDeepseekSignal(dto)) {
            return false;
        }
        String dir = normalizeDirection(dto.getTechnicalDirection());
        if (!isOpenDirection(dir)) {
            return false;
        }
        if (StringUtils.isBlank(dto.getSymbol()) || StringUtils.isBlank(dto.getTimeframe()) || dto.getKlineTimestamp() == null) {
            return false;
        }

        String opposite = oppositeOpenDirection(dir);
        if (opposite == null) {
            return false;
        }

        LambdaQueryWrapper<TechnicalSignal> oppositeQuery = new LambdaQueryWrapper<>();
        oppositeQuery.eq(TechnicalSignal::getSymbol, dto.getSymbol())
                .eq(TechnicalSignal::getTimeframe, dto.getTimeframe())
                .eq(TechnicalSignal::getKlineTimestamp, dto.getKlineTimestamp())
                .eq(TechnicalSignal::getTechnicalDirection, opposite);

        boolean hasOpposite = this.count(oppositeQuery) > 0;
        if (!hasOpposite) {
            return false;
        }

        deleteExistingOpenSignals(dto.getSymbol(), dto.getTimeframe(), dto.getKlineTimestamp());
        return true;
    }

    private void deleteExistingOpenSignalsByKey(String key) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        String[] parts = key.split("\\|", -1);
        if (parts.length != 3) {
            return;
        }
        String symbol = parts[0];
        String timeframe = parts[1];
        Long klineTimestamp;
        try {
            klineTimestamp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        deleteExistingOpenSignals(symbol, timeframe, klineTimestamp);
    }

    private void deleteExistingOpenSignals(String symbol, String timeframe, Long klineTimestamp) {
        LambdaQueryWrapper<TechnicalSignal> deleteQuery = new LambdaQueryWrapper<>();
        deleteQuery.eq(TechnicalSignal::getSymbol, symbol)
                .eq(TechnicalSignal::getTimeframe, timeframe)
                .eq(TechnicalSignal::getKlineTimestamp, klineTimestamp)
                .in(TechnicalSignal::getTechnicalDirection, Arrays.asList(OPEN_LONG, OPEN_SHORT));
        this.remove(deleteQuery);
    }

    private Set<String> findIncomingOpenSignalConflicts(List<TechnicalSignalDTO> dtos) {
        Map<String, Set<String>> keyToDirs = new HashMap<>();
        for (TechnicalSignalDTO dto : dtos) {
            if (isDeepseekSignal(dto)) {
                continue;
            }
            String dir = normalizeDirection(dto.getTechnicalDirection());
            if (!isOpenDirection(dir)) {
                continue;
            }
            String key = openSignalKey(dto);
            if (key == null) {
                continue;
            }
            keyToDirs.computeIfAbsent(key, k -> new HashSet<>()).add(dir);
        }
        return keyToDirs.entrySet().stream()
                .filter(e -> e.getValue().contains(OPEN_LONG) && e.getValue().contains(OPEN_SHORT))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private boolean isDeepseekSignal(TechnicalSignalDTO dto) {
        if (dto == null) {
            return false;
        }
        String src = dto.getSignalSource();
        if (StringUtils.isBlank(src)) {
            return false;
        }
        return "DEEPSEEK".equalsIgnoreCase(src.trim());
    }

    private String openSignalKey(TechnicalSignalDTO dto) {
        if (dto == null) {
            return null;
        }
        if (StringUtils.isBlank(dto.getSymbol()) || StringUtils.isBlank(dto.getTimeframe()) || dto.getKlineTimestamp() == null) {
            return null;
        }
        return dto.getSymbol() + "|" + dto.getTimeframe() + "|" + dto.getKlineTimestamp();
    }

    private boolean isOpenDirection(String dir) {
        return OPEN_LONG.equals(dir) || OPEN_SHORT.equals(dir);
    }

    private String oppositeOpenDirection(String dir) {
        if (OPEN_LONG.equals(dir)) {
            return OPEN_SHORT;
        }
        if (OPEN_SHORT.equals(dir)) {
            return OPEN_LONG;
        }
        return null;
    }

    private String normalizeDirection(String dir) {
        if (StringUtils.isBlank(dir)) {
            return null;
        }
        return dir.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String generateSignalHash(TechnicalSignalDTO signalDTO) {
        try {
            String signalSource = signalDTO != null ? signalDTO.getSignalSource() : null;
            String content;
            if ("DEEPSEEK".equalsIgnoreCase(signalSource)) {
                String symbol = signalDTO.getSymbol() == null ? "" : signalDTO.getSymbol();
                String timeframe = signalDTO.getTimeframe() == null ? "" : signalDTO.getTimeframe();
                String klineTime = signalDTO.getKlineTime() == null ? "" : signalDTO.getKlineTime();
                String direction = signalDTO.getTechnicalDirection() == null ? "" : signalDTO.getTechnicalDirection();
                content = symbol + timeframe + klineTime + "DEEPSEEK" + direction;
            } else {
                content = String.format("%s:%s:%s:%s:%s",
                        signalDTO.getSymbol(),
                        signalDTO.getTimeframe(),
                        signalDTO.getKlineTime(),
                        signalDTO.getIndicator(),
                        signalDTO.getStrategyName());
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成信号哈希失败", e);
            throw new RuntimeException("生成信号哈希失败", e);
        }
    }

    @Override
    public Boolean existsByHash(String signalHash) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSignalHash, signalHash);
        return this.count(wrapper) > 0;
    }

    @Override
    public TechnicalSignal getByHash(String signalHash) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSignalHash, signalHash);
        return this.getOne(wrapper);
    }

    @Override
    public IPage<TechnicalSignal> pageTechnicalSignals(TechnicalSignalQuery query) {
        try {

            Page<TechnicalSignal> page = new Page<>(query.getPageNum(), query.getPageSize());

            LambdaQueryWrapper<TechnicalSignal> wrapper = buildQueryWrapper(query);

            // 设置排序
            if ("desc".equalsIgnoreCase(query.getOrderDirection())) {
                wrapper.orderByDesc(TechnicalSignal::getCreateTime);
            } else {
                wrapper.orderByAsc(TechnicalSignal::getCreateTime);
            }
            IPage<TechnicalSignal> result = this.page(page, wrapper);
            return result;
        } catch (Exception e) {
            System.out.println("❌ [TechnicalSignalServiceImpl] 分页查询执行出错: " + e.getMessage());
            e.printStackTrace();
            // 返回空的分页结果
            Page<TechnicalSignal> emptyPage = new Page<>(query.getPageNum(), query.getPageSize());
            emptyPage.setRecords(new ArrayList<>());
            emptyPage.setTotal(0);
            return emptyPage;
        }
    }

    @Override
    public List<TechnicalSignal> getSignalsByTimeRange(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        long startTs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTs = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSymbol, symbol)
                .between(TechnicalSignal::getKlineTimestamp, startTs, endTs)
                .gt(TechnicalSignal::getSignalStrength, BigDecimal.ZERO)   // 新增：信号强度 > 0
                .orderByDesc(TechnicalSignal::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<TechnicalSignal> getOpenSignalsByStrategy(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime) {
        long startTs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTs = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSymbol, symbol)
                .eq(TechnicalSignal::getIndicator, indicator)
                .in(TechnicalSignal::getTechnicalDirection, OPEN_LONG, OPEN_SHORT)
                .between(TechnicalSignal::getKlineTimestamp, startTs, endTs)
                .gt(TechnicalSignal::getSignalStrength, BigDecimal.ZERO)
                .orderByAsc(TechnicalSignal::getKlineTimestamp);
        return this.list(wrapper);
    }

    @Override
    public List<TechnicalSignal> getLatestSignals(String symbol, String indicator, Integer limit) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        if (symbol != null) {
            wrapper.eq(TechnicalSignal::getSymbol, symbol);
        }
        if (indicator != null) {
            wrapper.eq(TechnicalSignal::getIndicator, indicator);
        }
        wrapper.orderByDesc(TechnicalSignal::getKlineTimestamp)
                .last("LIMIT " + limit);
        return this.list(wrapper);
    }

    @Override
    public List<TechnicalSignal> listTechnicalSignals(TechnicalSignalQuery query) {
        try {
            System.out.println("🔍 [TechnicalSignalServiceImpl] 开始执行查询...");
            LambdaQueryWrapper<TechnicalSignal> wrapper = buildQueryWrapper(query);
            wrapper.orderByDesc(TechnicalSignal::getCreateTime);

            System.out.println("🔍 [TechnicalSignalServiceImpl] 执行数据库查询...");
            List<TechnicalSignal> result = this.list(wrapper);

            System.out.println("🔍 [TechnicalSignalServiceImpl] 查询结果数量: " + result.size());
            if (result.size() > 0) {
                System.out.println("🔍 [TechnicalSignalServiceImpl] 第一个结果:");
                TechnicalSignal first = result.get(0);
                System.out.println("  - id: " + first.getId());
                System.out.println("  - symbol: " + first.getSymbol());
                System.out.println("  - createTime: " + first.getCreateTime());
                System.out.println("  - klineTimestamp: " + first.getKlineTimestamp());
                System.out.println("  - technicalDirection: " + first.getTechnicalDirection());
            } else {
                System.out.println("⚠️ [TechnicalSignalServiceImpl] 查询结果为空");
            }

            return result;
        } catch (Exception e) {
            System.out.println("❌ [TechnicalSignalServiceImpl] 查询执行出错: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public List<TechnicalSignal> getSignalsByDirection(String symbol, String indicator, String direction, Integer limit) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getSymbol, symbol)
                .eq(TechnicalSignal::getIndicator, indicator)
                .eq(TechnicalSignal::getTechnicalDirection, direction)
                .orderByDesc(TechnicalSignal::getCreateTime)
                .last("LIMIT " + limit);
        return this.list(wrapper);
    }

    @Override
    public SignalStatisticsVO getSignalStatistics(TechnicalSignalQuery query) {
        // 这里实现具体的统计逻辑
        // 为了简化，这里只返回一个基本的统计对象
        SignalStatisticsVO statistics = new SignalStatisticsVO();
        statistics.setSymbol(query.getSymbol());
        statistics.setStartTime(query.getStartTime());
        statistics.setEndTime(query.getEndTime());

        // 计算总信号数量
        LambdaQueryWrapper<TechnicalSignal> wrapper = buildQueryWrapper(query);
        Long totalSignals = this.count(wrapper);
        statistics.setTotalSignals(totalSignals);

        return statistics;
    }

    @Override
    public BigDecimal calculateIndicatorHitRate(String symbol, String indicator, Integer lookbackPeriod) {
        // 计算指标命中率的逻辑
        // 这里需要根据业务逻辑实现具体的计算方法
        return BigDecimal.valueOf(0.75); // 示例返回值
    }

    @Override
    public Map<String, Integer> getSignalStrengthDistribution(String symbol, String indicator) {
        // 计算信号强度分布的逻辑
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("0-0.2", 10);
        distribution.put("0.2-0.4", 25);
        distribution.put("0.4-0.6", 40);
        distribution.put("0.6-0.8", 20);
        distribution.put("0.8-1.0", 5);
        return distribution;
    }

    @Override
    public List<IndicatorPerformanceVO> analyzeIndicatorPerformance(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        // 分析指标性能的逻辑
        List<IndicatorPerformanceVO> performances = new ArrayList<>();
        // 这里应该实现具体的性能分析算法
        return performances;
    }

    @Override
    public BigDecimal calculateSignalCorrelation(String indicator1, String indicator2, String symbol) {
        // 计算信号相关性的逻辑
        return BigDecimal.valueOf(0.65); // 示例返回值
    }

    @Override
    public Boolean canGenerateTradeSignal(Long technicalSignalId) {
        TechnicalSignal signal = this.getById(technicalSignalId);
        if (signal == null) {
            return false;
        }
        // 检查信号强度等条件
        return signal.getSignalStrength() != null &&
               signal.getSignalStrength().compareTo(BigDecimal.valueOf(0.5)) > 0;
    }

    @Override
    public String getSignalBrief(Long technicalSignalId) {
        TechnicalSignal signal = this.getById(technicalSignalId);
        if (signal == null) {
            return "信号不存在";
        }
        return String.format("%s-%s-%s-强度:%.2f",
                signal.getIndicator(),
                signal.getTechnicalDirection(),
                signal.getStrategyName(),
                signal.getSignalStrength());
    }

    @Override
    public void markRejected(Long signalId) {
        if (signalId == null) {
            return;
        }
        TechnicalSignal signal = new TechnicalSignal();
        signal.setId(signalId);
        signal.setSignalStrength(BigDecimal.ZERO);
        this.updateById(signal);
        log.info("信号已标记为拒绝（signalStrength=0）: signalId={}", signalId);
    }

    @Override
    public Map<String, Object> extractSignalFeatures(Long technicalSignalId) {
        TechnicalSignal signal = this.getById(technicalSignalId);
        Map<String, Object> features = new HashMap<>();
        if (signal != null) {
            features.put("indicator", signal.getIndicator());
            features.put("direction", signal.getTechnicalDirection());
            features.put("strength", signal.getSignalStrength());
            features.put("timeframe", signal.getTimeframe());
        }
        return features;
    }

    @Override
    public Boolean validateSignal(Long technicalSignalId) {
        TechnicalSignal signal = this.getById(technicalSignalId);
        if (signal == null) {
            return false;
        }
        // 验证信号的完整性和有效性
        return signal.getSymbol() != null &&
               signal.getIndicator() != null &&
               signal.getTechnicalDirection() != null &&
               signal.getSignalStrength() != null;
    }

    @Override
    public Map<String, Long> groupSignalsByTime(String symbol, String timeframe, LocalDateTime startTime, LocalDateTime endTime) {
        // 按时间分组的逻辑
        Map<String, Long> result = new HashMap<>();
        // 这里应该实现具体的分组统计逻辑
        return result;
    }

    @Override
    public Map<String, Long> groupSignalsByIndicator(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        // 按指标分组的逻辑
        Map<String, Long> result = new HashMap<>();
        // 这里应该实现具体的分组统计逻辑
        return result;
    }

    @Override
    public Map<String, Long> groupSignalsByDirection(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime) {
        // 按方向分组的逻辑
        Map<String, Long> result = new HashMap<>();
        // 这里应该实现具体的分组统计逻辑
        return result;
    }

    @Override
    public void cacheTechnicalSignal(TechnicalSignal signal, Long expireSeconds) {
        // 缓存逻辑
        // redisService.set("technical_signal:" + signal.getSignalHash(), signal, expireSeconds);
        log.debug("缓存技术信号: {}", signal.getSignalHash());
    }

    @Override
    public TechnicalSignal getTechnicalSignalFromCache(String signalHash) {
        // 从缓存获取的逻辑
        // return redisService.get("technical_signal:" + signalHash);
        return null;
    }

    @Override
    public void evictTechnicalSignalFromCache(String signalHash) {
        // 清除缓存的逻辑
        // redisService.delete("technical_signal:" + signalHash);
        log.debug("清除缓存中的技术信号: {}", signalHash);
    }

    @Override
    public TechnicalSignal getLatestSignalFromCache(String symbol, String indicator) {
        // 获取缓存中最新信号的逻辑
        return null;
    }

    @Override
    public Long monitorSignalFrequency(String indicator, Integer windowMinutes) {
        // 监控信号生成频率的逻辑
        return 0L;
    }

    @Override
    public List<TechnicalSignal> detectAnomalousSignals(String symbol, BigDecimal threshold) {
        // 检测异常信号的逻辑
        return new ArrayList<>();
    }

    @Override
    public void triggerSignalAlarm(TechnicalSignal signal, String alarmType) {
        // 触发信号告警的逻辑
        log.warn("触发信号告警: {} - {}", alarmType, signal.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchDeleteExpiredSignals(Integer expireDays) {
        Date expireTime = Date.from(LocalDateTime.now().minusDays(expireDays).atZone(ZoneId.systemDefault()).toInstant());
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(TechnicalSignal::getCreateTime, expireTime);
        return Math.toIntExact(this.baseMapper.delete(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchUpdateSignalStatus(List<Long> signalIds, String status) {
        // 批量更新状态的逻辑（如果有状态字段的话）
        return 0;
    }

    @Override
    public String batchExportSignals(TechnicalSignalQuery query, String format) {
        // 批量导出信号数据的逻辑
        return "export_path";
    }

    @Override
    public Map<String, Object> checkDataIntegrity(LocalDateTime startTime, LocalDateTime endTime) {
        // 检查数据完整性的逻辑
        Map<String, Object> report = new HashMap<>();
        report.put("totalRecords", 0L);
        report.put("missingData", 0L);
        report.put("integrityScore", 1.0);
        return report;
    }

    @Override
    public Boolean repairAbnormalData(Long signalId) {
        // 修复异常数据的逻辑
        return true;
    }

    @Override
    public Map<String, Boolean> validateSignalConsistency(TechnicalSignal signal) {
        // 验证信号一致性的逻辑
        Map<String, Boolean> result = new HashMap<>();
        result.put("hashConsistency", true);
        result.put("dataConsistency", true);
        result.put("timeConsistency", true);
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private TechnicalSignal convertToEntity(TechnicalSignalDTO dto) {
        TechnicalSignal signal = new TechnicalSignal();
        BeanUtils.copyProperties(dto, signal);
        return signal;
    }

    private LambdaQueryWrapper<TechnicalSignal> buildQueryWrapper(TechnicalSignalQuery query) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();

        System.out.println("🔍 [TechnicalSignalServiceImpl] 构建查询条件:");
        System.out.println("  - symbol: " + query.getSymbol());
        System.out.println("  - indicator: " + query.getIndicator());
        System.out.println("  - timeframe: " + query.getTimeframe());
        System.out.println("  - klineTimestampStart: " + query.getKlineTimestampStart());
        System.out.println("  - klineTimestampEnd: " + query.getKlineTimestampEnd());

        if (StringUtils.isNotEmpty(query.getSymbol())) {
            wrapper.eq(TechnicalSignal::getSymbol, query.getSymbol());
        }
        if (query.getTimeframe() != null) {
            wrapper.eq(TechnicalSignal::getTimeframe, query.getTimeframe());
        }
        if (StringUtils.isNotEmpty(query.getIndicator())) {
            wrapper.eq(TechnicalSignal::getIndicator, query.getIndicator());
        }
        if (query.getStrategyName() != null) {
            wrapper.eq(TechnicalSignal::getStrategyName, query.getStrategyName());
        }
        if (StringUtils.isNotEmpty(query.getTechnicalDirection())) {
            wrapper.eq(TechnicalSignal::getTechnicalDirection, query.getTechnicalDirection());
        }
        if (query.getDataSource() != null) {
            wrapper.eq(TechnicalSignal::getDataSource, query.getDataSource());
        }
        // 时间戳范围查询（直接比较毫秒级时间戳，因为数据库中存储的就是毫秒级时间戳）
        if (query.getKlineTimestampStart() != null) {
            // 直接使用毫秒级时间戳进行比较
            System.out.println("🔍 [TechnicalSignalServiceImpl] 时间范围查询 - 开始时间戳: " + query.getKlineTimestampStart());
            wrapper.ge(TechnicalSignal::getKlineTimestamp, query.getKlineTimestampStart());
        }
        if (query.getKlineTimestampEnd() != null) {
            // 直接使用毫秒级时间戳进行比较
            System.out.println("🔍 [TechnicalSignalServiceImpl] 时间范围查询 - 结束时间戳: " + query.getKlineTimestampEnd());
            wrapper.le(TechnicalSignal::getKlineTimestamp, query.getKlineTimestampEnd());
        }
        if (query.getMinSignalStrength() != null) {
            wrapper.ge(TechnicalSignal::getSignalStrength, query.getMinSignalStrength());
        }
        if (query.getMaxSignalStrength() != null) {
            wrapper.le(TechnicalSignal::getSignalStrength, query.getMaxSignalStrength());
        }

        // 调试：查询总数据量（不含时间限制）
        try {
            LambdaQueryWrapper<TechnicalSignal> countWrapper = new LambdaQueryWrapper<>();
            if (query.getSymbol() != null) {
                countWrapper.eq(TechnicalSignal::getSymbol, query.getSymbol());
            }
            if (query.getIndicator() != null) {
                countWrapper.eq(TechnicalSignal::getIndicator, query.getIndicator());
            }
            long totalCount = this.count(countWrapper);
            System.out.println("🔍 [TechnicalSignalServiceImpl] 数据库中符合基本条件的总记录数: " + totalCount);
        } catch (Exception e) {
            System.out.println("❌ [TechnicalSignalServiceImpl] 查询总记录数时出错: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("🔍 [TechnicalSignalServiceImpl] 查询条件构建完成");
        return wrapper;
    }

    @Override
    public TechnicalSignal getTechnicalSignalByTime(String indicator, String symbol, String technicalDirection,Long klineTimestamp) {
        LambdaQueryWrapper<TechnicalSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TechnicalSignal::getIndicator, indicator);
        wrapper.eq(TechnicalSignal::getSymbol, symbol);
        wrapper.eq(TechnicalSignal::getTechnicalDirection, technicalDirection);
        wrapper.eq(TechnicalSignal::getKlineTimestamp, klineTimestamp);
        return this.getOne(wrapper);
    }
}
