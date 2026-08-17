package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("review_tasks")
public class ReviewTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private Date timeRangeStart;

    private Date timeRangeEnd;

    private String robotId;

    private String status;

    private String reportJson;

    private Date completedAt;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;
}
