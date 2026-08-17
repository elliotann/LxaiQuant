package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 交易账户DTO - 与前端TradingAccount接口匹配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingAccountDTO {
    private String id;
    private String name; // accountName映射为name
    private String platform; // memberPlatform映射为platform
    private String apiKey;
    private String type;
    private Boolean simulated;
    private boolean isActive;
    private double balance;
    private double pnl; // 当日盈亏
    private String status; // active/inactive
    private LocalDateTime createdAt; // createTime映射为createdAt
    private LocalDateTime updatedAt; // updateTime映射为updatedAt

    // 后端原始字段（可选）
    private String accountName;
    private String memberId;
    private String memberPlatform;
    private String apiSecret;
    private String bindStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String platformName;

    // 手动添加 getter/setter 方法确保 Lombok 正常工作
    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setIsActive(boolean active) {
        this.isActive = active;
    }
}
