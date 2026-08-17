package com.chain.ai.trade.common.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 交易相关工具类
 * 提供交易计算相关的工具方法
 *
 * @author system
 */
@Slf4j
public class TradingUtil {

    /**
     * 将USDT金额转换为合约张数
     * 公式：合约张数 = (USDT金额 × 杠杆倍数) / (当前价格 × 合约面值)
     *
     * @param usdtAmount USDT金额
     * @param currentPrice 当前价格
     * @param leverage 杠杆倍数
     * @param contractSize 合约面值（如ETH: 0.1, BTC: 0.01等）
     * @return 合约张数
     */
    public static long convertUsdtToContractSize(double usdtAmount, double currentPrice, double leverage, double contractSize) {
        if (currentPrice <= 0) {
            log.warn("当前价格无效: {}, 使用原始金额", currentPrice);
            return 0;
        }
        if (contractSize <= 0) {
            log.warn("合约面值无效: {}, 使用默认值 1.0", contractSize);
            contractSize = 1.0;
        }
        if (leverage <= 0) {
            log.warn("杠杆倍数无效: {}, 使用默认值 1.0", leverage);
            leverage = 1.0;
        }
        
        // 合约张数 = (USDT金额 × 杠杆) / (当前价格 × 合约面值)
        double contractQuantity = (usdtAmount * leverage) / (currentPrice * contractSize);
        log.info("USDT转合约张数: {} USDT × {}倍杠杆 ÷ ({}价格 × {}面值) = {} 张",
                usdtAmount, leverage, currentPrice, contractSize, contractQuantity);
        long finalQuantity = (long) Math.floor(contractQuantity);

        return finalQuantity;
    }

    /**
     * 张数转USDT
     * 公式：USDT金额 = (合约张数 × 当前价格 × 合约面值) / 杠杆倍数
     *
     * @param contractQuantity 合约张数
     * @param currentPrice 当前价格
     * @param leverage 杠杆倍数
     * @param contractSize 合约面值（如ETH: 0.1, BTC: 0.01等）
     * @return USDT金额
     */
    public static double contractToUsdt(double contractQuantity, double currentPrice, double leverage, double contractSize) {
        if (currentPrice <= 0) {
            log.warn("当前价格无效: {}", currentPrice);
            return 0.0;
        }
        if (contractSize <= 0) {
            log.warn("合约面值无效: {}, 使用默认值 1.0", contractSize);
            contractSize = 1.0;
        }
        if (leverage <= 0) {
            log.warn("杠杆倍数无效: {}, 使用默认值 1.0", leverage);
            leverage = 1.0;
        }
        
        // USDT金额 = (合约张数 × 当前价格 × 合约面值) / 杠杆倍数
        double usdtAmount = (contractQuantity * currentPrice * contractSize) / leverage;
        log.info("张数转USDT: {} 张 × {}价格 × {}面值 ÷ {}倍杠杆 = {} USDT",
                contractQuantity, currentPrice, contractSize, leverage, usdtAmount);
        return usdtAmount;
    }
}

