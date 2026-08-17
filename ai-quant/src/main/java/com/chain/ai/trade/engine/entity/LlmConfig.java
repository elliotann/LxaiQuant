package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_llm_config")
public class LlmConfig extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String configKey;

    private String provider;

    private String model;

    private String apiBaseUrl;

    private String apiKeyEnc;

    private Boolean apiKeyConfigured;

    private String extraConfig;
}

