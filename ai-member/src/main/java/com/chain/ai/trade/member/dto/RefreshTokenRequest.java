package com.chain.ai.trade.member.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
    private String accessToken;
}
