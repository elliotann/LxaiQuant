package com.chain.ai.trade.backtest.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_equity_curve")
public class BacktestEquityCurve {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String robotId;

    private String robotName;

    private LocalDateTime time;

    private BigDecimal equity;

    /** 周期收益率（相对上一个采样点的环比） */
    private BigDecimal returnRate;

    private BigDecimal drawdown;

    /** 基准指数净值 */
    private BigDecimal benchmarkValue;

    /** 基准周期收益率 */
    private BigDecimal benchmarkReturnRate;
}
