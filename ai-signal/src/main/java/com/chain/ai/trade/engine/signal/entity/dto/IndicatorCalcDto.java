package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class IndicatorCalcDto {

    /**
     * 策略ID
     */
    private String robotId;

    private String robotName;

    private BarSeries series;

    private List<Candlestick> kLines;

    //当前bar
    private Candlestick currentCandlestick;

    private CandlestickIntervalEnum candlestickIntervalEnum;

    /**
     * 长周期
     */
    private int longLengthOpen;

    /**
     * 短周期
     */
    private int shortLengthOpen;

    private int lengthOpen;

    /**
     * 产品
     */
    private String symbol;

    /**
     * 是u否过滤均线
     */
    private boolean isFilterEma;

    private int emaLength=100;

    private boolean isFilterHama;

    private float multiplier;



    //现价
    private Num nowPrice;

    //当前策略方向，需要开空还是开多
    private String openSide;



    /**
     * 处理的 k 线数
     */
    private int klineLength;

    private int atrPeriod; // 添加缺失的字段

    private int adxThreshold;

    private double volFactor;

    /**
     * 当前仓位
     */
    private Position position;

    private Num buyPrice;

    private Num buyAmount;

    private String tpAggressiveness;

    private boolean useATR;
    //用于计算权重是信号方向
    private SignalType signalType;

    private Map<String, String> parameterOverrides;


    private OrderPriceType entryType;
    private BigDecimal limitPrice;

    private String marketTrend;

    private String strategyType;

    private String configuration;
}
