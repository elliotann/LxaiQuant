package com.chain.ai.trade.engine.signal.entity.vo;

import cn.hutool.json.JSONUtil;
// Temporarily commented out for compatibility
// import com.vdr.modules.trade.model.dto.NewSignalDTO;
import com.chain.ai.trade.engine.signal.entity.dto.NewSignalDTO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;


@Data
public class SignalSaveReqVO {


    private Long id;


    @NotEmpty(message = "指标类型不能为空")
    private String indicatorType;

    private String strategyType;

    @NotEmpty(message = "数据来源不能为空")
    private String dataFrom;


    @NotEmpty(message = "数据周期不能为空")
    private String dataInterval;


    private String klineTime;


    @NotEmpty(message = "币种不能为空")
    private String symbol;


    private BigDecimal closePrice;

    @ApiModelProperty(value = "关键KEY高点")
    private BigDecimal highPrice;

    @ApiModelProperty(value = "关键KEY低点")
    private BigDecimal lowPrice;


    @NotEmpty(message = "趋势不能为空")
    private String trend;

    private NewSignalDTO signalDTO;

    public @NotEmpty(message = "趋势不能为空") String getTrend() {
        if(signalDTO!=null){
            return JSONUtil.toJsonStr(signalDTO);
        }
        return trend;
    }




}