package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.common.entity.constants.Exchange;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * K线查询参数
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KlineQueryParam {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * K线间隔
     */
    private CandlestickIntervalEnum interval;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 限制数量
     */
    private Integer limit;

    /**
     * 数据源类型
     */
    private String dataSourceType;


    @ApiModelProperty(value = "指标名称")
    private String indicatorType;

    private Exchange memberPlatform;

    private String timeStr;

    /**
     * 查询时间断，10位，单位为s
     */
    private long from;
    /**
     * 查询时间断，10位，单位为s
     */
    private long to;
}
