package com.chain.ai.trade.engine.signal.entity.dto;

// Temporarily commented out for compatibility
// import com.vdr.modules.member.entity.dos.MemberThirdAccount;
// import com.vdr.modules.trade.entity.constants.CandlestickIntervalEnum;
// import com.vdr.modules.trade.entity.constants.OrderSideEnum;

// Use local constants
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class TradeSignalSignalDto {

    // Temporarily commented out for compatibility
    private Exchange memberPlatform;

    private String symbol;

    private String robotId;

    private String accountId;

    @ApiModelProperty("获取K线周期")
    private CandlestickIntervalEnum klineInterval;

    @ApiModelProperty("K线时间，格式：yyyy-MM-dd HH:mm:ss")
    private String klineTime;

    private String orderSn;

    private BigDecimal buyPrice;

    private BigDecimal sellPrice;

    private String signalTrend;

    private Date orderTime;

    private Date sellTime;

    private OrderSideEnum orderSideEnum;

    private BigDecimal amount;


    @ApiModelProperty(value = "实际收益")
    private BigDecimal income;

    @ApiModelProperty(value = "手续费")
    private BigDecimal charge;
}
