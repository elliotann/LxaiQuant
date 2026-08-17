package com.chain.ai.trade.engine.strategy.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiGenerateRequest {

    @NotBlank(message = "请描述您的交易策略需求")
    private String prompt;

    private String intent = "bot_recommend";

    private String marketType = "spot";
}
