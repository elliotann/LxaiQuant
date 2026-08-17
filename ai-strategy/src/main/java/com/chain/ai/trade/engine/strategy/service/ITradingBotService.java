package com.chain.ai.trade.engine.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 交易机器人服务接口
 */
public interface ITradingBotService extends IService<TradingBot> {

    /**
     * 根据机器人ID查询机器人
     */
    TradingBot getByBotId(String botId);

    /**
     * 创建交易机器人
     */
    TradingBot createBot(TradingBot bot);

    /**
     * 更新交易机器人
     */
    boolean updateBot(TradingBot bot);

    /**
     * 删除交易机器人
     */
    boolean deleteBot(String botId);

    /**
     * 启动机器人
     */
    boolean startBot(String botId);

    /**
     * 停止机器人
     */
    boolean stopBot(String botId);

    /**
     * 暂停机器人
     */
    boolean pauseBot(String botId);

    /**
     * 恢复机器人
     */
    boolean resumeBot(String botId);

    /**
     * 更新机器人状态
     */
    boolean updateBotStatus(String botId, String status);

    /**
     * 批量更新机器人状态
     */
    boolean batchUpdateStatus(List<String> botIds, String status);

    /**
     * 分页查询机器人
     */
    IPage<TradingBot> pageBots(Page<TradingBot> page, String botName, String userId,
                              String exchange, String status, String strategyId, String accountId, String tradingPair);

    /**
     * 根据用户ID查询机器人列表
     */
    List<TradingBot> listByUserId(String userId);

    /**
     * 根据策略ID查询机器人列表
     */
    List<TradingBot> listByStrategyId(String strategyId);

    /**
     * 根据账户ID查询机器人列表
     */
    List<TradingBot> listByAccountId(String accountId);

    /**
     * 根据状态查询机器人列表
     */
    List<TradingBot> listByStatus(String status);

    /**
     * 更新机器人资金
     */
    boolean updateCapital(String botId, BigDecimal allocatedCapital, BigDecimal currentCapital);

    /**
     * 更新机器人统计信息
     */
    boolean updateStatistics(String botId, String statistics);

    /**
     * 更新最后信号时间
     */
    boolean updateLastSignalTime(String botId);

    /**
     * 生成机器人ID
     */
    String generateBotId();

    /**
     * 统计用户机器人数量
     */
    int countByUserId(String userId);

    /**
     * 统计运行中的机器人数量
     */
    int countRunningBots();

    /**
     * 验证机器人配置
     */
    boolean validateBotConfiguration(TradingBot bot);

    /**
     * 获取机器人运行状态详情
     */
    String getBotRunningStatus(String botId);

    /**
     * 获取机器人状态统计信息
     * @return 包含各状态机器人数量的Map
     */
    Map<String, Object> getStatusStats();
}
