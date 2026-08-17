package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.entity.dto.WeightAndConfidenceDto;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import com.chain.ai.trade.engine.signal.service.ISignService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 组合信号服务：将多个不同周期的信号子服务组合为一个信号源。
 * <p>
 * 每个子服务可绑定独立的K线周期，组合层分别用各自周期的数据计算子服务信号。
 * 仅当所有子信号方向一致时输出合并信号并落库，冲突（同时有多空）则丢弃。
 * 策略名（strategy_name）拼接达成一致的子服务简称（如 "RANGE_FILTER_DW+BOLLINGER_RSI"），
 * 权重直接取第一个达成一致子服务的权重（不进行加权平均）。
 */
@Slf4j
@Service
public class CombinedSignService extends DefaultSignService {

    // ==================== 硬编码配置 ====================

    /** 各子服务绑定的主周期 */
    private static final Map<Class<?>, CandlestickIntervalEnum> DELEGATE_INTERVALS = Map.of(
            PriceTrendChannelSignService.class, CandlestickIntervalEnum.OKXMIN5,
            SmoothSignService.class, CandlestickIntervalEnum.OKXMIN5,
            FibonacciBandsSignService.class, CandlestickIntervalEnum.OKXMIN15
    );

    private static final int DEFAULT_BAR_COUNT = 300;

    // ==================== 子服务列表 ====================

    private final PriceTrendChannelSignService rangeFilterSignService;
    private final BollingerRsiSignService bollingerRsiSignService;
    private final FibonacciBandsSignService fibonacciBandsSignService;
    private final SmoothSignService smoothSignService;

    /** 有序的子服务列表（初始化顺序固定） */
    private final List<ISignService> delegates = new ArrayList<>();

    @Autowired
    public CombinedSignService(
            PriceTrendChannelSignService rangeFilterSignService,
            BollingerRsiSignService bollingerRsiSignService,
            FibonacciBandsSignService fibonacciBandsSignService,
            SmoothSignService smoothSignService) {
        this.rangeFilterSignService = rangeFilterSignService;
        this.bollingerRsiSignService = bollingerRsiSignService;
        this.fibonacciBandsSignService = fibonacciBandsSignService;
        this.smoothSignService = smoothSignService;
    }

    @PostConstruct
    public void init() {
        /*delegates.add(rangeFilterSignService);
        delegates.add(bollingerRsiSignService);
        delegates.add(fibonacciBandsSignService);*/
        delegates.add(smoothSignService);
        log.info("CombinedSignService 已初始化，组合 {} 个信号服务: {}，周期配置: {}",
                delegates.size(),
                delegates.stream().map(s -> s.getClass().getSimpleName()).toList(),
                DELEGATE_INTERVALS);
    }

    // ==================== 核心执行方法 ====================

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto baseCalcDto) {
        if (baseCalcDto == null) {
            return new BuyAndSellWeightDto();
        }

        // 记录信号发送状态，外层统一控制
        boolean previouslyEnabled = isSignalSendingEnabled();
        DefaultSignService.disableSignalSending();

        try {
            long t0 = System.currentTimeMillis();
            // 顺序执行所有子任务（串行，同一线程）
            List<DelegateResult> results = new ArrayList<>();
            for (ISignService delegate : delegates) {
                long dt0 = System.currentTimeMillis();
                try {
                    IndicatorCalcDto subDto = buildSubCalcDto(delegate, baseCalcDto);
                    BuyAndSellWeightDto dto = delegate.execute(subDto);
                    if (dto != null && dto.getSignalType() != null) {
                        results.add(new DelegateResult(delegate, dto, subDto));
                    }
                } catch (Exception e) {
                    log.warn("组合信号: 子服务执行异常, delegate={}, error={}",
                            delegate.getClass().getSimpleName(), e.getMessage());
                }
                log.info("信号耗时 - delegate {}: {}ms",
                        delegate.getClass().getSimpleName(), System.currentTimeMillis() - dt0);
            }
            long t1 = System.currentTimeMillis();
            log.info("信号耗时 - 所有delegate总计: {}ms, 有效结果: {}", t1 - t0, results.size());

            // 合并并落库（外层控制发送信号）
            BuyAndSellWeightDto merged = mergeAndEmit(baseCalcDto, results);
            log.info("信号耗时 - mergeAndEmit: {}ms", System.currentTimeMillis() - t1);
            return merged;

        } finally {
            // 恢复原始信号发送状态
            if (previouslyEnabled) {
                DefaultSignService.enableSignalSending();
            }
        }
    }

    @Override
    public BuyAndSellWeightDto executeClose(IndicatorCalcDto calcDto) {
        DefaultSignService.disableSignalSending();
        try {
            for (ISignService delegate : delegates) {
                try {
                    IndicatorCalcDto subDto = buildSubCalcDto(delegate, calcDto);
                    BuyAndSellWeightDto closeResult = delegate.executeClose(subDto);
                    if (closeResult != null && closeResult.getSignalType() != null) {
                        return closeResult;
                    }
                } catch (Exception e) {
                    log.debug("组合信号: 子服务 executeClose 异常, delegate={}, error={}",
                            delegate.getClass().getSimpleName(), e.getMessage());
                }
            }
            return null;
        } finally {
            DefaultSignService.enableSignalSending();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 为指定子服务构建适配其周期的 calcDto。
     * 若周期与 baseCalcDto 相同则直接复用，否则拉取目标周期 K 线。
     */
    private IndicatorCalcDto buildSubCalcDto(ISignService delegate, IndicatorCalcDto baseCalcDto) {
        CandlestickIntervalEnum delegateInterval = DELEGATE_INTERVALS.get(delegate.getClass());
        if (delegateInterval == null || delegateInterval == baseCalcDto.getCandlestickIntervalEnum()) {
            return baseCalcDto;
        }

        long endTime = baseCalcDto.getKLines() != null && !baseCalcDto.getKLines().isEmpty()
                ? baseCalcDto.getKLines().getLast().getId()
                : System.currentTimeMillis();

        List<Candlestick> kLines = loadDelegateKlines(baseCalcDto.getSymbol(), delegateInterval, endTime);
        if (kLines == null || kLines.isEmpty()) {
            log.warn("子服务 {} 拉取K线为空，降级使用主周期数据", delegate.getClass().getSimpleName());
            return baseCalcDto;
        }

        // 克隆并替换周期相关字段
        IndicatorCalcDto subDto = new IndicatorCalcDto();
        copyBaseFields(baseCalcDto, subDto);
        subDto.setCandlestickIntervalEnum(delegateInterval);
        subDto.setKLines(kLines);
        subDto.setCurrentCandlestick(kLines.getLast());
        subDto.setKlineLength(kLines.size());
        return subDto;
    }

    /** 复制基础字段（不含 K 线相关） */
    private void copyBaseFields(IndicatorCalcDto src, IndicatorCalcDto dest) {
        dest.setRobotId(src.getRobotId());
        dest.setRobotName(src.getRobotName());
        dest.setSymbol(src.getSymbol());
        dest.setParameterOverrides(src.getParameterOverrides());
        dest.setMarketTrend(src.getMarketTrend());
        dest.setEntryType(src.getEntryType());
        dest.setLimitPrice(src.getLimitPrice());
        dest.setOpenSide(src.getOpenSide());
        dest.setStrategyType(src.getStrategyType());
        dest.setConfiguration(src.getConfiguration());

        if (src.getLongLengthOpen() > 0) dest.setLongLengthOpen(src.getLongLengthOpen());
        if (src.getShortLengthOpen() > 0) dest.setShortLengthOpen(src.getShortLengthOpen());
        if (src.getLengthOpen() > 0) dest.setLengthOpen(src.getLengthOpen());

        dest.setFilterEma(src.isFilterEma());
        if (src.getEmaLength() > 0) dest.setEmaLength(src.getEmaLength());
        dest.setFilterHama(src.isFilterHama());
        dest.setMultiplier(src.getMultiplier());
    }

    /**
     * 构建保存用的 calcDto：K 线数据使用子服务的周期数据，
     * 其余元数据（robotId、symbol、configuration 等）复用主 calcDto。
     */
    private IndicatorCalcDto buildSaveDto(IndicatorCalcDto base, IndicatorCalcDto subDto) {
        IndicatorCalcDto saveDto = new IndicatorCalcDto();
        copyBaseFields(base, saveDto);
        saveDto.setCandlestickIntervalEnum(subDto.getCandlestickIntervalEnum());
        saveDto.setKLines(subDto.getKLines());
        saveDto.setCurrentCandlestick(subDto.getCurrentCandlestick());
        saveDto.setKlineLength(subDto.getKlineLength());
        return saveDto;
    }

    /** 拉取指定周期的 K 线 */
    private List<Candlestick> loadDelegateKlines(String symbol, CandlestickIntervalEnum interval, long endTime) {
        try {
            var param = com.chain.ai.trade.engine.data.entity.param.KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(interval)
                    .endTime(endTime)
                    .size(DEFAULT_BAR_COUNT)
                    .build();
            return candlestickService.listByLtId(param);
        } catch (Exception e) {
            log.error("拉取K线失败: symbol={}, interval={}, error={}", symbol, interval, e.getMessage());
            return null;
        }
    }

    // ==================== 合并逻辑 ====================

    /** 子服务执行结果封装 */
    private record DelegateResult(ISignService delegate, BuyAndSellWeightDto dto, IndicatorCalcDto subDto) {
        String shortName() {
            return delegate.getClass().getSimpleName().replace("SignService", "");
        }
    }

    /**
     * 合并各子服务结果：
     * <ul>
     *   <li>同时存在多空信号 → 冲突，不生成信号</li>
     *   <li>方向一致 → 生成信号，strategy_name 拼接同向子服务名，权重取第一个一致子服务的权重</li>
     * </ul>
     */
    private BuyAndSellWeightDto mergeAndEmit(IndicatorCalcDto baseCalcDto, List<DelegateResult> results) {
        BuyAndSellWeightDto merged = new BuyAndSellWeightDto();
        if (results.isEmpty()) {
            setKlineTime(merged, baseCalcDto);
            return merged;
        }

        // 按方向分组
        List<DelegateResult> buyDelegates = new ArrayList<>();
        List<DelegateResult> sellDelegates = new ArrayList<>();
        for (DelegateResult r : results) {
            SignalType type = r.dto().getSignalType();
            if (type == SignalType.LONG || type == SignalType.CALLBACK_LONG) {
                buyDelegates.add(r);
            } else if (type == SignalType.SHORT || type == SignalType.CALLBACK_SHORT) {
                sellDelegates.add(r);
            }
        }

        boolean hasBuy = !buyDelegates.isEmpty();
        boolean hasSell = !sellDelegates.isEmpty();

        // 冲突检查：同时有多空 → 丢弃
        if (hasBuy && hasSell) {
            log.debug("组合信号: 同时存在多空信号，丢弃。buy={}, sell={}",
                    buyDelegates.size(), sellDelegates.size());
            setKlineTime(merged, baseCalcDto);
            return merged;
        }

        // 确定方向和参与的子服务
        List<DelegateResult> agreeingDelegates;
        SignalType finalSignalType;
        if (hasBuy) {
            agreeingDelegates = buyDelegates;
            finalSignalType = SignalType.LONG;
        } else if (hasSell) {
            agreeingDelegates = sellDelegates;
            finalSignalType = SignalType.SHORT;
        } else {
            return merged;
        }

        // 拼接策略名
        StringJoiner sj = new StringJoiner("+");
        for (DelegateResult r : agreeingDelegates) {
            sj.add(r.shortName());
        }
        String strategyName = sj.toString();

        // 使用第一个一致子服务的权重（业务逻辑保持不变）
        DelegateResult firstDelegate = agreeingDelegates.get(0);
        firstDelegate.subDto().setSignalType(finalSignalType);

        // ★ 构建保存用 calcDto：K线数据用第一个子服务的周期数据，其余元数据复用 baseCalcDto
        IndicatorCalcDto saveDto = buildSaveDto(baseCalcDto, firstDelegate.subDto());

        // 落库（临时启用信号发送，外层 execute 已统一禁用）
        DefaultSignService.enableSignalSending();
        long saveT0 = System.currentTimeMillis();
        Long signalId = saveSign(saveDto, finalSignalType, null, null, strategyName);
        log.info("信号耗时 - mergeAndEmit/saveSign: {}ms", System.currentTimeMillis() - saveT0);
        DefaultSignService.disableSignalSending();
        merged.setSignalId(signalId);
        // 构建返回
        merged.setSignalType(finalSignalType);
        merged.setDataInterval(saveDto.getCandlestickIntervalEnum());
        setKlineTime(merged, saveDto);

        log.debug("组合信号: 输出 {}, strategyName={}, 参与子服务={}",
                finalSignalType, strategyName, agreeingDelegates.size());
        return merged;
    }

    /** 从 calcDto 提取信号时间戳 */
    private long extractSignalTime(IndicatorCalcDto calcDto) {
        if (calcDto.getCurrentCandlestick() != null && calcDto.getCurrentCandlestick().getId() != null) {
            return calcDto.getCurrentCandlestick().getId();
        }
        if (calcDto.getKLines() != null && !calcDto.getKLines().isEmpty()
                && calcDto.getKLines().getLast().getId() != null) {
            return calcDto.getKLines().getLast().getId();
        }
        return 0L;
    }

    /** 设置 K 线时间字符串（用于返回） */
    private void setKlineTime(BuyAndSellWeightDto dto, IndicatorCalcDto calcDto) {
        if (calcDto.getKLines() != null && !calcDto.getKLines().isEmpty()) {
            dto.setKlineTime(calcDto.getKLines().getLast().getTimeStr());
        }
    }

    // ==================== 权重方法（暂时未使用，保留但可优化） ====================

    @Override
    public WeightAndConfidenceDto getWeightAndConfidence(IndicatorCalcDto calcDto) {
        return smoothSignService.getWeightAndConfidence(calcDto);
    }
}