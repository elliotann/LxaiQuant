package com.chain.ai.trade.engine.signal.entity.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交易信号生成响应DTO
 */
@Data
public class GenerateTradeSignalResponse {

    @ApiModelProperty(value = "是否成功")
    private boolean success;

    @ApiModelProperty(value = "响应消息")
    private String message;

    @ApiModelProperty(value = "生成的交易信号ID")
    private Long tradeSignalId;

    @ApiModelProperty(value = "订单号")
    private String orderSn;

    @ApiModelProperty(value = "计算出的开仓价格")
    private BigDecimal calculatedPrice;

    @ApiModelProperty(value = "计算出的开仓数量")
    private BigDecimal calculatedAmount;

    @ApiModelProperty(value = "建议的止损价")
    private BigDecimal suggestedStopLoss;

    @ApiModelProperty(value = "建议的止盈价")
    private BigDecimal suggestedTakeProfit;

    @ApiModelProperty(value = "风险评估等级")
    private String riskAssessment;

    @ApiModelProperty(value = "决策原因说明")
    private String decisionReason;

    @ApiModelProperty(value = "警告信息")
    private String warningMessage;

    // 便捷的静态工厂方法
    public static GenerateTradeSignalResponse success(Long tradeSignalId, String orderSn) {
        GenerateTradeSignalResponse response = new GenerateTradeSignalResponse();
        response.setSuccess(true);
        response.setTradeSignalId(tradeSignalId);
        response.setOrderSn(orderSn);
        response.setMessage("交易信号生成成功");
        return response;
    }

    public static GenerateTradeSignalResponse failure(String reason) {
        GenerateTradeSignalResponse response = new GenerateTradeSignalResponse();
        response.setSuccess(false);
        response.setMessage("交易信号生成失败: " + reason);
        return response;
    }

    public static GenerateTradeSignalResponse rejected(String reason) {
        GenerateTradeSignalResponse response = new GenerateTradeSignalResponse();
        response.setSuccess(false);
        response.setMessage("交易信号被拒绝: " + reason);
        return response;
    }
}
