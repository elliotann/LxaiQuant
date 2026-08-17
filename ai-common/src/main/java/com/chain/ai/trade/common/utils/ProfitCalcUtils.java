package com.chain.ai.trade.common.utils;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.entity.dto.OrderProfitItem;
import com.chain.ai.trade.common.entity.dto.ProfitDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 通用收益计算工具
 * 所有收益/手续费/保证金计算统一通过本类，与平台（火币/OKX 等）及合约规格解耦
 */
public final class ProfitCalcUtils {

    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.00045");
    private static final int SCALE = 4;

    private ProfitCalcUtils() {
    }

    // --------------- 手续费 ---------------

    /**
     * 手续费 = 持仓 USDT 价值 × 费率
     */
    public static BigDecimal calcFee(BigDecimal positionUsdt, BigDecimal feeRate) {
        if (positionUsdt == null || positionUsdt.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = feeRate != null ? feeRate : DEFAULT_FEE_RATE;
        return positionUsdt.multiply(rate).setScale(SCALE, RoundingMode.HALF_DOWN);
    }

    // --------------- 持仓收益（按订单维度） ---------------

    /**
     * 获取指定订单的持仓收益（不含手续费）
     *
     * @param platform        平台
     * @param orderSide       方向
     * @param openPrice       开仓价
     * @param remainingVolume 可平仓数量（张）
     * @param nowPrice        当前价
     * @param contractSpec    合约规格（火币时可为 null，内部按火币公式算）
     * @return 持仓收益
     */
    public static BigDecimal getPositionProfit(Exchange platform, OrderSideEnum orderSide,
                                               BigDecimal openPrice, BigDecimal remainingVolume,
                                               BigDecimal nowPrice, ContractSpec contractSpec) {
        return calculatePositionProfit(platform, orderSide, openPrice, remainingVolume, nowPrice,
                contractSpec, false, null);
    }

    /**
     * 获取指定订单的持仓实际收益（含手续费）
     */
    public static BigDecimal getPositionRealProfit(Exchange platform, OrderSideEnum orderSide,
                                                   BigDecimal openPrice, BigDecimal remainingVolume,
                                                   BigDecimal nowPrice, ContractSpec contractSpec,
                                                   BigDecimal feeRate) {
        return calculatePositionProfit(platform, orderSide, openPrice, remainingVolume, nowPrice,
                contractSpec, true, feeRate);
    }

    private static BigDecimal calculatePositionProfit(Exchange platform, OrderSideEnum orderSide,
                                                       BigDecimal openPrice, BigDecimal remainingVolume,
                                                       BigDecimal nowPrice, ContractSpec contractSpec,
                                                       boolean includeFee, BigDecimal feeRate) {
        if (remainingVolume == null || remainingVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return includeFee ? BigDecimal.ZERO : BigDecimal.ZERO;
        }
        if (openPrice == null || nowPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal profit;
        if (Exchange.HUOBI == platform) {
            profit = calcHuobiProfit(orderSide, openPrice, remainingVolume, nowPrice);
        } else {
            ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
            profit = calcOtherPlatformProfit(orderSide, openPrice, remainingVolume, nowPrice, spec);
        }
        if (includeFee) {
            BigDecimal positionUsdt = volumeToUsdt(platform, openPrice, remainingVolume, nowPrice, contractSpec);
            BigDecimal fee = calcFee(positionUsdt, feeRate);
            profit = profit.subtract(fee);
        }
        return profit.setScale(SCALE, RoundingMode.HALF_DOWN);
    }

    // --------------- 按数量计算收益 ---------------

    /**
     * 按数量计算收益（不含手续费）
     */
    public static BigDecimal getProfitByVolume(Exchange platform, OrderSideEnum orderSide,
                                               BigDecimal openPrice, BigDecimal volume,
                                               BigDecimal nowPrice, ContractSpec contractSpec) {
        return calculateProfitByVolume(platform, orderSide, openPrice, volume, nowPrice, contractSpec, false, null);
    }

    /**
     * 按数量计算实际收益（含手续费）
     */
    public static BigDecimal getRealProfitByVolume(Exchange platform, OrderSideEnum orderSide,
                                                    BigDecimal openPrice, BigDecimal volume,
                                                    BigDecimal nowPrice, ContractSpec contractSpec,
                                                    BigDecimal feeRate) {
        Objects.requireNonNull(nowPrice, "当前价格不能为空");
        Objects.requireNonNull(openPrice, "开仓价格不能为空");
        return calculateProfitByVolume(platform, orderSide, openPrice, volume, nowPrice, contractSpec, true, feeRate);
    }

    private static BigDecimal calculateProfitByVolume(Exchange platform, OrderSideEnum orderSide,
                                                      BigDecimal openPrice, BigDecimal volume,
                                                      BigDecimal nowPrice, ContractSpec contractSpec,
                                                      boolean includeFee, BigDecimal feeRate) {
        if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal profit;
        if (Exchange.HUOBI == platform) {
            profit = calcHuobiProfit(orderSide, openPrice, volume, nowPrice);
        } else {
            ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
            profit = calcOtherPlatformProfit(orderSide, openPrice, volume, nowPrice, spec);
        }
        if (includeFee) {
            BigDecimal positionUsdt = volumeToUsdt(platform, openPrice, volume, nowPrice, contractSpec);
            BigDecimal fee = calcFee(positionUsdt, feeRate);
            profit = profit.subtract(fee);
        }
        return profit.setScale(SCALE, RoundingMode.HALF_DOWN);
    }

    // --------------- 火币 / 其他平台 纯收益公式 ---------------

    /** 火币：收益 = (现价-开仓)/开仓 * 数量（多） 或 (开仓-现价)/开仓 * 数量（空） */
    private static BigDecimal calcHuobiProfit(OrderSideEnum orderSide, BigDecimal openPrice,
                                             BigDecimal volume, BigDecimal nowPrice) {
        if (OrderSideEnum.BUY == orderSide) {
            return nowPrice.subtract(openPrice)
                    .divide(openPrice, SCALE, RoundingMode.HALF_DOWN)
                    .multiply(volume);
        } else {
            return openPrice.subtract(nowPrice)
                    .divide(openPrice, SCALE, RoundingMode.HALF_DOWN)
                    .multiply(volume);
        }
    }

    /** 其他平台：收益 = 面值 × 张数 × 乘数 × 价差 */
    private static BigDecimal calcOtherPlatformProfit(OrderSideEnum orderSide, BigDecimal openPrice,
                                                      BigDecimal volume, BigDecimal nowPrice,
                                                      ContractSpec spec) {
        BigDecimal priceDiff = nowPrice.subtract(openPrice);
        if (OrderSideEnum.SELL == orderSide) {
            priceDiff = openPrice.subtract(nowPrice);
        }
        return spec.getContractSize()
                .multiply(volume)
                .multiply(spec.getContractMult())
                .multiply(priceDiff)
                .setScale(SCALE, RoundingMode.HALF_DOWN);
    }

    /** 张数转 USDT 价值（用于手续费等）：火币按 张≈USD 处理；其他 面值×张数×乘数×现价 */
    private static BigDecimal volumeToUsdt(Exchange platform, BigDecimal openPrice, BigDecimal volume,
                                           BigDecimal nowPrice, ContractSpec contractSpec) {
        if (Exchange.HUOBI == platform) {
            return volume.multiply(nowPrice);
        }
        ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
        return spec.getContractSize().multiply(volume).multiply(spec.getContractMult()).multiply(nowPrice);
    }

    // --------------- 汇总多笔订单收益 ---------------

    /**
     * 汇总多笔订单的总收益与持仓收益
     * 需由调用方将订单转为 OrderProfitItem，并约定：closed 且 closedProfit 已填的用 closedProfit；
     * 未完全平仓的用 getPositionRealProfit 计算持仓收益；部分已平仓用 income - charge
     */
    public static ProfitDto getProfit(List<OrderProfitItem> items, BigDecimal marketPrice,
                                      ContractSpecResolver contractSpecResolver,
                                      FeeRateResolver feeRateResolver) {
        ProfitDto result = ProfitDto.builder()
                .totalProfit(BigDecimal.ZERO)
                .positionProfit(BigDecimal.ZERO)
                .build();
        if (items == null || marketPrice == null) {
            return result;
        }
        BigDecimal sumProfit = BigDecimal.ZERO;
        BigDecimal positionProfit = BigDecimal.ZERO;
        for (OrderProfitItem item : items) {
            if (shouldSkipItem(item) || item.isSkip()) {
                continue;
            }
            if (item.isClosed()) {
                BigDecimal closed = item.getClosedProfit() != null ? item.getClosedProfit() : BigDecimal.ZERO;
                if (item.getIncome() != null && item.getCharge() != null) {
                    closed = item.getIncome().subtract(item.getCharge());
                }
                sumProfit = sumProfit.add(closed);
                continue;
            }
            BigDecimal partPos = getPositionRealProfit(
                    item.getPlatform(), item.getOrderSide(),
                    item.getOpenPrice(), item.getRemainingVolume(),
                    marketPrice,
                    contractSpecResolver != null ? contractSpecResolver.resolve(item.getPlatform(), item.getSymbol()) : null,
                    feeRateResolver != null ? feeRateResolver.getFeeRate(item.getPlatform()) : null
            );
            positionProfit = positionProfit.add(partPos);
            BigDecimal partial = (item.getIncome() != null ? item.getIncome() : BigDecimal.ZERO)
                    .subtract(item.getCharge() != null ? item.getCharge() : BigDecimal.ZERO);
            sumProfit = sumProfit.add(partial).add(partPos);
        }
        result.setTotalProfit(sumProfit);
        result.setPositionProfit(positionProfit);
        return result;
    }

    /** 跳过挂单、待成交、已撤销等不参与收益汇总的项 */
    private static boolean shouldSkipItem(OrderProfitItem item) {
        return item == null;
    }

    /**
     * 单笔订单总收益（未完全平仓时：部分已平仓收益 + 持仓实际收益）
     */
    public static ProfitDto getOrderTotalProfit(OrderProfitItem item, BigDecimal marketPrice,
                                                 ContractSpecResolver contractSpecResolver,
                                                 FeeRateResolver feeRateResolver) {
        ProfitDto result = ProfitDto.builder()
                .totalProfit(BigDecimal.ZERO)
                .positionProfit(BigDecimal.ZERO)
                .build();
        if (item == null || marketPrice == null || shouldSkipItem(item) || item.isClosed()) {
            return result;
        }
        BigDecimal positionProfit = getPositionRealProfit(
                item.getPlatform(), item.getOrderSide(),
                item.getOpenPrice(), item.getRemainingVolume(),
                marketPrice,
                contractSpecResolver != null ? contractSpecResolver.resolve(item.getPlatform(), item.getSymbol()) : null,
                feeRateResolver != null ? feeRateResolver.getFeeRate(item.getPlatform()) : null
        );
        BigDecimal partial = (item.getIncome() != null ? item.getIncome() : BigDecimal.ZERO)
                .subtract(item.getCharge() != null ? item.getCharge() : BigDecimal.ZERO);
        result.setPositionProfit(positionProfit);
        result.setTotalProfit(partial.add(positionProfit));
        return result;
    }

    // --------------- 保证金 ---------------

    /**
     * 初始保证金（火币：面值*张数*乘数*标记价/杠杆 → 火币特殊为 张数/100）
     */
    public static BigDecimal getOpenMargin(Exchange platform, String symbol, BigDecimal openPrice,
                                           BigDecimal volume, int leverRate, ContractSpec contractSpec) {
        if (volume == null || openPrice == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (Exchange.HUOBI == platform) {
            return volume.divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_DOWN);
        }
        ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
        return spec.getContractSize()
                .multiply(volume)
                .multiply(spec.getContractMult())
                .multiply(openPrice)
                .divide(BigDecimal.valueOf(leverRate), SCALE, RoundingMode.HALF_DOWN);
    }

    /**
     * 根据 USDT 数量、市价和杠杆计算保证金
     */
    public static BigDecimal getMargin(Exchange platform, String symbol, BigDecimal usdtAmount,
                                       BigDecimal marketPrice, int leverRate, ContractSpec contractSpec) {
        Objects.requireNonNull(platform, "平台不能为空");
        Objects.requireNonNull(symbol, "交易对不能为空");
        Objects.requireNonNull(usdtAmount, "USDT数量不能为空");
        Objects.requireNonNull(marketPrice, "市价不能为空");
        if (usdtAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (Exchange.HUOBI == platform) {
            return usdtAmount.divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_DOWN);
        }
        ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
        BigDecimal vol = usdtAmount.divide(
                spec.getContractSize().multiply(marketPrice),
                0, RoundingMode.DOWN
        );
        return spec.getContractSize()
                .multiply(vol)
                .multiply(spec.getContractMult())
                .multiply(marketPrice)
                .divide(BigDecimal.valueOf(leverRate), SCALE, RoundingMode.HALF_DOWN);
    }

    /**
     * 根据张数、市价和杠杆计算保证金
     */
    public static BigDecimal getMarginByVolume(Exchange platform, String symbol, BigDecimal volume,
                                              BigDecimal marketPrice, int leverRate, ContractSpec contractSpec) {
        Objects.requireNonNull(platform, "平台不能为空");
        Objects.requireNonNull(symbol, "交易对不能为空");
        Objects.requireNonNull(volume, "张数不能为空");
        Objects.requireNonNull(marketPrice, "市价不能为空");
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (Exchange.HUOBI == platform) {
            return volume.multiply(marketPrice).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_DOWN);
        }
        ContractSpec spec = contractSpec != null ? contractSpec : ContractSpec.defaultSpec();
        return spec.getContractSize()
                .multiply(volume)
                .multiply(spec.getContractMult())
                .multiply(marketPrice)
                .divide(BigDecimal.valueOf(leverRate), SCALE, RoundingMode.HALF_DOWN);
    }

    // --------------- 解析器接口（由业务模块实现并注入） ---------------

    /** 根据平台、交易对解析合约规格（可从 Redis/配置读取） */
    @FunctionalInterface
    public interface ContractSpecResolver {
        ContractSpec resolve(Exchange platform, String symbol);
    }

    /** 根据平台解析手续费率 */
    @FunctionalInterface
    public interface FeeRateResolver {
        BigDecimal getFeeRate(Exchange platform);
    }
}
