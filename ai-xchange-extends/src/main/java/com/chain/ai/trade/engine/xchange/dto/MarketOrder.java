package com.chain.ai.trade.engine.xchange.dto;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

/**
 * DTO representing a market order
 *
 * <p>A market order is a buy or sell order to be executed immediately at current market prices. As
 * long as there are willing sellers and buyers, market orders are filled. Market orders are
 * therefore used when certainty of execution is a priority over price of execution. <strong>Use
 * market orders with caution, and review {@link LimitOrder} in case it is more suitable.</strong>
 */
@JsonDeserialize
@Data
public class MarketOrder  {

    private static final long serialVersionUID = -3393286268772319210L;

    private String apiKey;

    private String secretKey;

    private String passphrase;


    private Boolean simulated;
    private String symbol;
    private String memberId;
    private Long accountId;

    //保证金模式：isolated：逐仓 ；cross：全仓
    private String tdMode;

    private OrderSideEnum side;

    private BigDecimal price;

    private BigDecimal amount;

    private String source;

    private String clientOrderId;

    private String orderId;

    //客户自定义策略订单ID,用于止盈止损
    private String algoClOrdId;

    private BigDecimal stopPrice;

    private int leverRate;

    @ApiModelProperty("开平方向,open close")
    private String offset;

    @ApiModelProperty("止盈价格")
    private BigDecimal stopGain;

    private String stopGainType;

    @ApiModelProperty("止损价格")
    private BigDecimal stopLoss;

    private String stopLossType;

    @ApiModelProperty("止盈止损订单类型，oco：双向止盈止损，move_order_stop：移动止盈止损，twap：时间加权委托")
    private String gainAndLossType;
    //------------------------------- Spot ---------------------------------------//


    @ApiModelProperty("固定止盈价，触发该价格后会平仓一半")
    private BigDecimal fixedStopGainPrice;

    @ApiModelProperty("修改止盈止损委托单的新数量")
    private BigDecimal newSz;


}