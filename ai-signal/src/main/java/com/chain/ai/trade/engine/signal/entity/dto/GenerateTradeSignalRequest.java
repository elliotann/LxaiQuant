package com.chain.ai.trade.engine.signal.entity.dto;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交易信号生成请求DTO
 */
@Data
public class GenerateTradeSignalRequest {

    @ApiModelProperty(value = "技术信号ID", required = true, example = "12345")
    @NotNull(message = "技术信号ID不能为空")
    private Long technicalSignalId;

    @ApiModelProperty(value = "交易对", required = true, example = "BTCUSDT")
    @NotBlank(message = "交易对不能为空")
    private String symbol;

    @ApiModelProperty(value = "账户ID", example = "account_001")
    private String accountId;

    @ApiModelProperty(value = "策略ID", example = "strategy_momentum")
    private String strategyId;

    @ApiModelProperty(value = "最大仓位比例", example = "0.1", notes = "默认值为0.1（10%）")
    @NotNull(message = "最大仓位比例不能为空")
    @DecimalMin(value = "0.01", message = "仓位比例不能小于1%")
    @DecimalMax(value = "1.0", message = "仓位比例不能超过100%")
    private BigDecimal maxPositionRatio = BigDecimal.valueOf(0.1);  // 最大仓位比例，默认10%

    @ApiModelProperty(value = "止损比例", example = "0.02", notes = "相对于开仓价格的止损比例，如0.02表示2%")
    @DecimalMin(value = "0.001", message = "止损比例不能小于0.1%")
    @DecimalMax(value = "0.5", message = "止损比例不能超过50%")
    private BigDecimal stopLossRatio;      // 止损比例

    @ApiModelProperty(value = "止盈比例", example = "0.06", notes = "相对于开仓价格的止盈比例，如0.06表示6%")
    @DecimalMin(value = "0.001", message = "止盈比例不能小于0.1%")
    @DecimalMax(value = "1.0", message = "止盈比例不能超过100%")
    private BigDecimal takeProfitRatio;    // 止盈比例

    @ApiModelProperty(value = "杠杆倍数", example = "5", notes = "杠杆交易的倍数，1表示不使用杠杆")
    @DecimalMin(value = "1", message = "杠杆倍数不能小于1")
    @DecimalMax(value = "100", message = "杠杆倍数不能超过100")
    private Integer leverage;              // 杠杆倍数

    @ApiModelProperty(value = "风控等级", example = "MEDIUM", notes = "LOW, MEDIUM, HIGH")
    private String riskLevel;              // 风控等级

    @ApiModelProperty(value = "优先级", example = "5", notes = "1-10，数字越大优先级越高")
    @DecimalMin(value = "1", message = "优先级不能小于1")
    @DecimalMax(value = "10", message = "优先级不能超过10")
    private Integer priority;              // 优先级，1-10
}
