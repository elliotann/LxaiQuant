package com.chain.ai.trade.engine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 从交易所导入K线请求
 */
@Data
public class KLineImportFromExchangeRequest {

    /** 交易所标识，如 OKX */
    @NotBlank(message = "交易所不能为空")
    private String exchange;

    /** 交易对，如 BTC-USDT */
    @NotBlank(message = "交易对不能为空")
    private String symbol;

    /** K线周期，如 1m, 3m, 5m, 15m, 30m, 1h, 4h, 1d */
    @NotBlank(message = "周期不能为空")
    private String interval;

    /** 开始时间（毫秒时间戳） */
    @NotNull(message = "开始时间不能为空")
    private Long startTime;

    /** 结束时间（毫秒时间戳） */
    @NotNull(message = "结束时间不能为空")
    private Long endTime;

    /** 可选：交易账户 ID，选择后使用该账户的交易所 API 拉取；不选则使用公开 API */
    private String accountId;
}
