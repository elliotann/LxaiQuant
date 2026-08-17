package com.chain.ai.trade.engine.notifier.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification_config")
public class NotificationConfig extends BaseEntity {

    private String userId;
    private String channel;
    private Boolean enabled;
    private String configJson;
}
