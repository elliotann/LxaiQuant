package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 机器人参数实体 - 存储机器人级别的参数配置
 * 分组存储，支持 risk_control、add_position_config 等分组
 */
@Data
@TableName("bot_parameter")
public class BotParameter implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 机器人ID
     */
    private String botId;

    /**
     * 分组名，如 risk_control, add_position_config
     */
    private String groupName;

    /**
     * 参数名
     */
    private String name;

    /**
     * 参数值
     */
    private String value;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
