package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.engine2.core.cost.CostModel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RealtimeConfig {

    private String symbol;
    private Exchange exchange;
    private String interval;
    private BigDecimal initialCapital;
    private int leverage;
    private BigDecimal positionAmount = BigDecimal.valueOf(1000);
    private BigDecimal slippage = BigDecimal.valueOf(0.001);
    private ContractSpec contractSpec;
    private CostModel costModel;
    private int warmupPeriod = 50;
    private String positionMode = "QUALITY";

    /** 交易用户ID（memberId），用于通知推送 */
    private String userId;

    /** 交易账户ID */
    private String accountId;

    /** 策略机器人ID */
    private String robotId;

    /** 启用同向信号频率限制 */
    @Builder.Default
    boolean signalFrequencyEnabled = false;

    /** 限制粒度: 3min / 15min / 1hour */
    @Builder.Default
    String signalFrequencyGranularity = "15min";

    /** 限制模式: strict_lock / structure_upgrade_exempt / unlimited */
    @Builder.Default
    String signalFrequencyMode = "structure_upgrade_exempt";
}