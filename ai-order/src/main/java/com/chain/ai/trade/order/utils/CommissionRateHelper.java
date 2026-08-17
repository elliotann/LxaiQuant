package com.chain.ai.trade.order.utils;

import com.alibaba.fastjson.JSONObject;
import com.chain.ai.trade.engine.strategy.service.IBotParameterService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 手续费率工具类
 * 从 bot_parameter 表中读取机器人的手续费率配置
 */
@Slf4j
public class CommissionRateHelper {

    /** 默认手续费率兜底值 */
    public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.00045");

    /**
     * 从 bot_parameter 获取机器人的手续费率
     *
     * @param botId       机器人ID
     * @param paramService BotParameter 服务（可为 null）
     * @return 手续费率，若不存在则返回 null
     */
    public static BigDecimal getCommissionRate(String botId, IBotParameterService paramService) {
        if (botId == null || paramService == null) {
            return null;
        }
        try {
            String json = paramService.getParameterValue(botId, "config", "config");
            if (json != null && !json.isBlank()) {
                JSONObject obj = JSONObject.parseObject(json);
                if (obj != null && obj.containsKey("commission_rate")) {
                    return obj.getBigDecimal("commission_rate");
                }
            }
        } catch (Exception e) {
            log.warn("读取机器人手续费率失败, botId={}", botId, e);
        }
        return null;
    }
}
