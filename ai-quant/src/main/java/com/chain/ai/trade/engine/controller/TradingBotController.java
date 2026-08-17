package com.chain.ai.trade.engine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.strategy.entity.dos.BotParameter;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.enums.BotStatus;
import com.chain.ai.trade.engine.strategy.service.IBotParameterService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 交易机器人管理控制器
 * 提供交易机器人的后台管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/trading-bots")
@RequiredArgsConstructor
public class TradingBotController {

    private final ITradingBotService tradingBotService;
    private final IBotParameterService botParameterService;

    /**
     * 分页查询交易机器人
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBotsPaged(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer limit,
            @RequestParam(required = false) String botName,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String tradingPair,
            Authentication auth) {

        try {
            // 非管理员只能查自己的机器人，管理员可查全部
            String finalUserId = userId;
            if (auth != null && !"ADMIN".equals(auth.getDetails())) {
                finalUserId = (String) auth.getPrincipal();
            }
            Page<TradingBot> pageParam = new Page<>(page, limit);
            IPage<TradingBot> result = tradingBotService.pageBots(
                pageParam, botName, finalUserId, exchange, status, strategyId, accountId, tradingPair);

            List<TradingBot> records = new ArrayList<>(result.getRecords());

            Map<String, Object> data = Map.of(
                "records", records,
                "total", (long) records.size(),
                "current", result.getCurrent(),
                "size", result.getSize(),
                "pages", result.getPages()
            );

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("分页查询交易机器人失败", e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID查询交易机器人
     */
    @GetMapping("/{botId}")
    public ResponseEntity<ApiResponse<TradingBot>> getBot(@PathVariable String botId) {
        try {
            TradingBot bot = tradingBotService.getByBotId(botId);
            if (bot == null) {
                return ResponseEntity.ok(ApiResponse.error("机器人不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(bot));
        } catch (Exception e) {
            log.error("查询交易机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 创建交易机器人
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TradingBot>> createBot(@RequestBody TradingBot bot) {
        try {
            TradingBot createdBot = tradingBotService.createBot(bot);
            return ResponseEntity.ok(ApiResponse.success(createdBot));
        } catch (Exception e) {
            log.error("创建交易机器人失败", e);
            return ResponseEntity.ok(ApiResponse.error("创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新交易机器人
     * 只有机器人创建者或管理员可以更新
     */
    @PutMapping("/{botId}")
    public ResponseEntity<ApiResponse<Boolean>> updateBot(
            @PathVariable String botId,
            @RequestBody TradingBot bot,
            Authentication auth) {
        try {
            // 先查询机器人
            TradingBot existingBot = tradingBotService.getByBotId(botId);
            if (existingBot == null) {
                return ResponseEntity.ok(ApiResponse.error("机器人不存在"));
            }

            // 校验操作权限：只有机器人创建者或管理员可修改
            String currentUserId = (String) auth.getPrincipal();
            boolean isAdmin = "ADMIN".equals(auth.getDetails());
            boolean isOwnBot = isAdmin || currentUserId.equals(existingBot.getCreatedBy());
            if (!isOwnBot) {
                // 购买的机器人：只允许修改投入本金，其他字段不允许改
                if (bot.getAllocatedCapital() != null) {
                    existingBot.setAllocatedCapital(bot.getAllocatedCapital());
                }
                if (bot.getCurrentCapital() != null) {
                    existingBot.setCurrentCapital(bot.getCurrentCapital());
                }
                boolean result = tradingBotService.updateBot(existingBot);
                return ResponseEntity.ok(ApiResponse.success(result));
            }

            // 只更新传入的字段，保留其他字段不变
            if (bot.getBotName() != null) {
                existingBot.setBotName(bot.getBotName());
            }
            if (bot.getUserId() != null) {
                existingBot.setUserId(bot.getUserId());
            }
            if (bot.getExchange() != null) {
                existingBot.setExchange(bot.getExchange());
            }
            if (bot.getAccountId() != null) {
                existingBot.setAccountId(bot.getAccountId());
            }
            if (bot.getStrategyId() != null) {
                existingBot.setStrategyId(bot.getStrategyId());
            }
            if (bot.getTradingPair() != null) {
                existingBot.setTradingPair(bot.getTradingPair());
            }
            if (bot.getAllocatedCapital() != null) {
                existingBot.setAllocatedCapital(bot.getAllocatedCapital());
            }
            if (bot.getCurrentCapital() != null) {
                existingBot.setCurrentCapital(bot.getCurrentCapital());
            }
            if (bot.getEnabled() != null) {
                existingBot.setEnabled(bot.getEnabled());
            }
            if (bot.getConfiguration() != null) {
                existingBot.setConfiguration(bot.getConfiguration());
            }
            if (bot.getRemark() != null) {
                existingBot.setRemark(bot.getRemark());
            }

            boolean result = tradingBotService.updateBot(existingBot);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("更新交易机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除交易机器人
     */
    @DeleteMapping("/{botId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteBot(@PathVariable String botId) {
        try {
            boolean result = tradingBotService.deleteBot(botId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("删除交易机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }

    /**
     * 启动机器人
     */
    @PostMapping("/{botId}/start")
    public ResponseEntity<ApiResponse<Boolean>> startBot(@PathVariable String botId) {
        try {
            boolean result = tradingBotService.startBot(botId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("启动机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("启动失败: " + e.getMessage()));
        }
    }

    /**
     * 停止机器人
     */
    @PostMapping("/{botId}/stop")
    public ResponseEntity<ApiResponse<Boolean>> stopBot(@PathVariable String botId) {
        try {
            boolean result = tradingBotService.stopBot(botId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("停止机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("停止失败: " + e.getMessage()));
        }
    }

    /**
     * 暂停机器人
     */
    @PostMapping("/{botId}/pause")
    public ResponseEntity<ApiResponse<Boolean>> pauseBot(@PathVariable String botId) {
        try {
            boolean result = tradingBotService.pauseBot(botId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("暂停机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("暂停失败: " + e.getMessage()));
        }
    }

    /**
     * 恢复机器人
     */
    @PostMapping("/{botId}/resume")
    public ResponseEntity<ApiResponse<Boolean>> resumeBot(@PathVariable String botId) {
        try {
            boolean result = tradingBotService.resumeBot(botId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("恢复机器人失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("恢复失败: " + e.getMessage()));
        }
    }

    /**
     * 更新机器人状态
     */
    @PostMapping("/{botId}/status")
    public ResponseEntity<ApiResponse<Boolean>> updateBotStatus(
            @PathVariable String botId, @RequestParam String status) {
        try {
            boolean result = tradingBotService.updateBotStatus(botId, status);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("更新机器人状态失败: botId={}, status={}", botId, status, e);
            return ResponseEntity.ok(ApiResponse.error("更新状态失败: " + e.getMessage()));
        }
    }

    /**
     * 批量更新机器人状态
     */
    @PostMapping("/batch/status")
    public ResponseEntity<ApiResponse<Boolean>> batchUpdateStatus(
            @RequestParam List<String> botIds, @RequestParam String status) {
        try {
            boolean result = tradingBotService.batchUpdateStatus(botIds, status);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("批量更新机器人状态失败: status={}", status, e);
            return ResponseEntity.ok(ApiResponse.error("批量更新失败: " + e.getMessage()));
        }
    }

    /**
     * 根据用户ID查询机器人列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<TradingBot>>> getBotsByUserId(@PathVariable String userId) {
        try {
            List<TradingBot> bots = tradingBotService.listByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success(bots));
        } catch (Exception e) {
            log.error("查询用户机器人失败: userId={}", userId, e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据策略ID查询机器人列表
     */
    @GetMapping("/strategy/{strategyId}")
    public ResponseEntity<ApiResponse<List<TradingBot>>> getBotsByStrategyId(@PathVariable String strategyId) {
        try {
            List<TradingBot> bots = tradingBotService.listByStrategyId(strategyId);
            return ResponseEntity.ok(ApiResponse.success(bots));
        } catch (Exception e) {
            log.error("查询策略机器人失败: strategyId={}", strategyId, e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据账户ID查询机器人列表
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<TradingBot>>> getBotsByAccountId(@PathVariable String accountId) {
        try {
            List<TradingBot> bots = tradingBotService.listByAccountId(accountId);
            return ResponseEntity.ok(ApiResponse.success(bots));
        } catch (Exception e) {
            log.error("查询账户机器人失败: accountId={}", accountId, e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据状态查询机器人列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<TradingBot>>> getBotsByStatus(@PathVariable String status) {
        try {
            List<TradingBot> bots = tradingBotService.listByStatus(status);
            return ResponseEntity.ok(ApiResponse.success(bots));
        } catch (Exception e) {
            log.error("查询状态机器人失败: status={}", status, e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 更新机器人资金
     */
    @PostMapping("/{botId}/capital")
    public ResponseEntity<ApiResponse<Boolean>> updateCapital(
            @PathVariable String botId,
            @RequestParam BigDecimal allocatedCapital,
            @RequestParam BigDecimal currentCapital) {
        try {
            boolean result = tradingBotService.updateCapital(botId, allocatedCapital, currentCapital);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("更新机器人资金失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("更新资金失败: " + e.getMessage()));
        }
    }

    /**
     * 更新机器人统计信息
     */
    @PostMapping("/{botId}/statistics")
    public ResponseEntity<ApiResponse<Boolean>> updateStatistics(
            @PathVariable String botId, @RequestParam String statistics) {
        try {
            boolean result = tradingBotService.updateStatistics(botId, statistics);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("更新机器人统计信息失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("更新统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取机器人运行状态详情
     */
    @GetMapping("/{botId}/running-status")
    public ResponseEntity<ApiResponse<String>> getBotRunningStatus(@PathVariable String botId) {
        try {
            String status = tradingBotService.getBotRunningStatus(botId);
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("获取机器人运行状态失败: botId={}", botId, e);
            return ResponseEntity.ok(ApiResponse.error("获取状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取机器人状态统计
     */
    @GetMapping("/stats/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBotStatusStats() {
        try {
            Map<String, Object> stats = tradingBotService.getStatusStats();
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("获取机器人状态统计失败", e);
            return ResponseEntity.ok(ApiResponse.error("获取统计失败: " + e.getMessage()));
        }
    }

    /**
     * 保存机器人某分组的所有参数
     * group: risk_control, add_position_config 等
     * 只有机器人创建者或管理员可以修改参数
     */
    @PutMapping("/{botId}/parameters/{group}")
    public ResponseEntity<ApiResponse<Void>> saveBotParameters(
            @PathVariable String botId,
            @PathVariable String group,
            @RequestBody Map<String, String> params,
            Authentication auth) {
        try {
            // 校验操作权限：只有机器人创建者或管理员可修改参数
            String currentUserId = (String) auth.getPrincipal();
            boolean isAdmin = "ADMIN".equals(auth.getDetails());
            if (!isAdmin) {
                TradingBot bot = tradingBotService.getByBotId(botId);
                if (bot == null) {
                    return ResponseEntity.ok(ApiResponse.error("机器人不存在"));
                }
                if (!currentUserId.equals(bot.getCreatedBy())) {
                    return ResponseEntity.ok(ApiResponse.error("无权修改该机器人的参数"));
                }
            }
            botParameterService.saveParameters(botId, group, params);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            log.error("保存机器人参数失败: botId={}, group={}", botId, group, e);
            return ResponseEntity.ok(ApiResponse.error("保存参数失败: " + e.getMessage()));
        }
    }

    /**
     * 查询机器人某分组的所有参数
     */
    @GetMapping("/{botId}/parameters/{group}")
    public ResponseEntity<ApiResponse<List<BotParameter>>> getBotParameters(
            @PathVariable String botId,
            @PathVariable String group) {
        try {
            List<BotParameter> params = botParameterService.listByBotIdAndGroup(botId, group);
            return ResponseEntity.ok(ApiResponse.success(params));
        } catch (Exception e) {
            log.error("查询机器人参数失败: botId={}, group={}", botId, group, e);
            return ResponseEntity.ok(ApiResponse.error("查询参数失败: " + e.getMessage()));
        }
    }
}
