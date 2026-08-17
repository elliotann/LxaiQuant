package com.chain.ai.trade.engine.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实盘交易请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveTradingRequest {

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 策略参数
     */
    private Map<String, Object> parameters;

    /**
     * 是否测试模式
     * true: 测试模式，不发送真实请求到交易所，仅做系统内部订单操作
     * false: 实盘模式，正常发送请求到交易所
     */
    @Builder.Default
    private Boolean testMode = false;

    /**
     * 开始时间（用于测试模式，使用历史数据）
     * 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd
     * 如果为空，实盘模式从当前时间开始，测试模式需要指定
     */
    private String startTime;

    /**
     * 结束时间（用于测试模式，使用历史数据）
     * 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd
     * 如果为空，实盘模式持续运行，测试模式需要指定
     */
    private String endTime;
}
