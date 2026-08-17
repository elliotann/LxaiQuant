package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String menuCode;
    private String menuName;
    private String icon;
    private String routePath;
    private Integer parentId;
    private Integer sortOrder;
    private String permCode;
    private Boolean enabled;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<SysMenu> children;
}
