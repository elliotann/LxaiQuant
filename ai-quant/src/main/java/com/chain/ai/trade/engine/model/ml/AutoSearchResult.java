package com.chain.ai.trade.engine.model.ml;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("auto_search_result")
public class AutoSearchResult extends BaseEntity {
    private String searchId;
    private String symbol;
    private String status;
    private Integer totalCombinations;
    private Integer completedCombinations;
    private String bestSoFar;
    private String finalTop20;
    private Date startTime;
    private Date endTime;
    private String errorMsg;
    private String requestJson;
}
