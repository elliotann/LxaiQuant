package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户管理DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManagementDTO {
    private String userId;
    private String username;
    private String email;
    private String phone;
    private String role;
    private String status;
    private String preferences;
    private String securityConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginTime;
}
