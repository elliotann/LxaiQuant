package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_role_rel")
public class UserRoleRel {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String userId;
    private Integer roleId;
}
