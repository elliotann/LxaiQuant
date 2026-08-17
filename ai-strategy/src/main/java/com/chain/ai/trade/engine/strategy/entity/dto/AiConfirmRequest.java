package com.chain.ai.trade.engine.strategy.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class AiConfirmRequest {

    @NotNull
    private AiStrategyRecommendation recommendation;

    @NotBlank
    private String botName;

    private String remark;

    @NotBlank
    private String userId;

    private String accountId;
}
