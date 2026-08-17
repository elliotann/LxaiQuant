package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("credits_log")
public class CreditsLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Integer amount;
    private Integer balanceAfter;
    private String type;
    private String refId;
    private String description;
    private LocalDateTime createdAt;
}
