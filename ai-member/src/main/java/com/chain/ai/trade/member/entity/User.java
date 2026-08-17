package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    @TableId
    private String userId;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private UserRole role;
    private UserStatus status;
    private String preferences;
    private String securityConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginTime;

    private Integer creditsBalance;
    private String membershipLevel;
    private LocalDateTime membershipExpireTime;

    private Integer version;

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updateTime = LocalDateTime.now();
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.updateTime = LocalDateTime.now();
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
        this.updateTime = LocalDateTime.now();
    }

    public void updateProfile(String email, String phone) {
        if (email != null && !email.trim().isEmpty()) {
            this.email = email.trim();
        }
        if (phone != null && !phone.trim().isEmpty()) {
            this.phone = phone.trim();
        }
        this.updateTime = LocalDateTime.now();
    }

    public void recordLogin() {
        this.lastLoginTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public boolean isPremium() {
        return this.role == UserRole.ADMIN || this.role == UserRole.PREMIUM;
    }
}
