package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName("weight_rule_version")
@Data
public class WeightRuleVersion implements Serializable {
    private Long id;
    private Long configId;
    private Integer version;
    private String configJson;
    private String status;
    private String remark;
    private String createdBy;
    private Date createTime;
}
