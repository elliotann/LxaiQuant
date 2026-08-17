package com.chain.ai.trade.engine2.core.execution;

import lombok.Builder;
import lombok.Value;

/**
 * 撤单结果
 */
@Value
@Builder
public class CancelResult {

    /** 是否成功 */
    boolean success;

    /** 失败原因 */
    String reason;

    public static CancelResult ok() {
        return CancelResult.builder().success(true).build();
    }

    public static CancelResult fail(String reason) {
        return CancelResult.builder().success(false).reason(reason).build();
    }
}
