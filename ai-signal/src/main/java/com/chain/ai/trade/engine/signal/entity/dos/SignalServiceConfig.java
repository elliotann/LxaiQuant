package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName("signal_service_config")
@Data
public class SignalServiceConfig implements Serializable {
    private Long id;
    private String name;
    private String serviceKey;
    private String paramsJson;
    private String weightRulesJson;
    private Boolean enabled;
    private Date createdAt;
    private Date updatedAt;
}
