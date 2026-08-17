package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logarithmic Regression Channel-Trend 策略信号生成服务
 * 严格对应 TradingView Pine Script: "Logarithmic Regression Channel-Trend [BigBeluga]"
 * 修正版本 - 支持实盘未收盘K线处理，信号基于已收盘K线生成
 */
@Slf4j
@Service
public class LogRegChannelTrendSignService extends DefaultSignService {

    @Value("${strategy.logreg-channel.length:100}")
    private int defaultLength;

    @Value("${strategy.logreg-channel.channel-width:1.5}")
    private double defaultChannelWidth;

    @Value("${strategy.logreg-channel.channel-length:100}")
    private int defaultChannelLength;

    @Value("${strategy.logreg-channel.diff-period:3}")
    private int diffPeriod; // 对应Pine Script中的end[3]

    // 存储各交易对的状态
    private final ConcurrentHashMap<String, LogRegState> stateMap = new ConcurrentHashMap<>();

    // 定义策略类型常量
    private static final String STRATEGY_TYPE = "LOGREG_CHANNEL_TREND";

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();
        BarSeries series = IndicatorWrapHelper.buildSeries(kLines);

        // 从规则中获取配置
        int currentLength = defaultLength;
        double currentChannelWidth = defaultChannelWidth;
        int currentChannelLength = defaultChannelLength;

        // 确保有足够的数据计算end[3]
        int minRequired = currentLength + diffPeriod;
        if (kLines.size() < minRequired) {
            log.debug("数据不足，返回空结果。当前K线数量: {}, 需要: {}",
                    kLines.size(), minRequired);
            return new BuyAndSellWeightDto();
        }

        // 确定用于信号计算的K线索引（必须基于已收盘K线）
        int currentIndex = series.getEndIndex()-1;

        // 获取用于计算信号的实际K线（用于日志）
        Candlestick signalCandle = kLines.get(currentIndex);

        LogRegSignal signal = analyzeLogRegSignal(series, symbol,
                currentLength, currentChannelWidth, currentChannelLength, currentIndex);

        BuyAndSellWeightDto result = new BuyAndSellWeightDto();

        if (signal.isBuySignal()) {
            log.info("交易对: {}, K线时间: {}, LogReg Channel-Trend 买入信号确认, 斜率: {}, 差值: {}, 回归值: {}",
                    symbol, DateUtil.longConvertDateTime(signalCandle.getId()),
                    String.format("%.6f", signal.getSlope()),
                    String.format("%.4f", signal.getDiff()),
                    String.format("%.4f", signal.getEndValue()));
            result.setSignalType(SignalType.LONG);
            saveLastSignalToRedis(symbol, "BUY", signal);
        } else if (signal.isSellSignal()) {
            log.info("交易对: {}, K线时间: {}, LogReg Channel-Trend 卖出信号确认, 斜率: {}, 差值: {}, 回归值: {}",
                    symbol, DateUtil.longConvertDateTime(signalCandle.getId()),
                    String.format("%.6f", signal.getSlope()),
                    String.format("%.4f", signal.getDiff()),
                    String.format("%.4f", signal.getEndValue()));
            result.setSignalType(SignalType.SHORT);
            saveLastSignalToRedis(symbol, "SELL", signal);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("交易对: {}, K线时间: {}, LogReg Channel-Trend 无信号, 差值: {}, 趋势: {}",
                        symbol, DateUtil.longConvertDateTime(signalCandle.getId()),
                        String.format("%.4f", signal.getDiff()), signal.getTrendDirection());
            }
        }

        if (null != result.getSignalType()) {
            Long signalId = saveSign(calcDto, result.getSignalType());
            result.setSignalId(signalId);
        }
        return result;
    }

    /**
     * 判断K线是否已收盘（需根据实际业务实现）
     * 此处假设Candlestick有isClosed()方法，若无则需自行判断（例如比较时间戳与当前时间）
     */
    private boolean isCandleClosed(Candlestick candle) {
        // 如果Candlestick有isClosed方法，直接调用
        // 否则可以根据业务逻辑判断，例如：candle.getId() < 当前周期开始时间戳
        // 这里提供一个默认实现（假设所有传入的K线都是已收盘的）
        return true; // 请根据实际类实现替换
    }

    /**
     * 获取当前趋势信息（不生成交易信号）
     * 这个方法可以用于面板展示、状态监控等，允许使用未收盘K线展示实时趋势
     */
    public LogRegTrendInfo getCurrentTrendInfo(List<Candlestick> kLines, String symbol) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            int currentLength = defaultLength;

            // 检查数据是否足够
            if (kLines.size() < currentLength + diffPeriod) {
                log.warn("交易对: {}, 数据不足获取趋势信息，当前: {}, 需要: {}",
                        symbol, kLines.size(), currentLength + diffPeriod);
                return LogRegTrendInfo.empty();
            }

            int currentIndex = series.getEndIndex(); // 趋势信息可以使用最新K线（包括未收盘）

            // 获取状态对象
            LogRegState state = stateMap.get(symbol);
            if (state == null) {
                // 如果没有状态，初始化一个
                state = new LogRegState();
                stateMap.put(symbol, state);
            }

            // 计算对数回归
            LogRegressionResult regressionResult = calculateLogRegression(series, currentLength, currentIndex);
            double currentEnd = regressionResult.getEnd();

            // 保存当前回归值到历史
            state.addRegressionValue(currentEnd);

            // 计算标准差和通道
            double deviation = calculateStandardDeviation(series, currentLength, currentIndex);
            double upperBand = currentEnd + deviation * defaultChannelWidth;
            double lowerBand = currentEnd - deviation * defaultChannelWidth;

            // 计算diff = end - end[3]
            double diff = calculateDiff(state, currentEnd);
            double end3Value = state.getRegressionValueAt(diffPeriod);

            // 判断各种趋势
            boolean longTermTrendUp = regressionResult.getEnd() > regressionResult.getStart();  // 长期趋势
            boolean shortTermTrendUp = diff > 0;  // 短期趋势（基于diff）
            boolean slopePositive = regressionResult.getSlope() > 0;  // 斜率趋势

            // 计算趋势强度
            double trendStrength = calculateTrendStrength(regressionResult, diff, deviation);

            // 获取当前价格
            double currentPrice = series.getBar(currentIndex).getClosePrice().doubleValue();

            // 判断价格相对于通道的位置
            String channelPosition = getChannelPosition(currentPrice, currentEnd, upperBand, lowerBand);

            // 生成趋势信息
            LogRegTrendInfo trendInfo = new LogRegTrendInfo();
            trendInfo.setSymbol(symbol);
            trendInfo.setTimestamp(System.currentTimeMillis());
            trendInfo.setCurrentPrice(currentPrice);
            trendInfo.setRegValue(currentEnd);
            trendInfo.setRegStart(regressionResult.getStart());
            trendInfo.setSlope(regressionResult.getSlope());
            trendInfo.setDiff(diff);
            trendInfo.setEnd3Value(end3Value);
            trendInfo.setDeviation(deviation);
            trendInfo.setUpperBand(upperBand);
            trendInfo.setLowerBand(lowerBand);
            trendInfo.setLongTermTrend(longTermTrendUp ? "BULLISH" : "BEARISH");
            trendInfo.setShortTermTrend(shortTermTrendUp ? "BULLISH" : "BEARISH");
            trendInfo.setSlopeTrend(slopePositive ? "RISING" : "FALLING");
            trendInfo.setTrendStrength(trendStrength);
            trendInfo.setChannelPosition(channelPosition);
            trendInfo.setIsInChannel(Math.abs(currentPrice - currentEnd) <= deviation * defaultChannelWidth);
            trendInfo.setChannelWidth(defaultChannelWidth);

            // 更新前一个diff值到状态中（用于下一次信号判断）
            state.setPrevDiff(diff);

            log.debug("交易对: {}, 趋势信息获取完成 - 长期趋势: {}, 短期趋势: {}, 斜率: {}, 强度: {}",
                    symbol, trendInfo.getLongTermTrend(), trendInfo.getShortTermTrend(),
                    String.format("%.6f", trendInfo.getSlope()),
                    String.format("%.2f", trendInfo.getTrendStrength()));

            return trendInfo;

        } catch (Exception e) {
            log.error("交易对: {}, 获取趋势信息失败: {}", symbol, e.getMessage(), e);
            return LogRegTrendInfo.empty(symbol);
        }
    }

    /**
     * 计算趋势强度
     */
    private double calculateTrendStrength(LogRegressionResult regressionResult, double diff, double deviation) {
        // 基于多个因素计算趋势强度
        double strength = 0.0;

        // 1. 斜率强度 (30%)
        double slopeStrength = Math.abs(regressionResult.getSlope() * 1000);
        slopeStrength = Math.min(slopeStrength, 1.0);

        // 2. 差值强度 (40%)
        double diffStrength = Math.abs(diff) / (deviation > 0 ? deviation * 2 : 1.0);
        diffStrength = Math.min(diffStrength, 1.0);

        // 3. 长期趋势一致性 (30%)
        boolean longTrendUp = regressionResult.getEnd() > regressionResult.getStart();
        boolean shortTrendUp = diff > 0;
        double consistencyStrength = (longTrendUp == shortTrendUp) ? 1.0 : 0.3;

        // 综合强度
        strength = slopeStrength * 0.3 + diffStrength * 0.4 + consistencyStrength * 0.3;

        return Math.min(Math.max(strength, 0.0), 1.0);
    }

    /**
     * 获取价格在通道中的位置
     */
    private String getChannelPosition(double price, double regValue, double upperBand, double lowerBand) {
        double halfChannel = (upperBand - lowerBand) / 2;
        double midValue = (upperBand + lowerBand) / 2;

        if (price > upperBand) {
            return "ABOVE_UPPER";
        } else if (price > (midValue + halfChannel * 0.5)) {
            return "UPPER_HALF";
        } else if (price > (midValue - halfChannel * 0.5)) {
            return "MIDDLE";
        } else if (price > lowerBand) {
            return "LOWER_HALF";
        } else {
            return "BELOW_LOWER";
        }
    }

    /**
     * 对数回归通道趋势信号分析 - 支持指定当前索引
     * @param series K线序列
     * @param symbol 交易对
     * @param length 回归长度
     * @param channelWidth 通道宽度（未使用，仅保留签名一致性）
     * @param channelLength 通道长度（未使用）
     * @param currentIndex 当前计算的K线索引（必须是已收盘K线）
     */
    private LogRegSignal analyzeLogRegSignal(BarSeries series, String symbol,
                                             int length, double channelWidth, int channelLength,
                                             int currentIndex) {
        try {
            // 获取状态对象
            LogRegState state = stateMap.computeIfAbsent(symbol, k -> new LogRegState());

            // 计算对数回归
            LogRegressionResult regressionResult = calculateLogRegression(series, length, currentIndex);
            double currentEnd = regressionResult.getEnd();

            // 保存当前回归值到历史
            state.addRegressionValue(currentEnd);

            // 计算diff = end - end[3]
            double diff = calculateDiff(state, currentEnd);

            // 获取前一个diff值用于交叉判断
            double prevDiff = state.getPrevDiff();

            // 判断交叉信号
            boolean upSignal = isCrossover(prevDiff, diff, 0);
            boolean dnSignal = isCrossunder(prevDiff, diff, 0);

            // 更新前一个diff值
            state.setPrevDiff(diff);

            // 计算标准差（用于通道计算，但信号不直接依赖）
            double deviation = calculateStandardDeviation(series, length, currentIndex);

            // 判断趋势方向
            boolean trendUp = regressionResult.getEnd() > regressionResult.getStart();

            // 记录详细信息用于调试
            if (log.isDebugEnabled()) {
                double end3Value = state.getRegressionValueAt(3); // 获取3周期前的值
                log.debug("交易对: {}, 索引: {}, 回归值: {}, end[3]: {}, diff: {}, prevDiff: {}, 斜率: {}, 趋势: {}, upSignal: {}, dnSignal: {}",
                        symbol, currentIndex,
                        String.format("%.4f", currentEnd),
                        String.format("%.4f", end3Value),
                        String.format("%.4f", diff),
                        String.format("%.4f", prevDiff),
                        String.format("%.6f", regressionResult.getSlope()),
                        trendUp ? "UP" : "DN", upSignal, dnSignal);
            }

            if (upSignal) {
                // 买入信号: diff上穿0
                return new LogRegSignal("BUY", currentEnd, regressionResult.getSlope(), diff, trendUp);
            } else if (dnSignal) {
                // 卖出信号: diff下穿0
                return new LogRegSignal("SELL", currentEnd, regressionResult.getSlope(), diff, trendUp);
            } else {
                String trendDir = trendUp ? "UP" : "DN";
                return new LogRegSignal("HOLD", currentEnd, regressionResult.getSlope(), diff, trendDir);
            }

        } catch (Exception e) {
            log.error("交易对: {}, LogReg Channel-Trend 信号分析失败: {}", symbol, e.getMessage(), e);
            return new LogRegSignal("HOLD", 0.0, 0.0, 0.0, "ERROR");
        }
    }

    /**
     * 计算差值: diff = end - end[3]
     */
    private double calculateDiff(LogRegState state, double currentEnd) {
        // 获取3周期前的回归值
        double end3Value = state.getRegressionValueAt(diffPeriod);

        // 如果没有足够的历史数据，返回0
        if (Double.isNaN(end3Value)) {
            return 0.0;
        }

        return currentEnd - end3Value;
    }

    /**
     * 判断是否上穿
     */
    private boolean isCrossover(double prevValue, double currentValue, double level) {
        return prevValue <= level && currentValue > level;
    }

    /**
     * 判断是否下穿
     */
    private boolean isCrossunder(double prevValue, double currentValue, double level) {
        return prevValue >= level && currentValue < level;
    }

    /**
     * 计算对数回归 - 严格按Pine Script实现
     */
    private LogRegressionResult calculateLogRegression(BarSeries series, int length, int currentIndex) {
        // 确保有足够的数据
        int startIndex = currentIndex - length + 1;
        if (startIndex < 0) {
            throw new IllegalArgumentException("数据不足计算对数回归: 需要" + length + "根K线，实际只有" + (currentIndex + 1));
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXSqr = 0.0;
        double sumXY = 0.0;

        // 按照Pine Script的循环逻辑: for i = 0 to length - 1
        // 这意味着从最近到最远，i=0对应最新数据
        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            Bar bar = series.getBar(barIndex);
            double price = bar.getClosePrice().doubleValue();

            if (price <= 0) {
                throw new IllegalArgumentException("价格必须为正数才能计算对数: " + price);
            }

            // 计算对数
            double logVal = Math.log(price);

            // Pine Script中: per = i + 1.0
            double per = i + 1.0;

            sumX += per;
            sumY += logVal;
            sumXSqr += per * per;
            sumXY += logVal * per;
        }

        // 计算斜率: slope = (length * sumXY - sumX * sumY) / (length * sumXSqr - sumX * sumX)
        double denominator = length * sumXSqr - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            return new LogRegressionResult(0.0, 0.0, 0.0, 0.0);
        }

        double slope = (length * sumXY - sumX * sumY) / denominator;

        // 计算截距: intercept = average - slope * sumX / length + slope
        // 其中 average = sumY / length
        double average = sumY / length;
        double intercept = average - slope * sumX / length + slope;

        // 计算回归线的起点和终点
        // start = math.exp(intercept + slope * length) 对应最旧的bar (i = length - 1)
        // end = math.exp(intercept) 对应最新的bar (i = 0)
        double start = Math.exp(intercept + slope * length);
        double end = Math.exp(intercept);

        return new LogRegressionResult(slope, intercept, start, end);
    }

    /**
     * 计算标准差
     */
    private double calculateStandardDeviation(BarSeries series, int length, int currentIndex) {
        if (currentIndex < length - 1) {
            return 0.0;
        }

        // 计算平均值
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            sum += series.getBar(barIndex).getClosePrice().doubleValue();
        }
        double mean = sum / length;

        // 计算方差
        double variance = 0.0;
        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            double price = series.getBar(barIndex).getClosePrice().doubleValue();
            variance += Math.pow(price - mean, 2);
        }
        variance /= length;

        // 返回标准差
        return Math.sqrt(variance);
    }

    /**
     * 状态管理类
     */
    private static class LogRegState {
        private final LinkedList<Double> regressionHistory = new LinkedList<>();
        private double prevDiff = 0.0;

        public void addRegressionValue(double value) {
            regressionHistory.addFirst(value); // 最新值在头部

            // 保持足够的长度用于计算end[3]
            if (regressionHistory.size() > 10) {
                regressionHistory.removeLast();
            }
        }

        public double getRegressionValueAt(int periodsAgo) {
            if (regressionHistory.size() <= periodsAgo) {
                return Double.NaN;
            }

            // regressionHistory[0]是当前值，所以要取索引为periodsAgo的值
            return regressionHistory.get(periodsAgo);
        }

        public double getPrevDiff() {
            return prevDiff;
        }

        public void setPrevDiff(double prevDiff) {
            this.prevDiff = prevDiff;
        }
    }

    /**
     * 对数回归结果类
     */
    private static class LogRegressionResult {
        private final double slope;
        private final double intercept;
        private final double start;
        private final double end;

        public LogRegressionResult(double slope, double intercept, double start, double end) {
            this.slope = slope;
            this.intercept = intercept;
            this.start = start;
            this.end = end;
        }

        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public double getStart() { return start; }
        public double getEnd() { return end; }
    }

    /**
     * 信号包装类
     */
    private static class LogRegSignal {
        private final String signalType;
        private final double endValue;
        private final double slope;
        private final double diff;
        private final Object trendInfo;

        public LogRegSignal(String signalType, double endValue, double slope, double diff, Object trendInfo) {
            this.signalType = signalType;
            this.endValue = endValue;
            this.slope = slope;
            this.diff = diff;
            this.trendInfo = trendInfo;
        }

        public boolean isBuySignal() { return "BUY".equals(signalType); }
        public boolean isSellSignal() { return "SELL".equals(signalType); }
        public boolean isHold() { return "HOLD".equals(signalType); }
        public double getEndValue() { return endValue; }
        public double getSlope() { return slope; }
        public double getDiff() { return diff; }
        public String getTrendDirection() {
            if (trendInfo instanceof Boolean) {
                return (Boolean)trendInfo ? "UP" : "DN";
            } else if (trendInfo instanceof String) {
                return (String)trendInfo;
            }
            return "UNKNOWN";
        }
    }

    /**
     * 趋势信息类 - 用于返回详细的趋势状态
     */
    public static class LogRegTrendInfo {
        private String symbol;
        private long timestamp;
        private double currentPrice;
        private double regValue;          // 当前回归值(end)
        private double regStart;          // 回归起点(start)
        private double slope;             // 回归斜率
        private double diff;              // diff = end - end[3]
        private double end3Value;         // end[3]的值
        private double deviation;         // 标准差
        private double upperBand;         // 通道上轨
        private double lowerBand;         // 通道下轨
        private String longTermTrend;     // 长期趋势: BULLISH/BEARISH
        private String shortTermTrend;    // 短期趋势: BULLISH/BEARISH
        private String slopeTrend;        // 斜率趋势: RISING/FALLING
        private double trendStrength;     // 趋势强度 0-1
        private String channelPosition;   // 价格在通道中的位置
        private boolean isInChannel;      // 是否在通道内
        private double channelWidth;      // 通道宽度乘数

        // 构造函数
        public LogRegTrendInfo() {}

        public static LogRegTrendInfo empty() {
            return new LogRegTrendInfo();
        }

        public static LogRegTrendInfo empty(String symbol) {
            LogRegTrendInfo info = new LogRegTrendInfo();
            info.setSymbol(symbol);
            info.setTimestamp(System.currentTimeMillis());
            return info;
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public double getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

        public double getRegValue() { return regValue; }
        public void setRegValue(double regValue) { this.regValue = regValue; }

        public double getRegStart() { return regStart; }
        public void setRegStart(double regStart) { this.regStart = regStart; }

        public double getSlope() { return slope; }
        public void setSlope(double slope) { this.slope = slope; }

        public double getDiff() { return diff; }
        public void setDiff(double diff) { this.diff = diff; }

        public double getEnd3Value() { return end3Value; }
        public void setEnd3Value(double end3Value) { this.end3Value = end3Value; }

        public double getDeviation() { return deviation; }
        public void setDeviation(double deviation) { this.deviation = deviation; }

        public double getUpperBand() { return upperBand; }
        public void setUpperBand(double upperBand) { this.upperBand = upperBand; }

        public double getLowerBand() { return lowerBand; }
        public void setLowerBand(double lowerBand) { this.lowerBand = lowerBand; }

        public String getLongTermTrend() { return longTermTrend; }
        public void setLongTermTrend(String longTermTrend) { this.longTermTrend = longTermTrend; }

        public String getShortTermTrend() { return shortTermTrend; }
        public void setShortTermTrend(String shortTermTrend) { this.shortTermTrend = shortTermTrend; }

        public String getSlopeTrend() { return slopeTrend; }
        public void setSlopeTrend(String slopeTrend) { this.slopeTrend = slopeTrend; }

        public double getTrendStrength() { return trendStrength; }
        public void setTrendStrength(double trendStrength) { this.trendStrength = trendStrength; }

        public String getChannelPosition() { return channelPosition; }
        public void setChannelPosition(String channelPosition) { this.channelPosition = channelPosition; }

        public boolean isInChannel() { return isInChannel; }
        public void setIsInChannel(boolean isInChannel) { this.isInChannel = isInChannel; }

        public double getChannelWidth() { return channelWidth; }
        public void setChannelWidth(double channelWidth) { this.channelWidth = channelWidth; }

        @Override
        public String toString() {
            return String.format(
                    "LogRegTrendInfo{symbol='%s', timestamp=%d, price=%.4f, regValue=%.4f, slope=%.6f, diff=%.4f, " +
                            "longTrend=%s, shortTrend=%s, strength=%.2f, inChannel=%s}",
                    symbol, timestamp, currentPrice, regValue, slope, diff,
                    longTermTrend, shortTermTrend, trendStrength, isInChannel
            );
        }

        /**
         * 转换为JSON格式，便于API返回
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("timestamp", timestamp);
            map.put("currentPrice", currentPrice);
            map.put("regValue", regValue);
            map.put("regStart", regStart);
            map.put("slope", slope);
            map.put("diff", diff);
            map.put("end3Value", end3Value);
            map.put("deviation", deviation);
            map.put("upperBand", upperBand);
            map.put("lowerBand", lowerBand);
            map.put("longTermTrend", longTermTrend);
            map.put("shortTermTrend", shortTermTrend);
            map.put("slopeTrend", slopeTrend);
            map.put("trendStrength", trendStrength);
            map.put("channelPosition", channelPosition);
            map.put("isInChannel", isInChannel);
            map.put("channelWidth", channelWidth);
            map.put("channelRange", upperBand - lowerBand);
            return map;
        }
    }

    /**
     * 计算权重
     */
    @Override
    public Double getWeight(IndicatorCalcDto calcDto) {
        double finalWeight = 1;
        return BigDecimal.valueOf(finalWeight)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 计算信号强度 - 基于回归趋势的一致性
     */
    private double calculateSignalStrength(List<Candlestick> kLines, String symbol,
                                           String signalType, int length) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            int currentIndex = series.getEndIndex();

            // 计算对数回归
            LogRegressionResult regressionResult = calculateLogRegression(series, length, currentIndex);

            // 获取状态和diff值
            LogRegState state = stateMap.get(symbol);
            if (state == null) {
                return 1.0; // 默认权重
            }

            double currentEnd = regressionResult.getEnd();
            double diff = calculateDiff(state, currentEnd);

            // 判断信号强度
            double strength = 1.0;

            // 1. 基于斜率的强度
            double slopeAbs = Math.abs(regressionResult.getSlope());
            double slopeStrength = Math.min(slopeAbs * 1000, 1.0);

            // 2. 基于diff大小的强度
            double diffStrength = Math.min(Math.abs(diff) / (regressionResult.getEnd() * 0.01), 1.0);

            // 3. 基于趋势一致性
            boolean trendUp = regressionResult.getEnd() > regressionResult.getStart();
            boolean signalIsBuy = "BUY".equals(signalType);
            double trendStrength;

            // 如果是买入信号且趋势向上，或者卖出信号且趋势向下，强度更高
            if ((signalIsBuy && trendUp) || (!signalIsBuy && !trendUp)) {
                trendStrength = 1.0;
            } else {
                trendStrength = 0.5;
            }

            // 综合强度
            strength = 1.0 + (slopeStrength * 0.3 + diffStrength * 0.4 + trendStrength * 0.3);

            log.debug("交易对: {}, LogReg 信号强度 - 斜率: {}, 差值: {}, 趋势: {}, 最终: {}",
                    symbol,
                    String.format("%.3f", slopeStrength),
                    String.format("%.3f", diffStrength),
                    String.format("%.3f", trendStrength),
                    String.format("%.3f", strength));

            return strength;

        } catch (Exception e) {
            log.error("计算LogReg信号强度失败: {}", e.getMessage(), e);
            return 1.0; // 默认权重
        }
    }

    /**
     * 保存信号到Redis
     */
    private void saveLastSignalToRedis(String symbol, String signalType, LogRegSignal signal) {
        try {
            String redisKey = String.format("logreg_channel:signal:%s", symbol);
            String value = String.format("%s|%.4f|%.6f|%.4f|%s|%d",
                    signalType, signal.getEndValue(), signal.getSlope(),
                    signal.getDiff(), signal.getTrendDirection(), System.currentTimeMillis());

            log.debug("保存LogReg信号到Redis: {} -> {}", redisKey, value);

            // 这里需要调用Redis服务保存，示例代码
            // redisTemplate.opsForValue().set(redisKey, value, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("保存LogReg信号到Redis失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 与TradingView对比的调试方法
     */
    public void debugComparisonWithTV(List<Candlestick> kLines, String symbol, int length) {
        BarSeries series = IndicatorWrapHelper.buildSeries(kLines);

        log.info("=== LogReg Channel-Trend 与TradingView对比调试 ===");
        log.info("参数: length={}, diffPeriod={}", length, diffPeriod);
        log.info("索引 | 收盘价 | 回归值(end) | end[3] | diff | 信号");
        log.info("----------------------------------------------------------------");

        // 重置状态
        stateMap.put(symbol, new LogRegState());

        for (int i = length; i < series.getBarCount(); i++) {
            try {
                // 计算回归
                LogRegressionResult result = calculateLogRegression(series, length, i);
                double currentEnd = result.getEnd();

                // 获取状态
                LogRegState state = stateMap.get(symbol);
                state.addRegressionValue(currentEnd);

                // 计算diff
                double end3Value = state.getRegressionValueAt(diffPeriod);
                double diff = Double.isNaN(end3Value) ? 0.0 : currentEnd - end3Value;

                // 获取前一个diff
                double prevDiff = state.getPrevDiff();

                // 判断信号
                String signal = "";
                if (isCrossover(prevDiff, diff, 0)) {
                    signal = "BUY↑";
                } else if (isCrossunder(prevDiff, diff, 0)) {
                    signal = "SELL↓";
                }

                // 更新前一个diff
                state.setPrevDiff(diff);

                log.info("{}\t{:.4f}\t{:.4f}\t{:.4f}\t{:.4f}\t{}",
                        i, series.getBar(i).getClosePrice().doubleValue(),
                        currentEnd, end3Value, diff, signal);

            } catch (Exception e) {
                log.error("索引 {} 计算失败: {}", i, e.getMessage());
            }
        }
    }

    /**
     * 批量获取多个交易对的趋势信息
     */
    public Map<String, LogRegTrendInfo> getBatchTrendInfo(Map<String, List<Candlestick>> symbolKLinesMap) {
        Map<String, LogRegTrendInfo> result = new HashMap<>();

        for (Map.Entry<String, List<Candlestick>> entry : symbolKLinesMap.entrySet()) {
            try {
                LogRegTrendInfo trendInfo = getCurrentTrendInfo(entry.getValue(), entry.getKey());
                result.put(entry.getKey(), trendInfo);
            } catch (Exception e) {
                log.error("获取交易对 {} 趋势信息失败: {}", entry.getKey(), e.getMessage());
                result.put(entry.getKey(), LogRegTrendInfo.empty(entry.getKey()));
            }
        }

        return result;
    }

    /**
     * 获取趋势统计信息
     */
    public Map<String, Object> getTrendStatistics(List<LogRegTrendInfo> trendInfos) {
        Map<String, Object> stats = new HashMap<>();

        if (trendInfos.isEmpty()) {
            return stats;
        }

        // 统计趋势分布
        long bullishCount = trendInfos.stream()
                .filter(info -> "BULLISH".equals(info.getLongTermTrend()))
                .count();
        long bearishCount = trendInfos.size() - bullishCount;

        // 平均趋势强度
        double avgStrength = trendInfos.stream()
                .mapToDouble(LogRegTrendInfo::getTrendStrength)
                .average()
                .orElse(0.0);

        // 在通道内的比例
        long inChannelCount = trendInfos.stream()
                .filter(LogRegTrendInfo::isInChannel)
                .count();

        stats.put("totalSymbols", trendInfos.size());
        stats.put("bullishCount", bullishCount);
        stats.put("bearishCount", bearishCount);
        stats.put("bullishRatio", (double) bullishCount / trendInfos.size());
        stats.put("avgTrendStrength", avgStrength);
        stats.put("inChannelRatio", (double) inChannelCount / trendInfos.size());
        stats.put("timestamp", System.currentTimeMillis());

        return stats;
    }

    /**
     * 清除状态
     */
    public void clearState(String symbol) {
        stateMap.remove(symbol);
        log.info("已清除交易对 {} 的LogReg状态", symbol);
    }

    /**
     * 获取所有交易对状态
     */
    public Map<String, LogRegState> getAllStates() {
        return new HashMap<>(stateMap);
    }
}