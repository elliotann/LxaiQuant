package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 交易机器人Mapper接口
 */
public interface TradingBotMapper extends BaseMapper<TradingBot> {

    /**
     * 根据机器人ID查询
     */
    TradingBot selectByBotId(@Param("botId") String botId);

    /**
     * 根据用户ID查询机器人列表
     */
    List<TradingBot> selectByUserId(@Param("userId") String userId);

    /**
     * 根据策略ID查询机器人列表
     */
    List<TradingBot> selectByStrategyId(@Param("strategyId") String strategyId);

    /**
     * 根据账户ID查询机器人列表
     */
    List<TradingBot> selectByAccountId(@Param("accountId") String accountId);

    /**
     * 根据状态查询机器人列表
     */
    List<TradingBot> selectByStatus(@Param("status") String status);

    /**
     * 分页查询机器人
     */
    IPage<TradingBot> selectPage(Page<TradingBot> page,
                                @Param("botName") String botName,
                                @Param("userId") String userId,
                                @Param("exchange") String exchange,
                                @Param("status") String status,
                                @Param("strategyId") String strategyId,
                                @Param("accountId") String accountId,
                                @Param("tradingPair") String tradingPair);

    /**
     * 更新机器人状态
     */
    int updateStatus(@Param("botId") String botId, @Param("status") String status);

    /**
     * 更新机器人统计信息
     */
    int updateStatistics(@Param("botId") String botId, @Param("statistics") String statistics);

    /**
     * 批量更新机器人状态
     */
    int batchUpdateStatus(@Param("botIds") List<String> botIds, @Param("status") String status);

    /**
     * 统计用户机器人数量
     */
    int countByUserId(@Param("userId") String userId);

    /**
     * 统计运行中的机器人数量
     */
    int countRunningBots();

    /**
     * 获取状态统计信息
     * @return 状态和对应数量的Map列表
     */
    List<Map<String, Object>> selectStatusStats();
}
