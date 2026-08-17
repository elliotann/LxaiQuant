package com.chain.ai.trade.engine.data.entity.dos;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 统一标的字典实体
 */
@Data
@TableName("symbols")
public class Symbol {

    private Integer id;
    private String market;
    private String symbol;
    private String name;
    private String exchange;
    private Boolean isHot;
    private Integer sortOrder;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
