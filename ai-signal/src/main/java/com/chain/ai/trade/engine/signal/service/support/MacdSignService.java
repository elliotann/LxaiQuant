package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.signal.entity.constants.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.enums.IndicatorSignal;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;

import com.chain.ai.trade.extension.ta4j.indicator.DifferenceIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import org.ta4j.core.indicators.MACDIndicator;

import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN15;
import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN60;
import static com.chain.ai.trade.common.entity.constants.Exchange.OKX;
import static com.chain.ai.trade.engine.signal.enums.IndicatorSignal.DEATH_CROSS;
import static com.chain.ai.trade.engine.signal.enums.IndicatorSignal.GOLDEN_CROSS;


@Service
public class MacdSignService extends DefaultSignService {
    private static final double EPSILON = 1e-6; // 根据实际数据精度调整
    private static final Logger logger = LoggerFactory.getLogger(MacdSignService.class);
    private static final int DEFAULT_KLINES_COUNT = 200; // 默认获取的K线数量
    // ================= 动态参数配置 =================
    @Value("${macd.fast:12}")
    private int fastLength;
    @Value("${macd.slow:26}")
    private int slowLength;
    @Value("${macd.signal:9}")
    private int signalLength;
    @Value("${macd.sma:20}")
    private int smaLength;

    // ================= 动态权重配置 =================
    @Value("${position.base_weight:1.0}")
    private double baseWeight = 1.0;
    @Value("${position.min_weight:0.1}")
    private double minWeight = 0.1;
    @Value("${position.max_weight:3.0}")
    private double maxWeight = 3.0;
    @Value("${position.consolidation_lookback:20}")
    private int consolidationLookback = 20;
    @Value("${position.profit_target:0.01}")
    private double profitTarget = 0.01;
    @Value("${position.loss_tolerance:0.005}")
    private double lossTolerance = 0.005;
    @Value("${position.weight.enabled:true}")
    private boolean weightEnabled = true;

    // ================= 核心指标 =================
    private BarSeries series;
    private MACDIndicator macd;
    private EMAIndicator signalLine;
    private DifferenceIndicator histogram;
    private EMAIndicator fastEMA;
    private EMAIndicator slowEMA;
    private SMAIndicator sma;
    private EMAIndicator ema144;

    // ================= 横盘状态缓存 =================
    private final Map<String, ConsolidationState> consolidationCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MacdSignService() {
    }

    /**
     * 初始化技术指标（基于当前series）
     */
    private void initializeIndicators() {
        if (this.series == null) {
            logger.warn("无法初始化指标：series为空");
            return;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 指标初始化
        this.fastEMA = new EMAIndicator(closePrice, fastLength);
        this.slowEMA = new EMAIndicator(closePrice, slowLength);
        this.macd = new MACDIndicator(closePrice, fastLength, slowLength);
        this.signalLine = new EMAIndicator(macd, signalLength);
        this.histogram = new DifferenceIndicator(macd, signalLine);
        this.sma = new SMAIndicator(closePrice, smaLength);
        this.ema144 = new EMAIndicator(closePrice, 144);

        logger.debug("MACD指标初始化完成: fastEMA={}, slowEMA={}, signalLength={}, smaLength={}, ema144=144",
                fastLength, slowLength, signalLength, smaLength);
    }


    // ================= 核心功能：时间框架重采样 =================

    // ================= 趋势分析系统 =================
    public String analyzeHistogramTrend(int index) {
        if (!validateIndex(index)) return "数据不足";

        double current = histogram.getValue(index).doubleValue();
        double previous = histogram.getValue(index - 1).doubleValue();

        if (current >= 0) {
            return current > previous ? "多头动能↑↑" :
                    current < previous ? "多头动能↓" : "多头动能→";
        } else {
            return current > previous ? "空头动能↓" :
                    current < previous ? "空头动能↑↑" : "空头动能→";
        }
    }

    /**
     * 分析MACD交叉信号
     * 金叉：MACD线从信号线下方上穿到上方
     * 死叉：MACD线从信号线上方下穿到下方
     */
    public IndicatorSignal analyzeMACDCross(int index) {
        if (!validateIndex(index) || index < 1) {
            return IndicatorSignal.INSUFFICIENT_DATA;
        }

        double macdCurrent = macd.getValue(index).doubleValue();
        double signalCurrent = signalLine.getValue(index).doubleValue();
        double macdPrevious = macd.getValue(index - 1).doubleValue();
        double signalPrevious = signalLine.getValue(index - 1).doubleValue();

        // 计算MACD相对信号线的位置关系
        boolean currentAbove = (macdCurrent - signalCurrent) > EPSILON;
        boolean previousAbove = (macdPrevious - signalPrevious) > EPSILON;

        if (currentAbove && !previousAbove) {
            logger.debug("检测到MACD金叉: 当前MACD({}) > 信号线({}), 前一个MACD({}) <= 信号线({})",
                    macdCurrent, signalCurrent, macdPrevious, signalPrevious);
            return GOLDEN_CROSS;
        } else if (!currentAbove && previousAbove) {
            logger.debug("检测到MACD死叉: 当前MACD({}) <= 信号线({}), 前一个MACD({}) > 信号线({})",
                    macdCurrent, signalCurrent, macdPrevious, signalPrevious);
            return DEATH_CROSS;
        } else {
            return currentAbove ? IndicatorSignal.BULLISH_ALIGNMENT : IndicatorSignal.BEARISH_ALIGNMENT;
        }
    }


    // ================= 多时间框架分析 =================


    // ================= 工具方法 =================
    private boolean validateIndex(int index) {
        return series != null &&
                index >= 0 &&
                index < series.getBarCount() &&
                index < macd.getBarSeries().getBarCount();
    }

    // ================= 数据可视化 =================

    // ================= 接口实现 =================
    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        try {
            // 参考BollingerRsiSignService的方式，不创建新实例，直接使用当前实例
            BarSeries series = IndicatorWrapHelper.buildSeries(calcDto.getKLines());

            // 临时设置series用于分析
            BarSeries originalSeries = this.series;
            this.series = series;

            // 初始化指标（基于新的series）
            initializeIndicators();

            BuyAndSellWeightDto result = new BuyAndSellWeightDto();
            int lastIndex = series.getEndIndex() - 1; // 使用倒数第二根K线，确保已完成

            // 检查是否有足够的数据
            if (lastIndex < 60) { // 需要至少EMA60的数据
                logger.warn("MACD策略数据不足，至少需要60根K线，当前: {}", lastIndex + 1);
                return result;
            }

            // 生成交易信号
            TradeSignal rawSignal = this.generateMACDSignal(lastIndex, calcDto);

            // 应用K线方向过滤
            /*TradeSignal directionFilteredSignal = this.applyCandleDirectionFilter(rawSignal, lastIndex);

            // 应用15分钟MACD趋势过滤
            TradeSignal macdFilteredSignal = this.apply15MinMacdTrendFilter(directionFilteredSignal, calcDto);

            // 应用15分钟BOLL中轨过滤
            TradeSignal boll15FilteredSignal = this.apply15MinBollMiddleFilter(macdFilteredSignal, calcDto);

            // 应用1小时BOLL中轨过滤 (暂时注释)
//            TradeSignal boll1HFilteredSignal = this.apply1HourBollMiddleFilter(boll15FilteredSignal, calcDto);

            // 应用连续信号过滤
//            TradeSignal filteredSignal = this.applyConsecutiveSignalFilter(boll1HFilteredSignal, calcDto);
            TradeSignal filteredSignal = this.applyConsecutiveSignalFilter(boll15FilteredSignal, calcDto);*/

            // 详细记录过滤过程
            if (rawSignal != TradeSignal.NEUTRAL) {

            }

            if (rawSignal == TradeSignal.STRONG_LONG || rawSignal == TradeSignal.CAUTIOUS_LONG) {
                // 获取当前价格（开盘价）
                Candlestick currentCandle = calcDto.getKLines().get(calcDto.getKLines().size() - 1);
                double currentPrice = currentCandle.getOpenPrice().doubleValue();

                // EMA144过滤：价格必须在EMA144上方才允许多头信号
                if (lastIndex >= 144) { // 确保有足够的数据计算EMA144
                    double ema144Value = ema144.getValue(lastIndex).doubleValue();
                    boolean ema144ConditionMet = currentPrice > ema144Value;

                    if (!ema144ConditionMet) {
                        logger.info("MACD策略多头信号被EMA144过滤: 价格{:.4f} <= EMA144{:.4f}", currentPrice, ema144Value);
                        // EMA144条件不满足，跳过这个信号
                        return result;
                    }
                }

                // 检查连续多头信号的交替条件 (BUY信号必须在SELL信号之后)
                boolean alternationConditionMet = checkSignalAlternationCondition(calcDto, currentPrice, "LONG", null);

                if (alternationConditionMet) {
                    result.setSignalType(SignalType.LONG);
                    logger.info("MACD策略生成买入信号: {} (已通过EMA144和信号交替过滤)", rawSignal);
                } else {
                    logger.info("MACD策略买入信号被信号交替过滤: {} (必须在SELL信号之后)", rawSignal);
                }
            } else if (rawSignal == TradeSignal.STRONG_SHORT || rawSignal == TradeSignal.CAUTIOUS_SHORT) {
                // 获取当前价格（开盘价）
                Candlestick currentCandle = calcDto.getKLines().get(calcDto.getKLines().size() - 1);
                double currentPrice = currentCandle.getOpenPrice().doubleValue();

                // EMA144过滤：价格必须在EMA144下方才允许空头信号
                if (lastIndex >= 144) { // 确保有足够的数据计算EMA144
                    double ema144Value = ema144.getValue(lastIndex).doubleValue();
                    boolean ema144ConditionMet = currentPrice < ema144Value;

                    if (!ema144ConditionMet) {
                        logger.info("MACD策略空头信号被EMA144过滤: 价格{:.4f} >= EMA144{:.4f}", currentPrice, ema144Value);
                        // EMA144条件不满足，跳过这个信号
                        return result;
                    }
                }

                // 检查连续空头信号的交替条件 (SELL信号必须在BUY信号之后)
                boolean alternationConditionMet = checkSignalAlternationCondition(calcDto, currentPrice, "SHORT", null);

                if (alternationConditionMet) {
                    result.setSignalType(SignalType.SHORT);
                    logger.info("MACD策略生成卖出信号: {} (已通过EMA144和信号交替过滤)", rawSignal);
                } else {
                    logger.info("MACD策略卖出信号被信号交替过滤: {} (必须在BUY信号之后)", rawSignal);
                }
            } else if (rawSignal != TradeSignal.NEUTRAL && rawSignal == TradeSignal.NEUTRAL) {
                logger.debug("MACD策略信号被过滤掉: 原始{}, 方向过滤后{}, 最终{}", rawSignal, rawSignal, rawSignal);
            } else {
                logger.debug("MACD策略无信号: {}", rawSignal);
            }

            // 发送信号
            if (null != result.getSignalType()) {
                Long signalId = saveSign(calcDto, result.getSignalType());
                result.setSignalId(signalId);
            }

            // 设置K线时间
            if (calcDto.getKLines().size() > 0) {
                result.setKlineTime(String.valueOf(calcDto.getKLines().get(calcDto.getKLines().size() - 1).getId()));
            }

            // 恢复原始series，避免影响其他调用
            this.series = originalSeries;

            return result;
        } catch (Exception e) {
            logger.error("MACD信号生成失败: {}", e.getMessage(), e);
            return new BuyAndSellWeightDto(); // 返回中性结果
        }
    }

    /**
     * 生成MACD交易信号 - 原始信号生成（不包含K线方向过滤）
     * 1. MACD金叉开多（需快慢线都在0轴上方）
     * 2. MACD死叉开空（需快慢线都在0轴下方）
     * 3. 或EMA条件（满足任一即可）：
     * - EMA21上穿/下穿EMA60 + 3EMA排列（原条件）
     * - EMA9同时上穿/下穿EMA21和EMA60 + 3EMA排列（新条件）
     */
    public TradeSignal generateMACDSignal(int index, IndicatorCalcDto calcDto) {
        if (!validateIndex(index) || index < 1) {
            return TradeSignal.NEUTRAL;
        }

        TradeSignal signal = TradeSignal.NEUTRAL;
        String signalSource = "";

        // 条件1：MACD金叉/死叉信号（带位置过滤）
        IndicatorSignal macdCross = analyzeMACDCross(index);
        if (macdCross == GOLDEN_CROSS) {
            // 金叉开多：快慢线需要在0轴上方
            double macdValue = macd.getValue(index).doubleValue();
            double signalValue = signalLine.getValue(index).doubleValue();
            if (macdValue > 0 && signalValue > 0) {
                logger.debug("MACD金叉信号且快慢线在0轴上方 - 开多");
                signal = TradeSignal.STRONG_LONG;
                signalSource = "MACD金叉";
            } else {
                logger.debug("MACD金叉信号但快慢线不在0轴上方 - 过滤掉, MACD:{}, Signal:{}",
                        macdValue, signalValue);
            }
        } else if (macdCross == DEATH_CROSS) {
            // 死叉开空：快慢线需要在0轴下方
            double macdValue = macd.getValue(index).doubleValue();
            double signalValue = signalLine.getValue(index).doubleValue();
            if (macdValue < 0 && signalValue < 0) {
                logger.debug("MACD死叉信号且快慢线在0轴下方 - 开空");
                signal = TradeSignal.STRONG_SHORT;
                signalSource = "MACD死叉";
            } else {
                logger.debug("MACD死叉信号但快慢线不在0轴下方 - 过滤掉, MACD:{}, Signal:{}",
                        macdValue, signalValue);
            }
        }

        // 如果MACD没有产生信号，检查EMA信号
        if (signal == TradeSignal.NEUTRAL) {
            TradeSignal emaSignal = analyzeEMATrendSignal(index);
            if (emaSignal == TradeSignal.CAUTIOUS_LONG) {
                signal = TradeSignal.CAUTIOUS_LONG;
                signalSource = "EMA多头";
                logger.debug("EMA多头信号 - 开多");
            } else if (emaSignal == TradeSignal.CAUTIOUS_SHORT) {
                signal = TradeSignal.CAUTIOUS_SHORT;
                signalSource = "EMA空头";
                logger.debug("EMA空头信号 - 开空");
            }
        }

        // 如果MACD产生了信号，应用15分钟EMA9/21过滤
        if (signal != TradeSignal.NEUTRAL) {
            // 应用15分钟EMA9与EMA21过滤
            TradeSignal emaFilteredSignal = apply15MinEmaFilter(signal, index, calcDto);
            if (emaFilteredSignal == TradeSignal.NEUTRAL) {
                logger.debug("MACD信号 {} 被15分钟EMA9/21过滤 - EMA条件不满足", signal);
                return TradeSignal.NEUTRAL;
            }

            // 应用K线方向过滤
            TradeSignal candleFilteredSignal = applyCandleDirectionFilter(signal, index);
            if (candleFilteredSignal == TradeSignal.NEUTRAL) {
                logger.debug("MACD信号 {} 被K线方向过滤 - K线方向与信号不一致", signal);
                return TradeSignal.NEUTRAL;
            }

            // 验证EMA趋势是否一致
            TradeSignal emaTrendSignal = getEMATrendDirection(index);
            if ((signal == TradeSignal.STRONG_LONG && emaTrendSignal == TradeSignal.CAUTIOUS_SHORT) ||
                    (signal == TradeSignal.STRONG_SHORT && emaTrendSignal == TradeSignal.CAUTIOUS_LONG)) {
                logger.debug("MACD信号 {} 被EMA趋势验证过滤 - EMA趋势相反 (EMA: {}, MACD: {})",
                        signal, emaTrendSignal, signalSource);
                return TradeSignal.NEUTRAL;
            }
            logger.debug("MACD策略生成原始信号: {} ({}) - EMA过滤和趋势验证通过", signal, signalSource);
        }

        return signal;
    }

    /**
     * 应用15分钟EMA9与EMA21过滤
     * 多头信号只有在EMA9 > EMA21时才有效
     * 空头信号只有在EMA9 < EMA21时才有效
     */
    private TradeSignal apply15MinEmaFilter(TradeSignal signal, int index, IndicatorCalcDto calcDto) {
        try {
            // 获取15分钟K线数据
            List<Candlestick> min15Klines = get1HourKlines(calcDto.getKLines(), calcDto.getSymbol());
            if (min15Klines == null || min15Klines.size() < 25) { // 需要足够的数据
                logger.debug("15分钟EMA过滤：无法获取15分钟K线数据或数据不足，至少需要25根K线");
                return signal;
            }

            // 构建15分钟K线序列
            BarSeries min15Series = IndicatorWrapHelper.buildSeries(min15Klines);
            if (min15Series.getBarCount() < 25) {
                logger.debug("15分钟EMA过滤：构建的15分钟序列数据不足，当前: {}根K线", min15Series.getBarCount());
                return signal;
            }

            // 使用最新的15分钟K线进行EMA计算
            int lastIndex = min15Series.getEndIndex() - 1; // 使用倒数第二根，确保已完成

            // 检查是否有足够的数据计算EMA
            if (lastIndex < 21) { // EMA21需要至少21根K线
                logger.debug("15分钟EMA过滤：15分钟数据不足，至少需要21根K线，当前索引: {}", lastIndex);
                return signal;
            }

            // 计算EMA9和EMA21
            ClosePriceIndicator closePrice = new ClosePriceIndicator(min15Series);
            EMAIndicator ema9 = new EMAIndicator(closePrice, 9);
            EMAIndicator ema21 = new EMAIndicator(closePrice, 21);

            double ema9Value = ema9.getValue(lastIndex).doubleValue();
            double ema21Value = ema21.getValue(lastIndex).doubleValue();

            logger.debug("15分钟EMA过滤 - EMA9: {}, EMA21: {}, 信号: {}", ema9Value, ema21Value, signal);

            // 应用过滤条件
            if (signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) {
                // 多头信号：只有EMA9 > EMA21时才有效
                if (ema9Value > ema21Value) {
                    logger.debug("15分钟EMA过滤 - 多头信号通过: EMA9({}) > EMA21({})", ema9Value, ema21Value);
                    return signal;
                } else {
                    logger.debug("15分钟EMA过滤 - 多头信号被过滤: EMA9({}) <= EMA21({})", ema9Value, ema21Value);
                    return TradeSignal.NEUTRAL;
                }
            } else if (signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) {
                // 空头信号：只有EMA9 < EMA21时才有效
                if (ema9Value < ema21Value) {
                    logger.debug("15分钟EMA过滤 - 空头信号通过: EMA9({}) < EMA21({})", ema9Value, ema21Value);
                    return signal;
                } else {
                    logger.debug("15分钟EMA过滤 - 空头信号被过滤: EMA9({}) >= EMA21({})", ema9Value, ema21Value);
                    return TradeSignal.NEUTRAL;
                }
            }

            // 其他信号类型直接返回
            return signal;

        } catch (Exception e) {
            logger.error("15分钟EMA过滤出现异常: {}", e.getMessage(), e);
            // 出现异常时返回原信号，不进行过滤
            return signal;
        }
    }


    /**
     * 获取EMA趋势方向 - 用于MACD信号验证
     * 简化的EMA趋势判断，只返回多头或空头方向
     */
    private TradeSignal getEMATrendDirection(int index) {
        try {
            // 创建EMA指标
            ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
            EMAIndicator ema9 = new EMAIndicator(closePriceIndicator, 9);
            EMAIndicator ema21 = new EMAIndicator(closePriceIndicator, 21);
            EMAIndicator ema60 = new EMAIndicator(closePriceIndicator, 60);

            // 检查是否有足够的数据
            if (index < 60) {
                return TradeSignal.NEUTRAL;
            }

            // 获取当前EMA值
            double ema9Current = ema9.getValue(index).doubleValue();
            double ema21Current = ema21.getValue(index).doubleValue();
            double ema60Current = ema60.getValue(index).doubleValue();

            // 判断3EMA排列方向
            if (ema9Current > ema21Current && ema21Current > ema60Current) {
                return TradeSignal.CAUTIOUS_LONG;  // 多头趋势
            } else if (ema9Current < ema21Current && ema21Current < ema60Current) {
                return TradeSignal.CAUTIOUS_SHORT; // 空头趋势
            }

        } catch (Exception e) {
            logger.warn("EMA趋势方向获取失败: {}", e.getMessage());
        }

        return TradeSignal.NEUTRAL;
    }

    /**
     * 应用15分钟BOLL中轨过滤 - 总过滤条件
     * 规则：多头信号只有在中轨上方才有效，空头信号只有在中轨下方才有效
     */
    public TradeSignal apply15MinBollMiddleFilter(TradeSignal signal, IndicatorCalcDto calcDto) {
        if (signal == TradeSignal.NEUTRAL) {
            return signal; // 无信号直接返回
        }

        try {
            // 获取15分钟K线数据
            List<Candlestick> kLines15 = get15MinKlines(calcDto.getKLines(), calcDto.getSymbol());

            if (kLines15 == null || kLines15.isEmpty()) {
                logger.debug("15分钟数据不存在，跳过BOLL中轨过滤");
                return signal;
            }

            // 构建15分钟序列
            BarSeries series15 = IndicatorWrapHelper.buildSeries(kLines15);

            if (series15.getBarCount() < 25) { // 需要足够的数据计算BOLL
                logger.debug("15分钟数据不足，至少需要25根K线，跳过BOLL中轨过滤");
                return signal;
            }

            // 计算BOLL指标 (20周期，2倍标准差)
            ClosePriceIndicator closePrice15 = new ClosePriceIndicator(series15);
            SMAIndicator sma15 = new SMAIndicator(closePrice15, 20);
            StandardDeviationIndicator stdDev15 = new StandardDeviationIndicator(closePrice15, 20);

            int lastIndex15 = series15.getEndIndex() - 1;

            // 计算中轨（SMA20）
            double middleBand = sma15.getValue(lastIndex15).doubleValue();

            // 获取当前价格（收盘价）
            double currentPrice = series15.getBar(lastIndex15).getClosePrice().doubleValue();

            // 获取当前K线价格（当前周期的K线价格）
            double currentKlinePrice = calcDto.getKLines().get(calcDto.getKLines().size() - 1).getClosePrice().doubleValue();

            logger.debug("15分钟BOLL中轨过滤: 中轨={}, 当前价格={}, 当前K线价格={}",
                    middleBand, currentPrice, currentKlinePrice);

            // 多头信号过滤：只有在中轨上方才有效
            if ((signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) &&
                    currentKlinePrice <= middleBand) {
                logger.debug("多头信号被15分钟BOLL中轨过滤: {} -> 当前价格{}在中轨{}下方，过滤掉",
                        signal, currentKlinePrice, middleBand);
                return TradeSignal.NEUTRAL;
            }

            // 空头信号过滤：只有在中轨下方才有效
            if ((signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) &&
                    currentKlinePrice >= middleBand) {
                logger.debug("空头信号被15分钟BOLL中轨过滤: {} -> 当前价格{}在中轨{}上方，过滤掉",
                        signal, currentKlinePrice, middleBand);
                return TradeSignal.NEUTRAL;
            }

            // 信号通过BOLL中轨过滤
            logger.debug("信号通过15分钟BOLL中轨过滤: {}", signal);

        } catch (Exception e) {
            logger.warn("15分钟BOLL中轨过滤失败: {}", e.getMessage());
            // 过滤失败时保持原信号
        }

        return signal;
    }

    /**
     * 应用1小时BOLL中轨过滤 - 高级过滤条件
     * 规则：多头信号只有在中轨上方才有效，空头信号只有在中轨下方才有效
     */
    /*
    public TradeSignal apply1HourBollMiddleFilter(TradeSignal signal, IndicatorCalcDto calcDto) {
        if (signal == TradeSignal.NEUTRAL) {
            return signal; // 无信号直接返回
        }

        try {
            // 获取1小时K线数据
            List<Candlestick> kLines1H = get1HourKlines(calcDto.getKLines(), calcDto.getSymbol());

            if (kLines1H == null || kLines1H.isEmpty()) {
                logger.debug("1小时数据不存在，跳过BOLL中轨过滤");
                return signal;
            }

            // 构建1小时序列
            BarSeries series1H = IndicatorWrapHelper.buildSeries(kLines1H,
                kLines1H.get(kLines1H.size()-1).getCandlestickIntervalEnum());

            if (series1H.getBarCount() < 25) { // 需要足够的数据计算BOLL
                logger.debug("1小时数据不足，至少需要25根K线，跳过BOLL中轨过滤");
                return signal;
            }

            // 计算BOLL指标 (20周期，2倍标准差)
            ClosePriceIndicator closePrice1H = new ClosePriceIndicator(series1H);
            SMAIndicator sma1H = new SMAIndicator(closePrice1H, 20);
            StandardDeviationIndicator stdDev1H = new StandardDeviationIndicator(closePrice1H, 20);

            int lastIndex1H = series1H.getEndIndex() - 1;

            // 计算中轨（SMA20）
            double middleBand = sma1H.getValue(lastIndex1H).doubleValue();

            // 获取当前价格（收盘价）
            double currentPrice = series1H.getBar(lastIndex1H).getClosePrice().doubleValue();

            // 获取当前K线价格（当前周期的K线价格）
            double currentKlinePrice = calcDto.getKLines().get(calcDto.getKLines().size() - 1).getClosePrice().doubleValue();

            logger.debug("1小时BOLL中轨过滤: 中轨={}, 当前价格={}, 当前K线价格={}",
                middleBand, currentPrice, currentKlinePrice);

            // 多头信号过滤：只有在中轨上方才有效
            if ((signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) &&
                currentKlinePrice <= middleBand) {
                logger.debug("多头信号被1小时BOLL中轨过滤: {} -> 当前价格{}在中轨{}下方，过滤掉",
                    signal, currentKlinePrice, middleBand);
                return TradeSignal.NEUTRAL;
            }

            // 空头信号过滤：只有在中轨下方才有效
            if ((signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) &&
                currentKlinePrice >= middleBand) {
                logger.debug("空头信号被1小时BOLL中轨过滤: {} -> 当前价格{}在中轨{}上方，过滤掉",
                    signal, currentKlinePrice, middleBand);
                return TradeSignal.NEUTRAL;
            }

            // 信号通过1小时BOLL中轨过滤
            logger.debug("信号通过1小时BOLL中轨过滤: {}", signal);

        } catch (Exception e) {
            logger.warn("1小时BOLL中轨过滤失败: {}", e.getMessage());
            // 过滤失败时保持原信号
        }

        return signal;
    }
    */

    /**
     * 应用连续信号过滤 - 防止连续相同信号过多
     * 规则：连续多头信号不超过2个，连续空头信号不超过2个
     */

    /**
     * 获取连续相同信号的数量
     *
     * @param calcDto       计算上下文
     * @param currentSignal 当前信号
     * @return 连续相同信号的数量
     */
    private int getConsecutiveSignalCount(IndicatorCalcDto calcDto, TradeSignal currentSignal) {
        try {
            // 检查服务是否已注入
            if (tradeSignalSignalService == null) {
                logger.warn("tradeSignalSignalService未注入，跳过连续信号过滤");
                return 0;
            }

            String robotId = calcDto.getRobotId();
            if (robotId == null) {
                logger.debug("robotId为空，无法查询连续信号计数");
                return 0;
            }

            // 获取当前K线时间作为查询基准
            String currentKlineTime = null;
            if (calcDto.getKLines() != null && !calcDto.getKLines().isEmpty()) {
                currentKlineTime = String.valueOf(calcDto.getKLines().get(calcDto.getKLines().size() - 1).getId());
            }

            if (currentKlineTime == null) {
                logger.debug("当前K线时间为空，无法查询连续信号计数");
                return 0;
            }

            // 查询最近的开仓信号记录
            List<TradeSignalSignal> recentSignals = new ArrayList<>();

            try {
                // 使用更简单的方法：查询最近N条指定机器人的信号记录
                // 由于queryBeforeSignal可能有时间格式问题，我们直接查询最近的信号

                List<TradeSignalSignal> allRecentSignals = new ArrayList<>();

                try {
                    // 使用新的批量查询方法，直接查询最近10条信号
                    allRecentSignals = tradeSignalSignalService.queryRecentSignals(
                            OKX.toString(),
                            calcDto.getCandlestickIntervalEnum().getCode(),
                            calcDto.getSymbol(),
                            robotId,
                            10  // 查询最近10条信号
                    );

                    logger.debug("批量查询完成，找到 {} 条历史信号: 机器人={}", allRecentSignals.size(), robotId);

                    // 只记录前3条信号详情，避免日志过多
                    for (int i = 0; i < Math.min(3, allRecentSignals.size()); i++) {
                        TradeSignalSignal signal = allRecentSignals.get(i);
                        logger.debug("历史信号[{}]: ID={}, 创建时间={}, 信号={}",
                                i + 1, signal.getId(),
                                signal.getCreateTime() != null ? signal.getCreateTime().toString() : "null",
                                signal.getTrend());
                    }

                } catch (Exception e) {
                    logger.error("查询历史信号失败: {}", e.getMessage(), e);
                }

                // queryRecentSignals已经按创建时间倒序返回，无需额外排序

                // 取最新的5条信号用于连续性检查
                recentSignals.addAll(allRecentSignals.subList(0, Math.min(5, allRecentSignals.size())));

                logger.debug("查询到历史信号总数: {}, 取其中最新的 {} 条用于连续性检查",
                        allRecentSignals.size(), recentSignals.size());

            } catch (Exception e) {
                logger.warn("查询历史信号失败: {}", e.getMessage());
            }

            if (recentSignals.isEmpty()) {
                logger.debug("未找到最近信号记录，连续计数为0");
                return 0;
            }

            // 确定当前信号类型
            String currentSignalType = getSignalTypeFromTradeSignal(currentSignal);
            if (currentSignalType == null) {
                logger.debug("无法确定当前信号类型: {}", currentSignal);
                return 0;
            }

            // 统计连续相同信号的数量（从最新的信号开始往前检查）
            // 注意：recentSignals已经按时间降序排序（最新的在前面）
            int consecutiveCount = 0;

            logger.debug("开始统计连续信号 - 机器人: {}, 当前信号类型: {}, 历史信号列表大小: {}",
                    robotId, currentSignalType, recentSignals.size());

            for (int i = 0; i < recentSignals.size(); i++) {
                TradeSignalSignal dbSignal = recentSignals.get(i);
                String signalValue = extractSignalFromTradeSignalSignal(dbSignal);

                logger.debug("检查历史信号[{}]: ID={}, 创建时间={}, 信号值={}",
                        i, dbSignal.getId(), dbSignal.getCreateTime(), signalValue);

                if (signalValue != null) {
                    if (signalValue.equals(currentSignalType)) {
                        consecutiveCount++;
                        logger.debug("找到相同信号，连续计数: {} (信号类型: {})", consecutiveCount, signalValue);
                    } else {
                        // 遇到不同信号，停止计数并记录
                        logger.debug("遇到不同信号: {} (期望: {}), 停止连续计数", signalValue, currentSignalType);
                        break;
                    }
                } else {
                    logger.debug("信号值解析为空，跳过此信号");
                }
            }

            logger.debug("连续信号统计完成 - 最终连续计数: {}", consecutiveCount);

            return consecutiveCount;

        } catch (Exception e) {
            logger.warn("获取连续信号计数失败: {}", e.getMessage());
            return 0; // 获取失败时假设没有连续信号
        }
    }

    /**
     * 从TradeSignal转换为信号类型字符串
     */
    private String getSignalTypeFromTradeSignal(TradeSignal signal) {
        if (signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) {
            return "LONG"; // 多头开仓信号
        } else if (signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) {
            return "SHORT"; // 空头开仓信号
        }
        return null;
    }

    /**
     * 从TradeSignalSignal实体中提取信号值
     */
    private String extractSignalFromTradeSignalSignal(TradeSignalSignal signal) {
        try {
            // 从trend字段中解析信号值，格式如：{"signal":"SHORT","weight":"1.0","source":"SYSTEM"}（兼容旧值"LB"/"SB"/"LS"/"SS"）
            String trend = signal.getTrend();
            if (trend != null && !trend.trim().isEmpty()) {
                try {
                    // 简单的JSON解析，提取signal字段的值
                    String signalPattern = "\"signal\":\"";
                    int signalStart = trend.indexOf(signalPattern);
                    if (signalStart != -1) {
                        signalStart += signalPattern.length(); // 跳过"signal":"
                        int signalEnd = trend.indexOf("\"", signalStart);
                        if (signalEnd != -1 && signalEnd > signalStart) {
                            String signalValue = trend.substring(signalStart, signalEnd);
                            // 验证信号值是否有效（兼容新旧值）
                            if (signalValue.equals("LONG") || signalValue.equals("SHORT") ||
                                    signalValue.equals("CLOSE_LONG") || signalValue.equals("CLOSE_SHORT") ||
                                    signalValue.equals("LB") || signalValue.equals("SB") ||
                                    signalValue.equals("LS") || signalValue.equals("SS")) {
                                logger.debug("从trend中提取到有效信号值: {}", signalValue);
                                return signalValue;
                            } else {
                                logger.debug("提取到无效信号值: {}", signalValue);
                            }
                        }
                    }
                    logger.debug("在trend中未找到有效的signal字段: {}", trend);
                } catch (Exception e) {
                    logger.warn("解析trend字段失败: {}", e.getMessage());
                }
            } else {
                logger.debug("trend字段为空或无效");
            }
        } catch (Exception e) {
            logger.warn("提取信号值失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 应用K线方向过滤 - 在所有信号生成后统一过滤
     * 规则：空头信号不能是上涨K，多头信号不能是下跌K
     */
    public TradeSignal applyCandleDirectionFilter(TradeSignal signal, int index) {
        if (signal == TradeSignal.NEUTRAL) {
            return signal; // 无信号直接返回
        }

        // 应用基础的K线方向过滤
        boolean isBullishCandle = isBullishCandle(index);
        boolean isBearishCandle = isBearishCandle(index);

        // 多头信号过滤：不能是下跌K
        if ((signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) && isBearishCandle) {
            logger.debug("多头信号被K线方向过滤: {} -> 下跌K线，过滤掉", signal);
            return TradeSignal.NEUTRAL;
        }

        // 空头信号过滤：不能是上涨K
        if ((signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) && isBullishCandle) {
            logger.debug("空头信号被K线方向过滤: {} -> 上涨K线，过滤掉", signal);
            return TradeSignal.NEUTRAL;
        }

        // 信号通过过滤
        if (signal != TradeSignal.NEUTRAL) {
            logger.debug("信号通过K线方向过滤: {}", signal);
        }

        return signal;
    }

    /**
     * 获取15分钟周期K线数据
     * 参考 getH1Klines 方法的实现方式
     */
    protected List<Candlestick> get15MinKlines(List<Candlestick> currentKLines, String symbol) {
        try {
            if (currentKLines == null || currentKLines.isEmpty()) {
                return Collections.emptyList();
            }
            Candlestick latest = currentKLines.get(currentKLines.size() - 1);
            return candlestickService.listByLeId(latest.getId(), symbol, OKXMIN15, DEFAULT_KLINES_COUNT);
        } catch (Exception e) {
            logger.warn("获取15分钟周期K线数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取1小时周期K线数据
     * 参考 get15MinKlines 方法的实现方式
     */
    protected List<Candlestick> get1HourKlines(List<Candlestick> currentKLines, String symbol) {
        try {
            if (currentKLines == null || currentKLines.isEmpty()) {
                return Collections.emptyList();
            }
            Candlestick latest = currentKLines.get(currentKLines.size() - 1);
            return candlestickService.listByLeId(latest.getId(), symbol, OKXMIN60, DEFAULT_KLINES_COUNT);
        } catch (Exception e) {
            logger.warn("获取1小时周期K线数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 应用15分钟MACD趋势过滤
     * 规则：如果15分钟MACD处于水下下跌趋势，不出多信号；空头信号反之
     */
    public TradeSignal apply15MinMacdTrendFilter(TradeSignal signal, IndicatorCalcDto calcDto) {
        if (signal == TradeSignal.NEUTRAL) {
            return signal; // 无信号直接返回
        }

        try {
            // 获取15分钟K线数据
            List<Candlestick> kLines15 = get15MinKlines(calcDto.getKLines(), calcDto.getSymbol());

            if (kLines15 == null || kLines15.isEmpty()) {
                logger.debug("15分钟数据不存在，跳过MACD趋势过滤");
                return signal;
            }

            // 构建15分钟序列
            BarSeries series15 = IndicatorWrapHelper.buildSeries(kLines15);

            if (series15.getBarCount() < 35) { // 需要足够的数据计算MACD
                logger.debug("15分钟数据不足，至少需要35根K线，跳过MACD趋势过滤");
                return signal;
            }

            // 计算15分钟MACD指标
            ClosePriceIndicator closePrice15 = new ClosePriceIndicator(series15);
            MACDIndicator macd15 = new MACDIndicator(closePrice15, fastLength, slowLength);
            EMAIndicator signalLine15 = new EMAIndicator(macd15, signalLength);
            int lastIndex15 = series15.getEndIndex() - 1;

            // 获取MACD值
            double macdValue = macd15.getValue(lastIndex15).doubleValue();
            double signalValue = signalLine15.getValue(lastIndex15).doubleValue();

            // 判断趋势
            boolean isMacdUnderwater = macdValue < 0 && signalValue < 0; // MACD在水下
            boolean isMacdBearishTrend = macdValue < signalValue; // MACD死叉趋势

            boolean isMacdAboveWater = macdValue > 0 && signalValue > 0; // MACD在水上
            boolean isMacdBullishTrend = macdValue > signalValue; // MACD金叉趋势

            logger.debug("15分钟MACD趋势分析: MACD={}, Signal={}, 水下={}, 死叉趋势={}, 水上={}, 金叉趋势={}",
                    macdValue, signalValue, isMacdUnderwater, isMacdBearishTrend, isMacdAboveWater, isMacdBullishTrend);

            // 多头信号过滤：如果15分钟MACD处于水下下跌趋势，过滤掉
            if ((signal == TradeSignal.STRONG_LONG || signal == TradeSignal.CAUTIOUS_LONG) &&
                    isMacdUnderwater && isMacdBearishTrend) {
                logger.debug("多头信号被15分钟MACD趋势过滤: {} -> 15分钟MACD水下死叉，过滤掉", signal);
                return TradeSignal.NEUTRAL;
            }

            // 空头信号过滤：如果15分钟MACD处于水上上涨趋势，过滤掉
            if ((signal == TradeSignal.STRONG_SHORT || signal == TradeSignal.CAUTIOUS_SHORT) &&
                    isMacdAboveWater && isMacdBullishTrend) {
                logger.debug("空头信号被15分钟MACD趋势过滤: {} -> 15分钟MACD水上金叉，过滤掉", signal);
                return TradeSignal.NEUTRAL;
            }

            logger.debug("信号通过15分钟MACD趋势过滤: {}", signal);

        } catch (Exception e) {
            logger.warn("15分钟MACD趋势过滤失败: {}", e.getMessage());
            // 过滤失败时不阻挡信号，通过
        }

        return signal;
    }

    /**
     * 判断是否为上涨K线
     */
    private boolean isBullishCandle(int index) {
        if (!validateIndex(index)) return false;

        Bar bar = series.getBar(index);
        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();

        // 收盘价高于开盘价为上涨K线
        return close > open;
    }

    /**
     * 判断是否为下跌K线
     */
    private boolean isBearishCandle(int index) {
        if (!validateIndex(index)) return false;

        Bar bar = series.getBar(index);
        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();

        // 收盘价低于开盘价为下跌K线
        return close < open;
    }


    /**
     * 分析EMA趋势信号
     * 同时检查四个条件：
     * 1. EMA21上穿/下穿EMA60 + 3EMA排列（原条件）
     * 2. EMA9同时上穿/下穿EMA21和EMA60 + 3EMA排列（新条件）
     * 3. EMA9连续趋势发展：倒数第3根下穿EMA60，倒数第2根下穿EMA21，当前保持空头排列（最新条件）
     * 4. EMA144穿越信号：所有EMA在144下方，开盘在144上方，收盘低于所有EMA（空头）；反之（多头）
     */
    private TradeSignal analyzeEMATrendSignal(int index) {
        try {
            // 创建EMA指标
            ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
            EMAIndicator ema9 = new EMAIndicator(closePriceIndicator, 9);
            EMAIndicator ema21 = new EMAIndicator(closePriceIndicator, 21);
            EMAIndicator ema60 = new EMAIndicator(closePriceIndicator, 60);
            EMAIndicator ema144 = new EMAIndicator(closePriceIndicator, 144);

            // 检查是否有足够的数据
            if (index < 144) {
                return TradeSignal.NEUTRAL;
            }

            // 获取当前EMA值
            double ema9Current = ema9.getValue(index).doubleValue();
            double ema21Current = ema21.getValue(index).doubleValue();
            double ema60Current = ema60.getValue(index).doubleValue();
            double ema144Current = ema144.getValue(index).doubleValue();

            // 获取前一个EMA值用于交叉判断
            double ema9Previous = ema9.getValue(index - 1).doubleValue();
            double ema21Previous = ema21.getValue(index - 1).doubleValue();
            double ema60Previous = ema60.getValue(index - 1).doubleValue();

            // 获取当前K线价格
            Bar currentBar = series.getBar(index);
            double openPrice = currentBar.getOpenPrice().doubleValue();
            double closePrice = currentBar.getClosePrice().doubleValue();

            // 判断3EMA排列方向（EMA9, EMA21, EMA60）
            boolean bullishAlignment = ema9Current > ema21Current && ema21Current > ema60Current;
            boolean bearishAlignment = ema9Current < ema21Current && ema21Current < ema60Current;

            // 条件1：EMA21与EMA60的交叉（原条件）
            boolean ema21CrossAboveEma60 = ema21Previous <= ema60Previous && ema21Current > ema60Current;
            boolean ema21CrossBelowEma60 = ema21Previous >= ema60Previous && ema21Current < ema60Current;

            // 条件2：EMA9同时上穿EMA21和EMA60（新条件）
            boolean ema9CrossAboveEma21 = ema9Previous <= ema21Previous && ema9Current > ema21Current;
            boolean ema9CrossAboveEma60 = ema9Previous <= ema60Previous && ema9Current > ema60Current;
            boolean ema9CrossBelowEma21 = ema9Previous >= ema21Previous && ema9Current < ema21Current;
            boolean ema9CrossBelowEma60 = ema9Previous >= ema60Previous && ema9Current < ema60Current;

            // EMA9同时上穿EMA21和EMA60
            boolean ema9DoubleCrossAbove = ema9CrossAboveEma21 && ema9CrossAboveEma60;
            // EMA9同时下穿EMA21和EMA60
            boolean ema9DoubleCrossBelow = ema9CrossBelowEma21 && ema9CrossBelowEma60;

            // 条件3：EMA9连续趋势发展（倒数第3根下穿EMA60，倒数第2根下穿EMA21，当前保持排列）
            boolean ema9ConsecutiveBelow = checkConsecutiveEma9Below(index, ema9, ema21, ema60);
            boolean ema9ConsecutiveAbove = checkConsecutiveEma9Above(index, ema9, ema21, ema60);

            // 条件4：EMA144穿越信号
            TradeSignal ema144CrossSignal = analyzeEMA144CrossSignal(index, ema9, ema21, ema60, ema144);

            // 多头信号：满足任一条件且3EMA多头排列，或EMA144穿越多头信号
            if (((ema21CrossAboveEma60 || ema9DoubleCrossAbove || ema9ConsecutiveAbove) && bullishAlignment) ||
                    ema144CrossSignal == TradeSignal.CAUTIOUS_LONG) {
                String triggerCondition = ema21CrossAboveEma60 ? "EMA21上穿EMA60" :
                        ema9DoubleCrossAbove ? "EMA9同时上穿EMA21和EMA60" :
                                ema9ConsecutiveAbove ? "EMA9连续上穿EMA60和EMA21" :
                                        "EMA144穿越多头信号";
                logger.debug("EMA多头信号 - {} (EMA9:{}, EMA21:{}, EMA60:{}, EMA144:{}, 开盘:{}, 收盘:{})",
                        triggerCondition, ema9Current, ema21Current, ema60Current, ema144Current, openPrice, closePrice);
                return TradeSignal.CAUTIOUS_LONG;
            }

            // 空头信号：满足任一条件且3EMA空头排列，或EMA144穿越空头信号
            if (((ema21CrossBelowEma60 || ema9DoubleCrossBelow || ema9ConsecutiveBelow) && bearishAlignment) ||
                    ema144CrossSignal == TradeSignal.CAUTIOUS_SHORT) {
                String triggerCondition = ema21CrossBelowEma60 ? "EMA21下穿EMA60" :
                        ema9DoubleCrossBelow ? "EMA9同时下穿EMA21和EMA60" :
                                ema9ConsecutiveBelow ? "EMA9连续下穿EMA60和EMA21" :
                                        "EMA144穿越空头信号";
                logger.debug("EMA空头信号 - {} (EMA9:{}, EMA21:{}, EMA60:{}, EMA144:{}, 开盘:{}, 收盘:{})",
                        triggerCondition, ema9Current, ema21Current, ema60Current, ema144Current, openPrice, closePrice);
                return TradeSignal.CAUTIOUS_SHORT;
            }

            // 记录当前状态用于调试
            logger.debug("EMA状态 - 多头排列:{}, 空头排列:{}, EMA21交叉:{}, EMA9双交叉:{}, EMA9连续:{}, EMA144穿越:{}, 值:{},{},{},{}",
                    bullishAlignment, bearishAlignment,
                    ema21CrossAboveEma60 ? "上穿" : ema21CrossBelowEma60 ? "下穿" : "无",
                    ema9DoubleCrossAbove ? "双上穿" : ema9DoubleCrossBelow ? "双下穿" : "无",
                    ema9ConsecutiveAbove ? "连续上穿" : ema9ConsecutiveBelow ? "连续下穿" : "无",
                    ema144CrossSignal != TradeSignal.NEUTRAL ? ema144CrossSignal.toString() : "无",
                    ema9Current, ema21Current, ema60Current, ema144Current);

        } catch (Exception e) {
            logger.warn("EMA趋势信号分析失败: {}", e.getMessage());
        }

        return TradeSignal.NEUTRAL;
    }

    /**
     * 检查EMA9连续下穿的趋势发展：倒数第3根下穿EMA60，倒数第2根下穿EMA21，当前保持空头排列
     */
    private boolean checkConsecutiveEma9Below(int currentIndex, EMAIndicator ema9, EMAIndicator ema21, EMAIndicator ema60) {
        try {
            // 记录调试时间
            if (series != null && currentIndex >= 0 && currentIndex < series.getBarCount()) {

            }

            // 需要至少3根K线的历史数据
            if (currentIndex < 2) {
                logger.debug("checkConsecutiveEma9Below - 数据不足，currentIndex:{}", currentIndex);
                return false;
            }

            // 倒数第3根K线（currentIndex - 2）：EMA9下穿EMA60
            int thirdIndex = currentIndex - 2;
            double ema9Third = ema9.getValue(thirdIndex).doubleValue();
            double ema60Third = ema60.getValue(thirdIndex).doubleValue();
            double ema9ThirdPrev = ema9.getValue(thirdIndex - 1).doubleValue();
            double ema60ThirdPrev = ema60.getValue(thirdIndex - 1).doubleValue();

            boolean thirdCrossBelow60 = ema9ThirdPrev >= ema60ThirdPrev && ema9Third < ema60Third;

            // 倒数第2根K线（currentIndex - 1）：EMA9下穿EMA21
            int secondIndex = currentIndex - 1;
            double ema9Second = ema9.getValue(secondIndex).doubleValue();
            double ema21Second = ema21.getValue(secondIndex).doubleValue();
            double ema9SecondPrev = ema9.getValue(secondIndex - 1).doubleValue();
            double ema21SecondPrev = ema21.getValue(secondIndex - 1).doubleValue();

            boolean secondCrossBelow21 = ema9SecondPrev >= ema21SecondPrev && ema9Second < ema21Second;

            // 当前K线（currentIndex）：保持空头排列（EMA9 < EMA21 < EMA60）
            double ema9Current = ema9.getValue(currentIndex).doubleValue();
            double ema21Current = ema21.getValue(currentIndex).doubleValue();
            double ema60Current = ema60.getValue(currentIndex).doubleValue();

            boolean currentBearishAlignment = ema9Current < ema21Current && ema21Current < ema60Current;

            // 所有条件都必须满足
            boolean result = thirdCrossBelow60 && secondCrossBelow21 && currentBearishAlignment;

            if (result) {
                logger.debug("EMA9连续下穿条件满足: 第3根下穿EMA60({}<={}), 第2根下穿EMA21({}<={}), 当前空头排列({}<{}<{})",
                        ema9Third, ema60Third, ema9Second, ema21Second, ema9Current, ema21Current, ema60Current);
            }

            return result;

        } catch (Exception e) {
            logger.warn("检查EMA9连续下穿失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 分析EMA144穿越信号
     * 空头信号：EMA9,21,60都在144EMA下方，开盘价在144EMA上方，收盘价低于所有EMA
     * 多头信号：EMA9,21,60都在144EMA上方，开盘价在144EMA下方，收盘价高于所有EMA
     */
    private TradeSignal analyzeEMA144CrossSignal(int index, EMAIndicator ema9, EMAIndicator ema21, EMAIndicator ema60, EMAIndicator ema144) {
        try {
            // 获取当前EMA值
            double ema9Current = ema9.getValue(index).doubleValue();
            double ema21Current = ema21.getValue(index).doubleValue();
            double ema60Current = ema60.getValue(index).doubleValue();
            double ema144Current = ema144.getValue(index).doubleValue();

            // 获取当前K线价格
            Bar currentBar = series.getBar(index);
            double openPrice = currentBar.getOpenPrice().doubleValue();
            double closePrice = currentBar.getClosePrice().doubleValue();

            // 空头信号条件：
            // 1. EMA9, EMA21, EMA60都在EMA144下方
            // 2. 开盘价在EMA144上方
            // 3. 收盘价低于所有EMA（EMA9, EMA21, EMA60, EMA144）
            boolean bearishEMAsBelow144 = ema9Current < ema144Current && ema21Current < ema144Current && ema60Current < ema144Current;
            boolean bearishOpenAbove144 = openPrice > ema144Current;
            boolean bearishCloseBelowAll = closePrice < ema9Current && closePrice < ema21Current &&
                    closePrice < ema60Current && closePrice < ema144Current;

            if (bearishEMAsBelow144 && bearishOpenAbove144 && bearishCloseBelowAll) {
                logger.debug("EMA144穿越空头信号满足: EMAs在144下方({}<{}), 开盘在144上方({}>{}), 收盘低于所有EMA({}<all)",
                        ema9Current, ema144Current, openPrice, ema144Current, closePrice);
                return TradeSignal.CAUTIOUS_SHORT;
            }

            // 多头信号条件：
            // 1. EMA9, EMA21, EMA60都在EMA144上方
            // 2. 开盘价在EMA144下方
            // 3. 收盘价高于所有EMA（EMA9, EMA21, EMA60, EMA144）
            boolean bullishEMAsAbove144 = ema9Current > ema144Current && ema21Current > ema144Current && ema60Current > ema144Current;
            boolean bullishOpenBelow144 = openPrice < ema144Current;
            boolean bullishCloseAboveAll = closePrice > ema9Current && closePrice > ema21Current &&
                    closePrice > ema60Current && closePrice > ema144Current;

            if (bullishEMAsAbove144 && bullishOpenBelow144 && bullishCloseAboveAll) {
                logger.debug("EMA144穿越多头信号满足: EMAs在144上方({}>{}), 开盘在144下方({}<{}), 收盘高于所有EMA({}>all)",
                        ema9Current, ema144Current, openPrice, ema144Current, closePrice);
                return TradeSignal.CAUTIOUS_LONG;
            }

        } catch (Exception e) {
            logger.warn("EMA144穿越信号分析失败: {}", e.getMessage());
        }

        return TradeSignal.NEUTRAL;
    }

    /**
     * 检查EMA9连续上穿的趋势发展：倒数第3根上穿EMA60，倒数第2根上穿EMA21，当前保持多头排列
     */
    private boolean checkConsecutiveEma9Above(int currentIndex, EMAIndicator ema9, EMAIndicator ema21, EMAIndicator ema60) {
        try {
            // 记录调试时间
            if (series != null && currentIndex >= 0 && currentIndex < series.getBarCount()) {

            }

            // 需要至少3根K线的历史数据
            if (currentIndex < 2) {
                logger.debug("checkConsecutiveEma9Above - 数据不足，currentIndex:{}", currentIndex);
                return false;
            }

            // 倒数第3根K线（currentIndex - 2）：EMA9上穿EMA60
            int thirdIndex = currentIndex - 2;
            double ema9Third = ema9.getValue(thirdIndex).doubleValue();
            double ema60Third = ema60.getValue(thirdIndex).doubleValue();
            double ema9ThirdPrev = ema9.getValue(thirdIndex - 1).doubleValue();
            double ema60ThirdPrev = ema60.getValue(thirdIndex - 1).doubleValue();

            boolean thirdCrossAbove60 = ema9ThirdPrev <= ema60ThirdPrev && ema9Third > ema60Third;

            // 倒数第2根K线（currentIndex - 1）：EMA9上穿EMA21
            int secondIndex = currentIndex - 1;
            double ema9Second = ema9.getValue(secondIndex).doubleValue();
            double ema21Second = ema21.getValue(secondIndex).doubleValue();
            double ema9SecondPrev = ema9.getValue(secondIndex - 1).doubleValue();
            double ema21SecondPrev = ema21.getValue(secondIndex - 1).doubleValue();

            boolean secondCrossAbove21 = ema9SecondPrev <= ema21SecondPrev && ema9Second > ema21Second;

            // 当前K线（currentIndex）：保持多头排列（EMA9 > EMA21 > EMA60）
            double ema9Current = ema9.getValue(currentIndex).doubleValue();
            double ema21Current = ema21.getValue(currentIndex).doubleValue();
            double ema60Current = ema60.getValue(currentIndex).doubleValue();

            boolean currentBullishAlignment = ema9Current > ema21Current && ema21Current > ema60Current;

            // 所有条件都必须满足
            boolean result = thirdCrossAbove60 && secondCrossAbove21 && currentBullishAlignment;

            if (result) {
                logger.debug("EMA9连续上穿条件满足: 第3根上穿EMA60({}>={}), 第2根上穿EMA21({}>={}), 当前多头排列({}>{}>{})",
                        ema9Third, ema60Third, ema9Second, ema21Second, ema9Current, ema21Current, ema60Current);
            }

            return result;

        } catch (Exception e) {
            logger.warn("检查EMA9连续上穿失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 分析信号盈亏模式
     * 假设：每个反向信号都会平仓，通过比较信号价格计算盈亏
     */
    private SignalAnalysisResult analyzeSignalProfitability(
            List<TradeSignalSignal> sortedSignals) {

        SignalAnalysisResult result = new SignalAnalysisResult();

        if (sortedSignals.size() < 2) {
            return result;
        }

        int totalTrades = 0;
        int profitableTrades = 0;
        int consecutiveLosses = 0;
        int maxConsecutiveLosses = 0;
        List<Double> profitPercentages = new ArrayList<>();
        List<Boolean> profitableFlags = new ArrayList<>();

        // 遍历信号，当出现反向信号时计算盈亏
        // 注意：sortedSignals是升序排列（旧到新），所以get(0)是最旧的，get(1)是次旧的
        for (int i = 0; i < sortedSignals.size() - 1; i++) {
            TradeSignalSignal entrySignal = sortedSignals.get(i);     // 较旧的信号（入场）
            TradeSignalSignal exitSignal = sortedSignals.get(i + 1);  // 较新的信号（出场）

            String entrySignalType = extractSignalFromTradeSignalSignal(entrySignal);
            String exitSignalType = extractSignalFromTradeSignalSignal(exitSignal);

            // 只有当信号方向相反时才计算盈亏
            if (entrySignalType != null && exitSignalType != null &&
                    !entrySignalType.equals(exitSignalType)) {

                // 获取信号价格
                BigDecimal entryPrice = entrySignal.getClosePrice();
                BigDecimal exitPrice = exitSignal.getClosePrice();

                if (entryPrice != null && exitPrice != null) {
                    // 计算盈亏百分比 - 基于市场价格变化判断盈利空间
                    double profitPct = calculateProfitPercentageForSignal(
                            entrySignalType, entryPrice, exitPrice);

                    totalTrades++;
                    profitPercentages.add(profitPct);

                    // 判断是否有盈利空间（市场向有利方向移动）
                    // 对于买入信号，价格上涨是有利的；对于卖出信号，价格下跌是有利的
                    boolean hasProfitPotential = profitPct >= profitTarget;
                    profitableFlags.add(hasProfitPotential);

                    if (hasProfitPotential) {
                        profitableTrades++;
                        consecutiveLosses = 0; // 有盈利空间，重置连败计数
                        logger.debug("有盈利空间: {}[{}] -> {}[{}], 价格变化={:.2f}%",
                                entrySignalType, entryPrice, exitSignalType, exitPrice, profitPct * 100);
                    } else {
                        // 市场价格变化不足以产生盈利空间
                        consecutiveLosses++;
                        maxConsecutiveLosses = Math.max(maxConsecutiveLosses, consecutiveLosses);
                        logger.debug("无盈利空间: {}[{}] -> {}[{}], 价格变化={:.2f}%, 连败={}",
                                entrySignalType, entryPrice, exitSignalType, exitPrice, profitPct * 100, consecutiveLosses);
                    }
                }
            }
        }

        // 设置分析结果
        result.setTotalTrades(totalTrades);
        result.setProfitableTrades(profitableTrades);
        result.setConsecutiveLosses(maxConsecutiveLosses);
        result.setProfitPercentages(profitPercentages);
        result.setProfitableFlags(profitableFlags);

        // 设置最近一次交易是否盈利 - 基于最后两个信号的实际交易
        if (sortedSignals.size() >= 2) {
            // 检查最后两个信号是否形成了有效的交易
            TradeSignalSignal lastExitSignal = sortedSignals.get(sortedSignals.size() - 2); // 倒数第二个（较旧）
            TradeSignalSignal lastEntrySignal = sortedSignals.get(sortedSignals.size() - 1); // 最后一个（较新）

            String lastExitSignalType = extractSignalFromTradeSignalSignal(lastExitSignal);
            String lastEntrySignalType = extractSignalFromTradeSignalSignal(lastEntrySignal);

            // 如果最后两个信号类型相反，则计算它们的交易盈亏
            if (lastExitSignalType != null && lastEntrySignalType != null &&
                    !lastExitSignalType.equals(lastEntrySignalType)) {

                BigDecimal lastExitPrice = lastExitSignal.getClosePrice();
                BigDecimal lastEntryPrice = lastEntrySignal.getClosePrice();

                if (lastExitPrice != null && lastEntryPrice != null) {
                    double lastProfitPct = calculateProfitPercentageForSignal(
                            lastExitSignalType, lastExitPrice, lastEntryPrice);
                    boolean lastTradeHasProfitPotential = lastProfitPct >= profitTarget;
                    result.setLastTradeProfitable(lastTradeHasProfitPotential);

                    logger.debug("最后交易盈亏计算: {}[{}] -> {}[{}], 价格变化={:.2f}%, 有盈利空间={}",
                            lastExitSignalType, lastExitPrice, lastEntrySignalType, lastEntryPrice,
                            lastProfitPct * 100, lastTradeHasProfitPotential);
                } else {
                    // 如果价格数据不完整，使用历史交易的最后结果
                    if (!profitableFlags.isEmpty()) {
                        result.setLastTradeProfitable(profitableFlags.get(profitableFlags.size() - 1));
                    }
                }
            } else {
                // 如果最后两个信号类型相同，没有完成交易，使用历史交易的最后结果
                if (!profitableFlags.isEmpty()) {
                    result.setLastTradeProfitable(profitableFlags.get(profitableFlags.size() - 1));
                }
            }
        } else {
            // 如果信号不足，使用历史交易的最后结果
            if (!profitableFlags.isEmpty()) {
                result.setLastTradeProfitable(profitableFlags.get(profitableFlags.size() - 1));
            }
        }

        // 计算平均盈利
        if (!profitPercentages.isEmpty()) {
            double avgProfit = profitPercentages.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            result.setAvgProfit(avgProfit);
        }

        // 计算胜率
        if (totalTrades > 0) {
            double winRate = (double) profitableTrades / totalTrades;
            result.setWinRate(winRate);
            result.setProfitRatio(winRate); // 使用胜率作为盈利比率
        }

        return result;
    }

    /**
     * 计算盈利百分比
     */
    private double calculateProfitPercentage(String entrySignalType, BigDecimal entryPrice, BigDecimal exitPrice) {
        if (entryPrice == null || exitPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        boolean isLong = "LONG".equals(entrySignalType) || "LB".equals(entrySignalType);
        boolean isShort = "SHORT".equals(entrySignalType) || "SB".equals(entrySignalType);

        if (isLong) {
            // 做多：盈利 = (出场价 - 进场价) / 进场价
            return exitPrice.subtract(entryPrice)
                    .divide(entryPrice, 6, RoundingMode.HALF_UP)
                    .doubleValue();
        } else if (isShort) {
            // 做空：盈利 = (进场价 - 出场价) / 进场价
            return entryPrice.subtract(exitPrice)
                    .divide(entryPrice, 6, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return 0.0;
    }

    /**
     * 计算信号的盈利空间 - 基于市场价格变化判断是否有利可图
     * 对于每个信号，我们判断市场价格相对于该信号是否有利
     */
    private double calculateProfitPercentageForSignal(String entrySignalType, BigDecimal entryPrice, BigDecimal exitPrice) {
        if (entryPrice == null || exitPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        // 计算价格变化百分比：(exitPrice - entryPrice) / entryPrice
        double priceChangePct = exitPrice.subtract(entryPrice)
                .divide(entryPrice, 6, RoundingMode.HALF_UP)
                .doubleValue();

        // 对于买入信号(LONG/LB)，价格上涨是有利的（正值）
        // 对于卖出信号(SHORT/SB)，价格下跌是有利的（负值，但我们取绝对值作为盈利空间）
        boolean isLong = "LONG".equals(entrySignalType) || "LB".equals(entrySignalType);
        boolean isShort = "SHORT".equals(entrySignalType) || "SB".equals(entrySignalType);

        if (isLong) {
            // 买入信号：价格上涨程度
            return priceChangePct;
        } else if (isShort) {
            // 卖出信号：价格下跌程度（用负值表示下跌幅度）
            return -priceChangePct;
        }

        return 0.0;
    }

    /**
     * 基于盈亏分析更新横盘状态
     * 核心规则：只有当第n个信号到第n-1个信号有盈利空间时，才重置Counter
     */
    private void updateConsolidationState(ConsolidationState state, SignalAnalysisResult analysisResult) {
        // 1. 检查最近一次交易是否有盈利空间
        boolean lastTradeHasProfitPotential = analysisResult.isLastTradeProfitable();

        // 2. 根据是否有盈利空间更新Counter
        if (lastTradeHasProfitPotential) {
            // 有盈利空间：适度减少Counter，不完全重置
            state.setCounter(Math.max(0, state.getCounter() - 2));
            logger.info("有盈利空间，Counter减少2，当前Counter: {}", state.getCounter());
        } else {
            // 无盈利空间：增加Counter
            state.setCounter(state.getCounter() + 1);
            logger.debug("无盈利空间，Counter增加1，当前Counter: {}", state.getCounter());
        }

        // 3. 基于整体表现更新市场状态
        double winRate = analysisResult.getWinRate();
        int totalTrades = analysisResult.getTotalTrades();

        if (totalTrades >= 5) {
            if (winRate < 0.3) {
                state.setMarketState("HIGH_CHOPPINESS"); // 高波动横盘
            } else if (winRate < 0.5) {
                state.setMarketState("MEDIUM_CHOPPINESS"); // 中等横盘
            } else if (winRate >= 0.7) {
                state.setMarketState("TRENDING"); // 趋势明显
            } else {
                state.setMarketState("NEUTRAL"); // 中性
            }
        } else {
            state.setMarketState("INSUFFICIENT_DATA");
        }

        // 4. 更新交替率（通过分析信号模式）
        state.setAlternationRate(calculateAlternationRateFromAnalysis(analysisResult));

        state.setLastUpdateTime(LocalDateTime.now());
    }

    /**
     * 计算交替率（基于盈亏分析）
     */
    private double calculateAlternationRateFromAnalysis(SignalAnalysisResult analysisResult) {
        List<Double> profits = analysisResult.getProfitPercentages();
        if (profits.size() < 2) {
            return 0.0;
        }

        int alternations = 0;
        for (int i = 1; i < profits.size(); i++) {
            // 如果相邻两个交易的盈亏方向相反（一正一负），则视为交替
            if ((profits.get(i - 1) >= 0 && profits.get(i) < 0) ||
                    (profits.get(i - 1) < 0 && profits.get(i) >= 0)) {
                alternations++;
            }
        }

        return (double) alternations / (profits.size() - 1);
    }

    /**
     * 计算增量金额 - 基于盈利情况和上次权重设置不同的增量
     */
    private double getIncrementAmount(double lastWeight, SignalAnalysisResult analysisResult) {
        // 基础增量非常小
        double baseIncrement = 0.01; // 仅1%

        // 根据最近一次交易是否盈利调整增量
        if (analysisResult.isLastTradeProfitable()) {
            // 盈利后：增量更小
            baseIncrement *= 0.5;
        } else {
            // 亏损后：增量稍微大一点
            baseIncrement *= 0.8;
        }

        // 权重越大，增量越小（更陡的衰减）
        double weightMultiplier = 1.0 / (1.0 + lastWeight * 0.5);

        return baseIncrement * weightMultiplier;
    }

    /**
     * 计算激进的动态权重 - 显著增加权重变化幅度
     */
    private double calculateDynamicWeight(ConsolidationState state,
                                          SignalAnalysisResult analysisResult,
                                          List<TradeSignalSignal> signals) {

        // 1. 基于Counter和盈利情况计算基础权重
        double baseWeightFromCounter = calculateConservativeBaseWeight(state.getCounter(), analysisResult.isLastTradeProfitable());

        // 2. 基于性能调整权重
        double performanceFactor = calculateSimplePerformanceFactor(analysisResult);

        // 3. 综合计算
        double comprehensiveWeight = baseWeightFromCounter * performanceFactor;

        // 4. 确保权重递增（连续亏损时强制增加）
        double lastWeight = state.getLastWeight();
        double minRequiredWeight = lastWeight;

        // 如果最近一次交易没有盈利，强制增加权重
        if (!analysisResult.isLastTradeProfitable()) {
            double increment = getAggressiveIncrement(analysisResult.getConsecutiveLosses());
            minRequiredWeight = lastWeight * (1.0 + increment);
        } else {
            // 盈利时仍保持一定递增
            minRequiredWeight = lastWeight + getSimpleIncrementAmount(true);
        }

        // 5. 取较大值
        double preLimitedWeight = Math.max(comprehensiveWeight, minRequiredWeight);

        // 6. 应用安全限制
        double finalWeight = applySafetyLimits(preLimitedWeight);

        // 7. 更新上次权重
        state.setLastWeight(finalWeight);

        // 8. 记录计算过程
        logger.info("权重计算详情 - 基础权重: {:.2f}, 性能因子: {:.2f}, 最终权重: {:.2f}, 递增要求: {:.2f}",
                baseWeightFromCounter, performanceFactor, finalWeight, minRequiredWeight);

        return finalWeight;
    }

    /**
     * 基于counter计算基础权重
     */
    private double calculateBaseWeightFromCounter(int counter) {
        // 分段计算：counter越大，权重越高，但增长速率不同
        if (counter <= 0) {
            return baseWeight; // 无横盘迹象，使用配置的基础权重
        } else if (counter < 5) {
            // 初期：缓慢增长
            return baseWeight * (1.0 + counter * 0.05); // 改为5%
        } else if (counter < 15) {
            // 中期：适度增长
            return baseWeight * (1.25 + (counter - 5) * 0.04); // 改为4%
        } else if (counter < 30) {
            // 后期：加速增长
            return baseWeight * (1.65 + (counter - 15) * 0.03); // 改为3%
        } else {
            // 超长期：稳定增长，但有上限
            double weight = baseWeight * (2.1 + (counter - 30) * 0.02); // 改为2%
            return Math.min(weight, maxWeight);
        }
    }

    /**
     * 基于盈利表现调整权重
     */
    private double calculatePerformanceFactor(SignalAnalysisResult analysisResult) {
        double performance = 1.0;

        boolean lastTradeProfitable = analysisResult.isLastTradeProfitable();
        int consecutiveLosses = analysisResult.getConsecutiveLosses();
        double winRate = analysisResult.getWinRate();
        double avgProfit = analysisResult.getAvgProfit();

        // 1. 最近一次交易盈利奖励
        if (lastTradeProfitable) {
            performance *= 1.1; // 盈利后，适度增加权重
            logger.debug("最近交易盈利，性能因子增加10%");
        }

        // 2. 连败惩罚
        if (consecutiveLosses >= 5) {
            performance *= 0.7; // 严重连败，权重减少30%
        } else if (consecutiveLosses >= 3) {
            performance *= 0.8;
        } else if (consecutiveLosses >= 1) {
            performance *= 0.9;
        }

        // 3. 胜率奖励
        if (winRate >= 0.7) {
            performance *= 1.3; // 高胜率，增加权重
        } else if (winRate >= 0.5) {
            performance *= 1.1;
        } else if (winRate < 0.3) {
            performance *= 0.8; // 低胜率，降低权重
        }

        // 限制在合理范围内
        return Math.max(0.8, Math.min(1.2, performance));
    }

    /**
     * 保守的性能因子计算
     */
    private double calculateConservativePerformanceFactor(SignalAnalysisResult analysisResult) {
        double performance = 1.0;

        boolean lastTradeProfitable = analysisResult.isLastTradeProfitable();
        int consecutiveLosses = analysisResult.getConsecutiveLosses();
        double winRate = analysisResult.getWinRate();

        // 1. 最近一次交易盈利奖励
        if (lastTradeProfitable) {
            performance *= 1.05; // 盈利后：仅增加5%
        }

        // 2. 连败惩罚
        if (consecutiveLosses >= 3) {
            performance *= 0.85; // 连续亏损3次：减少15%
        } else if (consecutiveLosses >= 2) {
            performance *= 0.9;
        } else if (consecutiveLosses >= 1) {
            performance *= 0.95;
        }

        // 3. 胜率奖励/惩罚
        if (winRate >= 0.7) {
            performance *= 1.1; // 高胜率：增加10%
        } else if (winRate >= 0.5) {
            performance *= 1.0; // 中等胜率：不变
        } else if (winRate < 0.3) {
            performance *= 0.9; // 低胜率：减少10%
        }

        // 限制在合理范围内
        return Math.max(0.8, Math.min(1.2, performance));
    }

    /**
     * 基于信号质量调整权重
     */
    private double calculateSignalQualityFactor(List<TradeSignalSignal> signals) {
        if (signals.size() < 2) {
            return 1.0;
        }

        double quality = 1.0;

        // 检查最近信号的盈亏一致性
        List<Double> recentProfits = new ArrayList<>();
        for (int i = Math.max(0, signals.size() - 5); i < signals.size() - 1; i++) {
            String signal1 = extractSignalFromTradeSignalSignal(signals.get(i));
            String signal2 = extractSignalFromTradeSignalSignal(signals.get(i + 1));

            if (signal1 != null && signal2 != null && !signal1.equals(signal2)) {
                BigDecimal price1 = signals.get(i).getClosePrice();
                BigDecimal price2 = signals.get(i + 1).getClosePrice();

                if (price1 != null && price2 != null) {
                    double profit = calculateProfitPercentage(signal1, price1, price2);
                    recentProfits.add(profit);
                }
            }
        }

        // 如果最近信号盈亏方向一致，说明信号质量较高
        if (recentProfits.size() >= 2) {
            boolean allPositive = recentProfits.stream().allMatch(p -> p >= 0);
            boolean allNegative = recentProfits.stream().allMatch(p -> p <= 0);

            if (allPositive || allNegative) {
                quality *= 1.2; // 一致性高，增加权重
            } else {
                quality *= 0.9; // 不一致，降低权重
            }
        }

        return Math.max(0.7, Math.min(1.3, quality));
    }

    /**
     * 基于市场状态调整权重
     */
    private double calculateMarketStateFactor(ConsolidationState state) {
        switch (state.getMarketState()) {
            case "HIGH_CHOPPINESS":
                return 0.5; // 高波动横盘，降低权重
            case "MEDIUM_CHOPPINESS":
                return 0.8;
            case "NEUTRAL":
                return 1.0;
            case "TRENDING":
                return 1.3; // 趋势中，增加权重
            default:
                return 1.0;
        }
    }

    /**
     * 基于风险控制调整权重
     */
    private double calculateRiskFactor(ConsolidationState state, SignalAnalysisResult analysisResult) {
        double riskFactor = 1.0;

        // 1. counter太大风险控制
        if (state.getCounter() > 50) {
            riskFactor *= 0.7; // 横盘太久，降低权重
        } else if (state.getCounter() > 30) {
            riskFactor *= 0.8;
        }

        // 2. 极端连败风险控制
        if (analysisResult.getConsecutiveLosses() >= 8) {
            riskFactor *= 0.5; // 极端连败，严重降低权重
        } else if (analysisResult.getConsecutiveLosses() >= 5) {
            riskFactor *= 0.7;
        }

        // 3. 低胜率风险控制
        if (analysisResult.getWinRate() < 0.2 && analysisResult.getTotalTrades() >= 10) {
            riskFactor *= 0.6; // 胜率极低，严重降低权重
        }

        return Math.max(0.3, Math.min(1.2, riskFactor));
    }

    /**
     * 应用安全限制（增加回归机制）
     */
    private double applySafetyLimits(double weight) {
        // 基础限制
        double limitedWeight = Math.max(minWeight, Math.min(maxWeight, weight));

        // 如果权重接近上限，检查是否需要回归
        if (limitedWeight >= maxWeight * 0.9) {
            logger.warn("权重接近上限{}, 考虑回归基础仓位", maxWeight);
            // 可以考虑记录日志或触发报警
        }

        return limitedWeight;
    }

    /**
     * 获取或初始化横盘状态
     */
    private ConsolidationState getOrCreateConsolidationState(IndicatorCalcDto calcDto) {
        String symbol = calcDto.getSymbol();
        String interval = calcDto.getCandlestickIntervalEnum().getCode();
        String robotId = calcDto.getRobotId();
        String cacheKey = String.format("%s_%s_%s", symbol, interval, robotId);

        ConsolidationState state = consolidationCache.computeIfAbsent(cacheKey, k -> {
            ConsolidationState newState = new ConsolidationState();
            newState.setLastWeight(baseWeight); // 初始权重设为配置的基础权重
            return newState;
        });

        return state;
    }

    /**
     * 重置权重计数器（在某些条件下）
     */
    private void resetWeightIfNeeded(ConsolidationState state, SignalAnalysisResult analysisResult) {
        // 条件1: 连续盈利后，可以适当重置权重，避免权重过大 (放宽条件，从5次改为10次)
        if (analysisResult.getConsecutiveLosses() == 0 &&
                analysisResult.getProfitableTrades() >= 10) {
            logger.info("连续10次盈利，重置权重到基础值");
            state.setLastWeight(baseWeight);
        }

        // 条件2: 长时间未交易（超过24小时）
        LocalDateTime now = LocalDateTime.now();
        if (state.getLastUpdateTime().isBefore(now.minusHours(24))) {
            logger.info("超过24小时未交易，重置权重");
            state.setLastWeight(baseWeight);
        }

        // 条件3: 权重达到上限后，如果出现亏损，重置权重
       /* if (state.getLastWeight() >= maxWeight * 0.8 &&
                analysisResult.getConsecutiveLosses() >= 3) {
            logger.info("权重接近上限且连续亏损3次，重置权重");
            state.setLastWeight(baseWeight);
        }*/
    }

    /**
     * 判断是否需要回归基础仓位
     */
    private boolean shouldResetToBaseWeight(ConsolidationState state, SignalAnalysisResult analysisResult) {
        // 放宽回归条件
        if (analysisResult.getConsecutiveLosses() >= 8) { // 从5提高到8
            return true;
        }

        if (state.getCounter() >= 30) { // 从20提高到30
            return true;
        }

        if (analysisResult.getTotalTrades() >= 15 && analysisResult.getWinRate() < 0.2) { // 放宽条件
            return true;
        }

        // 移除：权重接近上限且连续亏损时降回1.0的条件
        // if (state.getLastWeight() >= maxWeight * 0.8 && analysisResult.getConsecutiveLosses() >= 3) {
        //     return true;
        // }

        return false;
    }

    /**
     * 简单递增权重（用于信号不足时）
     */
    private double incrementWeight(double lastWeight) {
        // 对于信号不足的情况，使用默认的增量计算（假设最近交易不盈利）
        SignalAnalysisResult defaultResult = new SignalAnalysisResult();
        defaultResult.setLastTradeProfitable(false); // 假设最近交易不盈利，使用较大的增量

        double incremented = lastWeight + getIncrementAmount(lastWeight, defaultResult);
        return Math.min(maxWeight, incremented);
    }

    /**
     * 获取用于权重计算的历史信号
     */
    private List<TradeSignalSignal> getRecentSignalsForWeight(
            IndicatorCalcDto calcDto, int limit) {
        try {
            if (tradeSignalSignalService == null) {
                logger.warn("tradeSignalSignalService未注入");
                return new ArrayList<>();
            }

            String robotId = calcDto.getRobotId();
            if (robotId == null || robotId.trim().isEmpty()) {
                return new ArrayList<>();
            }

            return tradeSignalSignalService.queryRecentSignals(
                    OKX.toString(),
                    calcDto.getCandlestickIntervalEnum().getCode(),
                    calcDto.getSymbol(),
                    robotId,
                    limit
            );

        } catch (Exception e) {
            logger.error("获取历史信号失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 按时间排序信号
     *
     * @param ascending true=升序（旧到新），false=降序（新到旧）
     */
    private List<TradeSignalSignal> sortSignalsByTime(
            List<TradeSignalSignal> signals, boolean ascending) {

        List<TradeSignalSignal> sorted = new ArrayList<>(signals);

        sorted.sort((s1, s2) -> {
            if (s1.getKlineTime() == null || s2.getKlineTime() == null) {
                return 0;
            }
            int comparison = s1.getKlineTime().compareTo(s2.getKlineTime());
            return ascending ? comparison : -comparison;
        });

        return sorted;
    }

    // ================= 测试数据生成 =================


    // ================= 多时间框架趋势检测 =================

    /**
     * 信号交替检查：不允许连续相同类型的信号
     * LONG信号必须在SHORT信号之后，SHORT信号必须在LONG信号之后
     *
     * @param calcDto            计算上下文
     * @param currentPrice       当前价格 (保留参数以保持兼容性)
     * @param expectedSignalType 期望的信号类型 ("LONG" for BUY, "SHORT" for SELL)
     * @param priceComparator    价格比较器 (已废弃，传null即可)
     * @return 是否满足信号交替条件
     */
    private boolean checkSignalAlternationCondition(IndicatorCalcDto calcDto, double currentPrice,
                                                    String expectedSignalType, BiPredicate<Double, Double> priceComparator) {
        try {
            if (tradeSignalSignalService == null) {
                logger.warn("tradeSignalSignalService未注入，跳过连续信号价格检查");
                return true; // 默认允许
            }

            String robotId = calcDto.getRobotId();
            logger.info("连续{}信号价格检查开始 - 机器人ID: {}, 交易对: {}", expectedSignalType, robotId, calcDto.getSymbol());

            if (robotId == null) {
                logger.warn("robotId为空，无法查询信号历史，默认允许当前信号");
                return true; // 默认允许
            }

            // 使用queryLatestOpenSignal查询最新的开仓信号
            TradeSignalSignal latestSignal =
                    tradeSignalSignalService.queryLatestOpenSignal(
                            OKX.toString(),
                            calcDto.getSymbol(),
                            "MACD",  // 指标类型：MACD
                            robotId  // 机器人ID
                    );

            if (latestSignal == null) {
                logger.info("没有找到最新的开仓信号 (机器人ID: {}, 交易对: {})，默认允许当前信号", robotId, calcDto.getSymbol());
                return true;
            }

            logger.info("找到最新信号 - ID: {}, 创建时间: {}", latestSignal.getId(), latestSignal.getCreateTime());


            // 检查最新的信号类型 - 强制信号交替
            String signalValue = extractSignalFromTradeSignalSignal(latestSignal);
            logger.info("最新信号类型解析结果: {} (当前期望类型: {})", signalValue, expectedSignalType);

            // 信号交替检查（兼容新旧值）
            boolean signalAlternationAllowed = false;
            boolean isPrevShort = "SB".equals(signalValue) || "SHORT".equals(signalValue);
            boolean isPrevLong = "LB".equals(signalValue) || "LONG".equals(signalValue);
            boolean isExpectedShort = "SB".equals(expectedSignalType) || "SHORT".equals(expectedSignalType);
            boolean isExpectedLong = "LB".equals(expectedSignalType) || "LONG".equals(expectedSignalType);

            if (isPrevShort && isExpectedLong) {
                // 最新是SELL/SHORT，当前是BUY/LONG - 允许
                signalAlternationAllowed = true;
                logger.info("信号交替检查通过: 最新SELL/SHORT信号后允许BUY/LONG信号");
            } else if (isPrevLong && isExpectedShort) {
                // 最新是BUY/LONG，当前是SELL/SHORT - 允许
                signalAlternationAllowed = true;
                logger.info("信号交替检查通过: 最新BUY/LONG信号后允许SELL/SHORT信号");
            } else {
                // 信号类型相同 - 不允许
                logger.info("信号交替检查失败: 连续{}信号不允许 (必须交替BUY/SELL)", expectedSignalType);
            }

            return signalAlternationAllowed;

        } catch (Exception e) {
            logger.warn("连续信号价格检查失败: {}", e.getMessage());
            return true; // 出错时默认允许，避免阻塞正常信号
        }
    }

    /**
     * 重写getWeight方法，实现基于信号价格盈亏的动态权重计算
     * 核心假设：反向信号必须平仓，通过比较信号价格计算盈亏
     */
    @Override
    public Double getWeight(IndicatorCalcDto calcDto) {
        try {
            // 如果未启用动态权重，返回基础权重
            if (!weightEnabled) {
                logger.debug("动态权重未启用，返回基础权重: {}", baseWeight);
                return BigDecimal.valueOf(baseWeight)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }
            if(calcDto.getKLines().get(calcDto.getKLines().size()-1).getTimeStr().equals("2025-01-07 00:00:00")){
                System.out.printf("Hee");
            }
            String symbol = calcDto.getSymbol();
            String interval = calcDto.getCandlestickIntervalEnum().getCode();
            String robotId = calcDto.getRobotId();

            if (robotId == null || robotId.trim().isEmpty()) {
                logger.warn("RobotId为空，无法计算动态权重，返回基础权重");
                return BigDecimal.valueOf(baseWeight)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            String cacheKey = String.format("%s_%s_%s", symbol, interval, robotId);

            // 1. 获取最近的历史信号
            List<TradeSignalSignal> recentSignals =
                    getRecentSignalsForWeight(calcDto, consolidationLookback);

            // 2. 构造当前信号对象（还未入库的最新信号）
            TradeSignalSignal currentSignal = constructCurrentSignal(calcDto);
            if (currentSignal != null) {
                recentSignals.add(0, currentSignal); // 添加到列表开头，因为它是最新信号
                logger.info("添加当前信号到分析列表开头: 时间={}, 价格={}, 信号={}",
                        currentSignal.getKlineTime(),
                        currentSignal.getClosePrice(),
                        currentSignal.getTrend());
            }

            // 详细记录历史信号
            if (!recentSignals.isEmpty()) {
                logger.info("获取到历史信号{}个，最新信号：时间={}, 价格={}, 信号={}",
                        recentSignals.size(),
                        recentSignals.get(0).getCreateTime(),
                        recentSignals.get(0).getClosePrice(),
                        recentSignals.get(0).getTrend());
            }

            if (recentSignals.size() < 2) {
                // 历史信号不足，返回基础权重
                logger.info("历史信号不足，返回基础权重: {}", baseWeight);
                return BigDecimal.valueOf(baseWeight)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            // 2. 将信号按时间升序排列（旧到新）
            List<TradeSignalSignal> sortedSignals =
                    sortSignalsByTime(recentSignals, true); // true表示升序（旧到新）

            // 3. 检查当前信号是否与上一个信号形成盈利交易，如果是则立即重置权重到基础权重的0.5
            boolean shouldResetWeight = checkCurrentSignalProfitable(sortedSignals, calcDto);
            if (shouldResetWeight) {
                ConsolidationState state = getOrCreateConsolidationState(calcDto);
                double resetWeight = baseWeight * 0.5;
                logger.info("当前信号与上一个信号形成盈利交易，立即重置权重到基础权重的0.5: {:.2f}", resetWeight);
                state.setLastWeight(resetWeight);
                state.setCounter(0);
                return BigDecimal.valueOf(resetWeight)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }

            // 4. 分析信号盈亏模式
            SignalAnalysisResult analysisResult = analyzeSignalProfitability(sortedSignals);

            // 详细记录分析结果
            logger.info("信号盈亏分析 - 总交易: {}, 盈利交易: {}, 最近盈利: {}, 连续亏损: {}, 胜率: {:.2f}%",
                    analysisResult.getTotalTrades(),
                    analysisResult.getProfitableTrades(),
                    analysisResult.isLastTradeProfitable() ? "是" : "否",
                    analysisResult.getConsecutiveLosses(),
                    analysisResult.getWinRate() * 100);

            // 5. 获取或初始化横盘状态
            ConsolidationState state = getOrCreateConsolidationState(calcDto);

            // 记录当前状态
            logger.info("当前状态 - counter: {}, lastWeight: {:.2f}, marketState: {}, cacheKey: {}",
                    state.getCounter(), state.getLastWeight(), state.getMarketState(), cacheKey);

            // 6. 基于盈亏分析更新横盘状态
            // updateConsolidationState(state, analysisResult);

            // 6. 如果有盈利空间或连续亏损过多，强制回归基础仓位
       /* if (shouldResetToBaseWeight(state, analysisResult)) {
            logger.warn("强制回归基础仓位 - counter: {}, 连续亏损: {}, 胜率: {:.2f}%",
                state.getCounter(), analysisResult.getConsecutiveLosses(), analysisResult.getWinRate() * 100);
            state.setLastWeight(baseWeight);
            state.setCounter(0);
            return BigDecimal.valueOf(baseWeight)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        }*/

            // 7. 如果最近一次交易有盈利空间，保持当前权重不变（不再重置），让权重随着成功而递增
            if (analysisResult.isLastTradeProfitable()) {
                logger.info("最近交易有盈利空间，保持当前权重: {:.2f}，继续递增", state.getLastWeight());
                // 不重置权重，让权重随着成功交易而自然递增
            }

            // 7. 检查是否需要重置权重
            logger.info("重置检查前权重: {:.2f}", state.getLastWeight());
            resetWeightIfNeeded(state, analysisResult);
            logger.info("重置检查后权重: {:.2f}", state.getLastWeight());

            // 8. 计算动态权重（修改后的激进算法）
            logger.info("开始计算动态权重，输入权重: {:.2f}", state.getLastWeight());
            double dynamicWeight = calculateAggressiveDynamicWeight(state, analysisResult, sortedSignals);
            logger.info("动态权重计算完成，最终权重: {:.2f}", dynamicWeight);

            // 9. 记录最终结果
            logger.info("最终权重计算 - symbol: {}, 最终权重: {:.2f}, 基础权重: {:.2f}, 增长倍数: {:.2f}",
                    symbol, dynamicWeight, baseWeight, dynamicWeight / baseWeight);

            return BigDecimal.valueOf(dynamicWeight)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();

        } catch (Exception e) {
            logger.error("计算动态权重失败: {}, 返回基础权重", e.getMessage());
            return BigDecimal.valueOf(baseWeight)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    /**
     * 信号分析结果类
     */
    @Data
    private static class SignalAnalysisResult {
        private int totalTrades = 0;                // 总交易次数
        private int profitableTrades = 0;           // 盈利交易次数
        private int consecutiveLosses = 0;          // 连续亏损次数
        private double winRate = 0.0;               // 胜率
        private double avgProfit = 0.0;             // 平均盈利
        private double profitRatio = 0.0;           // 盈利比率
        private List<Double> profitPercentages = new ArrayList<>(); // 各次交易盈利百分比
        private List<Boolean> profitableFlags = new ArrayList<>(); // 每次交易是否盈利标志
        private boolean lastTradeProfitable = false; // 最近一次交易是否盈利
    }

    /**
     * 横盘状态内部类
     */
    /**
     * 激进的基础权重计算（显著增加增长幅度）
     */
    private double calculateConservativeBaseWeight(int counter, boolean lastTradeProfitable) {
        // 如果最近盈利，适度回归但保留部分增长
        if (lastTradeProfitable) {
            // 盈利后不完全回归，保留部分增量
            double reduction = Math.min(0.3, counter * 0.05); // 最多减少30%
            return Math.max(baseWeight, baseWeight * (1.0 + counter * 0.05 - reduction));
        }

        // 亏损后的激进增长
        if (counter <= 0) {
            return baseWeight;
        } else if (counter <= 3) {
            // 连续亏损1-3次：权重增加20%-60%
            return baseWeight * (1.0 + counter * 0.2);
        } else if (counter <= 6) {
            // 连续亏损4-6次：权重增加80%-140%
            return baseWeight * (1.6 + (counter - 3) * 0.2);
        } else if (counter <= 10) {
            // 连续亏损7-10次：权重增加160%-240%
            return baseWeight * (2.2 + (counter - 6) * 0.2);
        } else {
            // 连续亏损10次以上：权重增加260%+
            return Math.min(maxWeight, baseWeight * (3.0 + (counter - 10) * 0.15));
        }
    }

    /**
     * 激进的性能因子计算（扩大影响范围）
     */
    private double calculateSimplePerformanceFactor(SignalAnalysisResult analysisResult) {
        double performance = 1.0;

        boolean lastTradeProfitable = analysisResult.isLastTradeProfitable();
        int consecutiveLosses = analysisResult.getConsecutiveLosses();
        double winRate = analysisResult.getWinRate();
        int totalTrades = analysisResult.getTotalTrades();

        // 1. 最近一次交易奖励/惩罚
        if (lastTradeProfitable) {
            performance *= 1.15; // 盈利：增加15%
        } else {
            performance *= 0.9;  // 亏损：减少10%
        }

        // 2. 连败惩罚
        if (consecutiveLosses >= 5) {
            performance *= 0.7; // 严重连败：减少30%
        } else if (consecutiveLosses >= 3) {
            performance *= 0.8; // 中度连败：减少20%
        } else if (consecutiveLosses >= 1) {
            performance *= 0.9; // 轻度连败：减少10%
        }

        // 3. 胜率影响
        if (totalTrades >= 5) {
            if (winRate >= 0.7) {
                performance *= 1.3; // 高胜率：增加30%
            } else if (winRate >= 0.5) {
                performance *= 1.1; // 中等胜率：增加10%
            } else if (winRate < 0.3) {
                performance *= 0.8; // 低胜率：减少20%
            }
        }

        return Math.max(0.6, Math.min(1.5, performance));
    }

    /**
     * 激进的增量计算（根据连败次数调整）
     */
    private double getSimpleIncrementAmount(boolean lastTradeProfitable) {
        if (lastTradeProfitable) {
            return 0.05; // 盈利后：增量5%
        } else {
            return 0.1; // 亏损后：增量10%
        }
    }

    /**
     * 激进的增量计算（根据连败次数调整）
     */
    private double getAggressiveIncrement(int consecutiveLosses) {
        if (consecutiveLosses <= 0) {
            return 0.05; // 无连败：5%增量
        } else if (consecutiveLosses <= 2) {
            return 0.1;  // 轻度连败：10%增量
        } else if (consecutiveLosses <= 5) {
            return 0.15; // 中度连败：15%增量
        } else {
            return 0.2;  // 重度连败：20%增量
        }
    }

    /**
     * 固定的递增计算（确保权重递增，最大3.0）
     * 权重越小增量越大，越接近3.0增量越小
     */
    private double getFixedIncrement(double currentWeight, int counter) {
        // 使用允许最大权重的百分比来计算基础增量
        double weightRatio = currentWeight / maxWeight;
        double baseIncrement;

        if (weightRatio >= 0.9) {
            baseIncrement = 0.8; // 接近上限(90%)：小幅递增
        } else if (weightRatio >= 0.8) {
            baseIncrement = 0.6; // 较大权重(80%)：中等递增
        } else if (weightRatio >= 0.7) {
            baseIncrement = 0.4; // 中等权重(70%)：较大递增
        } else if (weightRatio >= 0.5) {
            baseIncrement = 0.2; // 较小权重(50%)：较大递增
        } else {
            baseIncrement = 0.2;  // 小权重：最大递增
        }

        // 根据counter区间调整增量倍数
        double multiplier;
        if (counter <= 3) {
            multiplier = 1.0; // 0-5: 基础增量
        } else if (counter <= 10) {
            multiplier = 1.5; // 5-15: 1.5倍增量
        } else if (counter <= 20) {
            multiplier = 2.0; // 15-20: 2倍增量
        } else {
            multiplier = 2.5; // 20以上: 2.5倍增量
        }

        double finalIncrement = baseIncrement * multiplier;

        logger.debug("增量计算 - 权重占比: {:.1f}%, 基础增量: {:.3f}, counter: {}, 倍数: {:.1f}, 最终增量: {:.3f}",
                weightRatio * 100, baseIncrement, counter, multiplier, finalIncrement);

        return finalIncrement;
    }

    /**
     * 检查当前信号是否与上一个信号形成盈利交易
     * 如果形成盈利交易，立即重置权重
     */
    private boolean checkCurrentSignalProfitable(List<TradeSignalSignal> sortedSignals, IndicatorCalcDto calcDto) {
        if (sortedSignals.size() < 2) {
            return false;
        }

        try {
            // 获取最后两个信号（升序排列，最后一个是最新的）
            TradeSignalSignal previousSignal = sortedSignals.get(sortedSignals.size() - 2);
            TradeSignalSignal currentSignal = sortedSignals.get(sortedSignals.size() - 1);

            String previousSignalType = extractSignalFromTradeSignalSignal(previousSignal);
            String currentSignalType = extractSignalFromTradeSignalSignal(currentSignal);

            // 如果信号类型相反，计算盈亏
            if (previousSignalType != null && currentSignalType != null &&
                    !previousSignalType.equals(currentSignalType)) {

                BigDecimal previousPrice = previousSignal.getClosePrice();
                BigDecimal currentPrice = currentSignal.getClosePrice();

                if (previousPrice != null && currentPrice != null) {
                    double profitPct = calculateProfitPercentageForSignal(
                            previousSignalType, previousPrice, currentPrice);
                    // 只有当盈利百分比为正（即市场向有利方向移动）时才认为是盈利
                    boolean hasProfitPotential = profitPct >= profitTarget;

                    logger.info("当前信号盈亏检查: 上一个信号={}({}), 当前信号={}({}), 价格: {} -> {}, 变化={:.2f}%, 阈值={:.2f}%, 有盈利空间={}",
                            previousSignalType, previousSignal.getKlineTime(),
                            currentSignalType, currentSignal.getKlineTime(),
                            previousPrice, currentPrice,
                            profitPct * 100, profitTarget * 100, hasProfitPotential);

                    return hasProfitPotential;
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("检查当前信号盈利失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构造当前信号对象（还未入库的最新信号）
     * 用于权重计算时包含当前正在处理的信号
     */
    private TradeSignalSignal constructCurrentSignal(IndicatorCalcDto calcDto) {
        try {
            // 获取当前K线数据
            Candlestick currentKline = calcDto.getCurrentCandlestick();
            if (currentKline == null) {
                logger.warn("当前K线数据为空，无法构造当前信号");
                return null;
            }

            // 推断当前信号类型
            String signalType = null;
            if (calcDto.getSignalType() != null) {
                // 根据signalType推断信号类型
                if (calcDto.getSignalType().name().startsWith("LONG")) {
                    signalType = "LONG"; // 多
                } else if (calcDto.getSignalType().name().startsWith("SHORT")) {
                    signalType = "SHORT"; // 空
                }
            }

            // 如果无法从signalType推断，尝试从openSide推断
            if (signalType == null && calcDto.getOpenSide() != null) {
                if ("buy".equals(calcDto.getOpenSide()) || "long".equals(calcDto.getOpenSide())) {
                    signalType = "LONG";
                } else if ("sell".equals(calcDto.getOpenSide()) || "short".equals(calcDto.getOpenSide())) {
                    signalType = "SHORT";
                }
            }

            if (signalType == null) {
                logger.warn("无法推断当前信号类型，signalType={}, openSide={}",
                        calcDto.getSignalType(), calcDto.getOpenSide());
                return null;
            }

            // 构造信号对象
            TradeSignalSignal signal =
                    new TradeSignalSignal();

            signal.setKlineTime(currentKline.getTimeStr());
            signal.setClosePrice(currentKline.getOpenPrice());
            signal.setSymbol(calcDto.getSymbol());
            signal.setIndicatorType(calcDto.getRobotId()); // indicatorType字段存储robotId
            signal.setStrategyType("MACD");

            // 设置trend字段，格式：{"signal":"LONG","weight":"1.0","source":"SYSTEM"}
            String trendJson = String.format("{\"signal\":\"%s\",\"weight\":\"1.0\",\"source\":\"CURRENT\"}", signalType);
            signal.setTrend(trendJson);

            // 设置其他必要字段
            signal.setCreateTime(new java.util.Date());
            signal.setDataFrom("OKX");

            logger.debug("构造当前信号成功: 类型={}, 时间={}, 价格={}",
                    signalType, signal.getKlineTime(), signal.getClosePrice());

            return signal;

        } catch (Exception e) {
            logger.error("构造当前信号失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 计算激进的动态权重
     * 修改：显著增加权重变化幅度，使权重能在1.0-3.0范围内有效变化
     */
    private double calculateAggressiveDynamicWeight(ConsolidationState state,
                                                    SignalAnalysisResult analysisResult,
                                                    List<TradeSignalSignal> signals) {

        double lastWeight = state.getLastWeight();

        // 简化的递增逻辑：权重必须比上次大，最大到2.0
        // 盈利重置已经在checkCurrentSignalProfitable中处理，这里只处理递增

        logger.info("calculateAggressiveDynamicWeight - 输入权重: {:.2f}, counter: {}", lastWeight, state.getCounter());

        double increment = getFixedIncrement(lastWeight, state.getCounter());
        logger.info("计算增量: {:.2f}", increment);

        double newWeight = Math.min(2.0, lastWeight + increment);
        logger.info("应用上限限制后: {:.2f}", newWeight);

        // 确保权重递增（最小增加0.01）
        newWeight = Math.max(newWeight, lastWeight + 0.01);
        logger.info("应用最小递增后: {:.2f}", newWeight);

        // 更新状态
        state.setLastWeight(newWeight);

        // 记录计算过程
        logger.info("递增权重计算完成 - 上次权重: {:.2f}, 增量: {:.2f}, 新权重: {:.2f}",
                lastWeight, increment, newWeight);

        return newWeight;
    }

    /**
     * 激进的基重计算
     * 修改：显著增加权重增长幅度
     */
    private double calculateAggressiveBaseWeight(int counter, SignalAnalysisResult analysisResult) {
        boolean lastTradeProfitable = analysisResult.isLastTradeProfitable();

        // 盈利后：回归基础权重，但保留部分增长
        if (lastTradeProfitable) {
            // 盈利后不完全回归，保留部分增量
            double reduction = Math.min(0.3, counter * 0.05); // 最多减少30%
            return Math.max(baseWeight, baseWeight * (1.0 + counter * 0.05 - reduction));
        }

        // 亏损后的激进增长
        if (counter <= 0) {
            return baseWeight;
        } else if (counter <= 3) {
            // 连续亏损1-3次：权重增加20%-60%
            return baseWeight * (1.0 + counter * 0.2);
        } else if (counter <= 6) {
            // 连续亏损4-6次：权重增加80%-140%
            return baseWeight * (1.6 + (counter - 3) * 0.2);
        } else if (counter <= 10) {
            // 连续亏损7-10次：权重增加160%-240%
            return baseWeight * (2.2 + (counter - 6) * 0.2);
        } else {
            // 连续亏损10次以上：权重增加260%+
            return Math.min(maxWeight, baseWeight * (3.0 + (counter - 10) * 0.15));
        }
    }

    /**
     * 激进的性能因子计算
     * 修改：扩大性能因子的影响范围
     */
    private double calculateAggressivePerformanceFactor(SignalAnalysisResult analysisResult) {
        double performance = 1.0;

        boolean lastTradeProfitable = analysisResult.isLastTradeProfitable();
        int consecutiveLosses = analysisResult.getConsecutiveLosses();
        double winRate = analysisResult.getWinRate();
        int totalTrades = analysisResult.getTotalTrades();

        // 1. 最近一次交易奖励/惩罚
        if (lastTradeProfitable) {
            performance *= 1.15; // 盈利：增加15%
        } else {
            performance *= 0.9;  // 亏损：减少10%
        }

        // 2. 连败惩罚
        if (consecutiveLosses >= 5) {
            performance *= 0.7; // 严重连败：减少30%
        } else if (consecutiveLosses >= 3) {
            performance *= 0.8; // 中度连败：减少20%
        } else if (consecutiveLosses >= 1) {
            performance *= 0.9; // 轻度连败：减少10%
        }

        // 3. 胜率影响
        if (totalTrades >= 5) {
            if (winRate >= 0.7) {
                performance *= 1.3; // 高胜率：增加30%
            } else if (winRate >= 0.5) {
                performance *= 1.1; // 中等胜率：增加10%
            } else if (winRate < 0.3) {
                performance *= 0.8; // 低胜率：减少20%
            }
        }

        return Math.max(0.6, Math.min(1.5, performance));
    }

    /**
     * 计算最大安全递增幅度
     */
    private double calculateMaxSafeIncrement(double lastWeight, SignalAnalysisResult analysisResult) {
        double maxIncrement;

        if (analysisResult.isLastTradeProfitable()) {
            // 盈利后：较小的递增（0.5%-2%）
            maxIncrement = lastWeight * 0.02; // 2%
        } else {
            // 亏损后：更小的递增（0.3%-1%）
            maxIncrement = lastWeight * 0.01; // 1%
        }

        // 确保最小递增不小于0.01
        return Math.max(0.01, maxIncrement);
    }

    @Data
    private static class ConsolidationState {
        private int counter = 0;                     // 横盘计数器
        private double alternationRate = 0.0;        // 信号交替率
        private String marketState = "UNKNOWN";      // 市场状态
        private double lastWeight = 0.0;            // 上次使用的权重
        private LocalDateTime lastUpdateTime = LocalDateTime.now(); // 最后更新时间
    }
}
