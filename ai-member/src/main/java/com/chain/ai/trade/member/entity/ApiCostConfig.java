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
@TableName("api_cost_config")
public class ApiCostConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String apiName;
    private Integer costCredits;
    private String description;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
