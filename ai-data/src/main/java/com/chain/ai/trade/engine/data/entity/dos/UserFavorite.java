package com.chain.ai.trade.engine.data.entity.dos;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户自选股实体
 */
@Data
@TableName("user_favorites")
public class UserFavorite {

    private Integer id;
    private String userId;
    private Integer symbolId;
    private LocalDateTime createdAt;

    /** 标的详情（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private Symbol symbol;
}
