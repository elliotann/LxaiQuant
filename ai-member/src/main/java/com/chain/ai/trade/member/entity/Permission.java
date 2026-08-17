package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String permCode;
    private String permName;
    private String module;
    private LocalDateTime createTime;
}
