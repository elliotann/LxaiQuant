package com.chain.ai.trade.engine2.backtest;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.ContractSpecUtils;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.strategy.StrategyFactory;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * V2 回测服务
 * <p>
 * 负责构建回测配置、协调回测执行流程。
 * 回测参数基于机器人配置驱动，支持前端入参覆盖和默认值兜底。
 * <p>
 * 资金相关概念：
 * - initialCapital（初始总资产）← 机器人当前资金 TradingBot.currentCapital
 * - positionAmount（分配资金） ← 机器人的基础仓位资金 TradingBot.allocatedCapital
 * - leverage（杠杆）  ← 机器人配置 JSON TradingBot.configuration.leverage
 */
@Slf4j
@Service("backtestServiceV2")
@RequiredArgsConstructor
public class BacktestService {

    @Autowired(required = false)
    private RedisCache redisCache;

    @Autowired(required = false)
    private ITradingBotService tradingBotService;

    @Autowired(required = false)
    private StrategyFactory strategyFactory;

    /** JSON 中的加仓配置键名（与 BacktestConfig 字段名一致） */
    private static final String KEY_MAX_ADD_POSITIONS = "maxAddPositions";
    private static final String KEY_ADD_POS_ON_PROFIT_PCT = "addPosOnProfitPct";
    private static final String KEY_ADD_POS_ON_LOSS_PCT = "addPosOnLossPct";
    private static final String KEY_ADD_POS_ON_PROFIT_GAP_PCT = "addPosOnProfitGapPct";
    private static final String KEY_ADD_POS_ON_LOSS_GAP_PCT = "addPosOnLossGapPct";
    private static final String KEY_PROFIT_ADD_EMA_ENABLED = "profitAddEmaTrendEnabled";
    private static final String KEY_PROFIT_ADD_EMA_FAST = "profitAddEmaFastPeriod";
    private static final String KEY_PROFIT_ADD_EMA_SLOW = "profitAddEmaSlowPeriod";
    private static final String KEY_PROFIT_ADD_EMA_BARS = "profitAddEmaMinConsecutiveBars";

    /**
     * 构建回测配置
     * <p>
     * 参数优先级：前端入参 > 机器人配置 > 默认值
     */
    public BacktestConfig buildConfig(BacktestRequest request) {
        // 1. 查询机器人配置（根据 robotId）
        TradingBot bot = null;
        if (request.getRobotId() != null && tradingBotService != null) {
            try {
                bot = tradingBotService.getByBotId(request.getRobotId());
                log.debug("已加载机器人配置: robotId={}, currentCapital={}, allocatedCapital={}",
                        request.getRobotId(),
                        bot != null ? bot.getCurrentCapital() : null,
                        bot != null ? bot.getAllocatedCapital() : null);
            } catch (Exception e) {
                log.warn("查询机器人配置失败: robotId={}", request.getRobotId(), e);
            }
        }

        // 2. 初始总资产（USDT）：前端入参 > 机器人当前资金 > 默认值
        BigDecimal initialCapital = firstOf(
                request.getInitialCapital(),
                bot != null ? bot.getCurrentCapital() : null,
                BigDecimal.valueOf(10000));

        // 3. 分配资金（USDT）：前端入参 > 机器人分配资金 > 默认值
        BigDecimal positionAmount = firstOf(
                request.getPositionAmount(),
                bot != null ? bot.getAllocatedCapital() : null,
                BigDecimal.valueOf(1000));

        // 4. 杠杆：前端入参 > 机器人配置.configuration.leverage > 默认值
        int leverage = firstOf(
                request.getLeverage(),
                bot != null ? extractLeverageFromConfig(bot.getConfiguration()) : null,
                1);

        // 5. 滑点：前端入参 > 默认值
        double slippage = Optional.ofNullable(request.getSlippage())
                .orElse(0.0);

        // 6. 手续费率：前端入参 > 默认值（无手续费）
        BigDecimal commissionRate = Optional.ofNullable(request.getCommissionRate())
                .orElse(BigDecimal.ZERO);

        // 7. 交易所平台：机器人交易所 > 默认值 OKX
        Exchange platform = resolvePlatform(bot != null ? bot.getExchange() : null);

        // 8. 合约规格（面值、乘数）：从品种配置查询
        ContractSpec contractSpec = resolveContractSpec(request.getSymbol(), platform);

        // 9. 加减仓配置：从机器人 configuration JSON 提取
        BacktestConfig.BacktestConfigBuilder builder = BacktestConfig.builder()
                .symbol(request.getSymbol())
                .platform(platform)
                .initialCapital(initialCapital)
                .positionAmount(positionAmount)
                .leverage(leverage)
                .slippage(slippage)
                .commissionRate(commissionRate)
                .contractSize(contractSpec.getContractSize())
                .contractSpec(contractSpec)
                .maxAddPositions(3)
                .warmupPeriod(50);

       /* if (bot != null && bot.getConfiguration() != null) {
            try {
                JSONObject cfg = JSONUtil.parseObj(bot.getConfiguration());
                extractScaleInConfig(cfg, builder);
            } catch (Exception e) {
                log.warn("解析机器人加仓配置失败: {}", e.getMessage());
            }
        }*/

        // 无机器人配置时，从策略级配置兜底
        if (strategyFactory != null && request.getStrategyId() != null) {
            Map<String, Object> strategyCfg = strategyFactory.loadAddPositionConfig(request.getStrategyId());
            if (strategyCfg != null && !strategyCfg.isEmpty()) {
                JSONObject cfg = JSONUtil.parseObj(strategyCfg);
                extractScaleInConfig(cfg, builder);
            }
        }

        return builder.build();
    }

    /** 取第一个非空值 */
    private static <T> T firstOf(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** 从机器人 configuration JSON 中提取杠杆 */
    private Integer extractLeverageFromConfig(String configuration) {
        if (configuration == null || configuration.isBlank()) return null;
        try {
            JSONObject config = JSONUtil.parseObj(configuration);
            return config.getInt("leverage");
        } catch (Exception e) {
            log.warn("解析机器人配置JSON失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从机器人 configuration JSON 中提取加仓配置并写入 Builder。
     * <p>
     * 键名与 BacktestConfig 字段名一致，读取规则：
     * - 仅当 JSON 中存在该键时覆盖 Builder，否则保持 Builder 默认值。
     * - 此配置在后端默认关闭（maxAddPositions=0），需用户在机器人配置中开启。
     */
    private void extractScaleInConfig(JSONObject cfg, BacktestConfig.BacktestConfigBuilder builder) {
        if (cfg.containsKey(KEY_MAX_ADD_POSITIONS)) {
            builder.maxAddPositions(cfg.getInt(KEY_MAX_ADD_POSITIONS));
        }
        setIfNonNull(builder::addPosOnProfitPct, normalizePercent(cfg.getDouble(KEY_ADD_POS_ON_PROFIT_PCT)));
        setIfNonNull(builder::addPosOnLossPct, normalizePercent(cfg.getDouble(KEY_ADD_POS_ON_LOSS_PCT)));
        setIfNonNull(builder::addPosOnProfitGapPct, normalizePercent(cfg.getDouble(KEY_ADD_POS_ON_PROFIT_GAP_PCT)));
        setIfNonNull(builder::addPosOnLossGapPct, normalizePercent(cfg.getDouble(KEY_ADD_POS_ON_LOSS_GAP_PCT)));
        if (cfg.containsKey(KEY_PROFIT_ADD_EMA_ENABLED)) {
            builder.profitAddEmaTrendEnabled(cfg.getBool(KEY_PROFIT_ADD_EMA_ENABLED));
        }
        if (cfg.containsKey(KEY_PROFIT_ADD_EMA_FAST)) {
            builder.profitAddEmaFastPeriod(cfg.getInt(KEY_PROFIT_ADD_EMA_FAST));
        }
        if (cfg.containsKey(KEY_PROFIT_ADD_EMA_SLOW)) {
            builder.profitAddEmaSlowPeriod(cfg.getInt(KEY_PROFIT_ADD_EMA_SLOW));
        }
        if (cfg.containsKey(KEY_PROFIT_ADD_EMA_BARS)) {
            builder.profitAddEmaMinConsecutiveBars(cfg.getInt(KEY_PROFIT_ADD_EMA_BARS));
        }
    }

    /**
     * 归一化百分比参数。
     * <p>
     * 前端传值可以是百分比（如 5 表示 5%）或小数（如 0.05 表示 5%），
     * 统一归一化为小数形式（0.05）。
     */
    private static Double normalizePercent(Double v) {
        if (v == null || v <= 0) return null;
        return v / 100.0;
    }

    /** 仅当值非空时调用 consumer */
    private static <T> void setIfNonNull(java.util.function.Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /** 解析交易所：字符串转 Exchange 枚举，空则默认 OKX */
    private Exchange resolvePlatform(String exchangeStr) {
        if (exchangeStr == null || exchangeStr.isBlank()) return Exchange.OKX;
        try {
            return Exchange.valueOf(exchangeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知交易所: {}, 使用默认值 OKX", exchangeStr);
            return Exchange.OKX;
        }
    }

    /** 解析合约规格 */
    private ContractSpec resolveContractSpec(String symbol, Exchange platform) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("交易对为空，使用默认合约规格");
            return ContractSpec.defaultSpec();
        }
        try {
            return ContractSpecUtils.getContractSpec(redisCache, platform, symbol);
        } catch (Exception e) {
            log.warn("获取合约规格失败，使用默认值: symbol={}, error={}", symbol, e.getMessage());
            return ContractSpec.defaultSpec();
        }
    }
}
