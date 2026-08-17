package com.chain.ai.trade.engine.notifier.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification_log")
public class NotificationLog extends BaseEntity {

    private String userId;
    private String channel;
    private String type;
    private String title;
    private String content;
    private String status;
    private String errorMsg;
    private Date sentAt;
    private Boolean isRead;
    private Date readAt;
}
