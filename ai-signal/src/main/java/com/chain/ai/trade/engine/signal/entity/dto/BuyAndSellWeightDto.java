package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 做多与做空
 */
@Data
public class BuyAndSellWeightDto {
    private SignalType signalType;

    private double ma1;

    private double ma2;

    private double ma3;

    private double ma4;

    /**
     * 开线周期
     */
    @ApiModelProperty(value = "信号周期")
    private CandlestickIntervalEnum dataInterval;

    @ApiModelProperty(value = "关联信号ID")
    private Long signalId;

    /**
     * K线时间
     */
    @ApiModelProperty(value = "K线时间")
    private String klineTime;

}
