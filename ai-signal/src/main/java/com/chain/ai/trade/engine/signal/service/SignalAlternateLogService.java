package com.chain.ai.trade.engine.signal.service;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.feature.SignalFeatureProvider;
import com.chain.ai.trade.engine.signal.mapper.SignalAlternateLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * L1 信号交替流水服务：负责信号配对、pending 记录写入与配对完成。
 * <p>
 * 配对规则（重叠模型，与历史回填 LEAD 口径一致）：
 * 1. 查询最近一笔未配对的 pending 记录（exit_signal_id 为空）；
 * 2. 若其开仓方向与当前信号方向相反，则填充该 pending 记录的 exit_* 字段完成配对，并触发 L2 特征更新；
 * 3. 无论是否配对成功，当前信号都作为新的 pending 记录插入（既是上一对的 exit，又是下一对的 entry）。
 */
@Slf4j
@Service
public class SignalAlternateLogService {

    @Autowired
    private SignalAlternateLogMapper signalAlternateLogMapper;

    @Autowired
    private SignalFeatureProvider featureProvider;

    /**
     * 尝试配对并写入 L1 交替流水记录。
     * <p>
     * 由 DefaultSignService.saveSign() 在技术信号入库后调用。
     * 仅处理开仓信号（LONG/SHORT），平仓（CLOSE_LONG/CLOSE_SHORT）、回调、持有信号不参与交替配对。
     *
     * @param signalId 当前技术信号 ID（technical_signal.id）
     * @param calcDto  指标计算上下文（提供 symbol/timeframe/当前K线/方向）
     */
    @Transactional(rollbackFor = Exception.class)
    public void tryPairAndInsert(Long signalId, IndicatorCalcDto calcDto) {
        SignalType signalType = calcDto.getSignalType();
        if (signalType != SignalType.LONG && signalType != SignalType.SHORT) {
            return;
        }

        String strategyName = calcDto.getRobotName();
        String symbol = calcDto.getSymbol();
        String direction = signalType.name();

        Candlestick current = resolveCurrentCandlestick(calcDto);
        if (current == null || current.getId() == null || current.getClosePrice() == null) {
            log.warn("L1配对跳过：当前K线信息缺失, strategyName={}, symbol={}", strategyName, symbol);
            return;
        }

        String timeframe = resolveTimeframe(calcDto, current);
        if (timeframe == null) {
            log.warn("L1配对跳过：无法解析周期, strategyName={}, symbol={}", strategyName, symbol);
            return;
        }

        Long timestamp = current.getId();
        BigDecimal price = current.getClosePrice();

        // 1. 查询最近一笔未配对的 pending 记录
        SignalAlternateLog lastUnpaired = signalAlternateLogMapper.selectLastUnpaired(strategyName, symbol, timeframe);

        // 2. 存在且方向相反 → 配对成功：填充 pending 记录的 exit 字段
        if (lastUnpaired != null && !direction.equals(lastUnpaired.getEntryDirection())) {
            lastUnpaired.setExitTime(timestamp);
            lastUnpaired.setExitPrice(price);
            lastUnpaired.setExitDirection(direction);
            lastUnpaired.setExitSignalId(signalId);
            lastUnpaired.setSpacePct(calculateSpace(lastUnpaired.getEntryPrice(), price));
            lastUnpaired.setMinutesBetween(calcMinutesBetween(timestamp, lastUnpaired.getEntryTime()));
            signalAlternateLogMapper.updateById(lastUnpaired);

            // 3. 触发 L2 特征更新
            featureProvider.onNewSignal(lastUnpaired);
            log.info("L1配对成功: strategyName={}, symbol={}, timeframe={}, entryTime={}, exitTime={}, space={}%",
                    strategyName, symbol, timeframe, lastUnpaired.getEntryTime(), lastUnpaired.getExitTime(),
                    lastUnpaired.getSpacePct());
        }

        // 4. 当前信号始终作为新的待配对记录（重叠模型：既是上一对的 exit，又是下一对的 entry）
        SignalAlternateLog pending = new SignalAlternateLog();
        pending.setStrategyName(strategyName);
        pending.setSymbol(symbol);
        pending.setTimeframe(timeframe);
        pending.setEntryTime(timestamp);
        pending.setEntryPrice(price);
        pending.setEntryDirection(direction);
        pending.setEntrySignalId(signalId);
        signalAlternateLogMapper.insert(pending);
    }

    /**
     * 计算交替空间百分比：(exitPrice - entryPrice) / entryPrice * 100
     */
    private BigDecimal calculateSpace(BigDecimal entryPrice, BigDecimal exitPrice) {
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return exitPrice.subtract(entryPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(entryPrice, 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算间隔分钟数：(exitTime - entryTime) / 60000
     */
    private int calcMinutesBetween(long exitTime, long entryTime) {
        return (int) ((exitTime - entryTime) / 60000);
    }

    /**
     * 解析当前 K 线（优先取 saveSign 已设置的 currentCandlestick，否则取最后一根 K 线）
     */
    private Candlestick resolveCurrentCandlestick(IndicatorCalcDto calcDto) {
        if (calcDto.getCurrentCandlestick() != null) {
            return calcDto.getCurrentCandlestick();
        }
        List<Candlestick> kLines = calcDto.getKLines();
        if (kLines != null && !kLines.isEmpty()) {
            return kLines.get(kLines.size() - 1);
        }
        return null;
    }

    /**
     * 解析周期（CandlestickIntervalEnum.name()，如 OKXMIN5），优先取 calcDto 上的周期
     */
    private String resolveTimeframe(IndicatorCalcDto calcDto, Candlestick current) {
        CandlestickIntervalEnum interval = calcDto.getCandlestickIntervalEnum();
        if (interval == null && current != null) {
            interval = current.getCandlestickIntervalEnum();
        }
        return interval != null ? interval.name() : null;
    }
}
