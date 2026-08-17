package com.chain.ai.trade.engine.strategy.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.enums.BotStatus;
import com.chain.ai.trade.engine.strategy.mapper.TradingBotMapper;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 交易机器人服务实现
 */
@Slf4j
@Service
public class TradingBotServiceImpl extends ServiceImpl<TradingBotMapper, TradingBot> implements ITradingBotService {

    @Override
    public TradingBot getByBotId(String botId) {
        return baseMapper.selectByBotId(botId);
    }

    @Override
    @Transactional
    public TradingBot createBot(TradingBot bot) {
        // 生成机器人ID
        bot.setBotId(generateBotId());

        // 设置默认值
        bot.setStatus(BotStatus.CREATED.getCode());
        bot.setEnabled(true);
        bot.setCreatedAt(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());

        // 如果当前资金为空，设置为分配资金
        if (bot.getCurrentCapital() == null) {
            bot.setCurrentCapital(bot.getAllocatedCapital());
        }

        // 验证配置
        if (!validateBotConfiguration(bot)) {
            throw new IllegalArgumentException("机器人配置验证失败");
        }

        // 保存到数据库
        save(bot);

        log.info("创建交易机器人成功: botId={}, botName={}", bot.getBotId(), bot.getBotName());
        return bot;
    }

    @Override
    @Transactional
    public boolean updateBot(TradingBot bot) {
        bot.setUpdatedAt(LocalDateTime.now());

        // 验证配置
        if (!validateBotConfiguration(bot)) {
            throw new IllegalArgumentException("机器人配置验证失败");
        }

        boolean result = updateById(bot);
        if (result) {
            log.info("更新交易机器人成功: botId={}", bot.getBotId());
        }
        return result;
    }

    @Override
    @Transactional
    public boolean deleteBot(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        // 只能删除已停止的机器人
        if (BotStatus.RUNNING.getCode().equals(bot.getStatus())) {
            throw new IllegalStateException("运行中的机器人不能删除，请先停止机器人");
        }

        boolean result = removeById(bot.getId());
        if (result) {
            log.info("删除交易机器人成功: botId={}", botId);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean startBot(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        // 只能启动已创建或已停止的机器人
        if (!BotStatus.CREATED.getCode().equals(bot.getStatus()) &&
            !BotStatus.STOPPED.getCode().equals(bot.getStatus()) &&
            !BotStatus.PAUSED.getCode().equals(bot.getStatus())) {
            throw new IllegalStateException("机器人状态不允许启动");
        }

        bot.setStatus(BotStatus.RUNNING.getCode());
        bot.setStartTime(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("启动交易机器人成功: botId={}", botId);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean stopBot(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        bot.setStatus(BotStatus.STOPPED.getCode());
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("停止交易机器人成功: botId={}", botId);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean pauseBot(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        // 只能暂停运行中的机器人
        if (!BotStatus.RUNNING.getCode().equals(bot.getStatus())) {
            throw new IllegalStateException("只有运行中的机器人才能暂停");
        }

        bot.setStatus(BotStatus.PAUSED.getCode());
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("暂停交易机器人成功: botId={}", botId);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean resumeBot(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        // 只能恢复已暂停的机器人
        if (!BotStatus.PAUSED.getCode().equals(bot.getStatus())) {
            throw new IllegalStateException("只有已暂停的机器人才能恢复");
        }

        bot.setStatus(BotStatus.RUNNING.getCode());
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("恢复交易机器人成功: botId={}", botId);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean updateBotStatus(String botId, String status) {
        int result = baseMapper.updateStatus(botId, status);
        if (result > 0) {
            log.info("更新机器人状态成功: botId={}, status={}", botId, status);
        }
        return result > 0;
    }

    @Override
    @Transactional
    public boolean batchUpdateStatus(List<String> botIds, String status) {
        int result = baseMapper.batchUpdateStatus(botIds, status);
        if (result > 0) {
            log.info("批量更新机器人状态成功: count={}, status={}", result, status);
        }
        return result > 0;
    }

    @Override
    public IPage<TradingBot> pageBots(Page<TradingBot> page, String botName, String userId,
                                    String exchange, String status, String strategyId, String accountId, String tradingPair) {
        return baseMapper.selectPage(page, botName, userId, exchange, status, strategyId, accountId, tradingPair);
    }

    @Override
    public List<TradingBot> listByUserId(String userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public List<TradingBot> listByStrategyId(String strategyId) {
        return baseMapper.selectByStrategyId(strategyId);
    }

    @Override
    public List<TradingBot> listByAccountId(String accountId) {
        return baseMapper.selectByAccountId(accountId);
    }

    @Override
    public List<TradingBot> listByStatus(String status) {
        return baseMapper.selectByStatus(status);
    }

    @Override
    @Transactional
    public boolean updateCapital(String botId, BigDecimal allocatedCapital, BigDecimal currentCapital) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        bot.setAllocatedCapital(allocatedCapital);
        bot.setCurrentCapital(currentCapital);
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("更新机器人资金成功: botId={}, allocated={}, current={}",
                    botId, allocatedCapital, currentCapital);
        }
        return result;
    }

    @Override
    @Transactional
    public boolean updateStatistics(String botId, String statistics) {
        int result = baseMapper.updateStatistics(botId, statistics);
        if (result > 0) {
            log.info("更新机器人统计信息成功: botId={}", botId);
        }
        return result > 0;
    }

    @Override
    @Transactional
    public boolean updateLastSignalTime(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return false;
        }

        bot.setLastSignalTime(LocalDateTime.now());
        bot.setUpdatedAt(LocalDateTime.now());

        boolean result = updateById(bot);
        if (result) {
            log.info("更新机器人最后信号时间成功: botId={}", botId);
        }
        return result;
    }

    @Override
    public String generateBotId() {
        return "BOT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    @Override
    public int countByUserId(String userId) {
        return baseMapper.countByUserId(userId);
    }

    @Override
    public int countRunningBots() {
        return baseMapper.countRunningBots();
    }

    @Override
    public boolean validateBotConfiguration(TradingBot bot) {
        if (bot == null) {
            return false;
        }

        // 验证必填字段
        if (bot.getBotName() == null || bot.getBotName().trim().isEmpty()) {
            return false;
        }

        if (bot.getUserId() == null || bot.getUserId().trim().isEmpty()) {
            return false;
        }

        if (bot.getAccountId() == null || bot.getAccountId().trim().isEmpty()) {
            return false;
        }

        if (bot.getStrategyId() == null || bot.getStrategyId().trim().isEmpty()) {
            return false;
        }

        // multi_direction 模式下 tradingPair 为可选（由信号的 symbol 决定）
        if (!isMultiDirectionMode(bot)) {
            if (bot.getTradingPair() == null || bot.getTradingPair().trim().isEmpty()) {
                return false;
            }
        }

        if (bot.getAllocatedCapital() == null || bot.getAllocatedCapital().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        return true;
    }

    private boolean isMultiDirectionMode(TradingBot bot) {
        if (bot.getConfiguration() == null || bot.getConfiguration().isBlank()) return false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bot.getConfiguration());
            JsonNode mode = root.get("mode");
            return mode != null && "multi_direction".equals(mode.asText());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getBotRunningStatus(String botId) {
        TradingBot bot = getByBotId(botId);
        if (bot == null) {
            return "机器人不存在";
        }

        StringBuilder status = new StringBuilder();
        status.append("机器人状态: ").append(BotStatus.fromCode(bot.getStatus()).getDescription());

        if (bot.getStartTime() != null) {
            status.append(", 启动时间: ").append(bot.getStartTime());
        }

        if (bot.getLastSignalTime() != null) {
            status.append(", 最后信号时间: ").append(bot.getLastSignalTime());
        }

        if (bot.getCurrentCapital() != null && bot.getAllocatedCapital() != null) {
            BigDecimal profit = bot.getCurrentCapital().subtract(bot.getAllocatedCapital());
            status.append(", 收益: ").append(profit);
        }

        return status.toString();
    }

    @Override
    public Map<String, Object> getStatusStats() {
        List<Map<String, Object>> statsList = baseMapper.selectStatusStats();

        // 初始化统计结果
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("running", 0);
        stats.put("created", 0);
        stats.put("paused", 0);
        stats.put("stopped", 0);
        stats.put("error", 0);

        // 计算总数
        int total = statsList.stream()
            .mapToInt(stat -> ((Number) stat.get("count")).intValue())
            .sum();
        stats.put("total", total);

        // 根据状态设置对应的数量
        for (Map<String, Object> stat : statsList) {
            String status = (String) stat.get("status");
            int count = ((Number) stat.get("count")).intValue();

            switch (status) {
                case "RUNNING":
                    stats.put("running", count);
                    break;
                case "CREATED":
                    stats.put("created", count);
                    break;
                case "PAUSED":
                    stats.put("paused", count);
                    break;
                case "STOPPED":
                    stats.put("stopped", count);
                    break;
                case "ERROR":
                    stats.put("error", count);
                    break;
            }
        }

        return stats;
    }
}
