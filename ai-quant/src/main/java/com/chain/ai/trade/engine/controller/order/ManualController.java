package com.chain.ai.trade.engine.controller.order;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.utils.TradingUtil;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.service.TradeOrderServiceAdapter;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.member.dto.AccountSecrets;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manual-orders")
@RequiredArgsConstructor
@Slf4j
public class ManualController {

    private final ITradeOrderService tradeOrderService;
    private final RedisCache redisCache;
    private final TradeOrderServiceAdapter orderAdapter;
    private final ITradingAccountService tradingAccountService;
    private final AccountSecretsService accountSecretsService;
    private final ITradingBotService tradingBotService;

    @PostMapping("/open")
    public ApiResponse<Map<String, Object>> open(@RequestBody ManualOpenRequest req) {
        try {
            if (req == null) {
                return ApiResponse.error("请求体不能为空");
            }
            if (req.getAccountId() == null || req.getAccountId().isBlank()) {
                return ApiResponse.error("accountId不能为空");
            }
            if (req.getSymbol() == null || req.getSymbol().isBlank()) {
                return ApiResponse.error("symbol不能为空");
            }
            if (req.getSide() == null || req.getSide().isBlank()) {
                return ApiResponse.error("side不能为空");
            }
            if (req.getOrderType() == null || req.getOrderType().isBlank()) {
                return ApiResponse.error("orderType不能为空");
            }
            if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.error("quantity必须大于0");
            }

            String side = req.getSide().trim().toUpperCase();
            if (!side.equals("LONG") && !side.equals("SHORT") && !side.equals("BUY") && !side.equals("SELL")) {
                return ApiResponse.error("side必须为LONG/SHORT或BUY/SELL");
            }
            String mappedSide = (side.equals("LONG") || side.equals("BUY")) ? "BUY" : "SELL";

            String orderTypeStr = req.getOrderType().trim().toUpperCase();
            if (!orderTypeStr.equals("MARKET") && !orderTypeStr.equals("LIMIT")) {
                return ApiResponse.error("orderType必须为MARKET或LIMIT");
            }
            boolean isLimit = orderTypeStr.equals("LIMIT");
            if (isLimit) {
                if (req.getLimitPrice() == null || req.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    return ApiResponse.error("限价单必须提供有效的limitPrice");
                }
            }

            // TradFi 品种（纯字母如 EURUSD）允许小数手数，不强制整数
            boolean isTradfiSymbol = req.getSymbol() != null && req.getSymbol().matches("^[A-Za-z]{4,12}$");
            BigDecimal flooredQty = isTradfiSymbol
                    ? req.getQuantity()
                    : req.getQuantity().setScale(0, java.math.RoundingMode.FLOOR);
            if (!isTradfiSymbol && flooredQty.compareTo(BigDecimal.ONE) < 0) {
                return ApiResponse.error("数量不足1，已拦截");
            }

            // 提前查询账号，获取交易所信息（优先用前端传入的，否则从账号获取）
            TradingAccount account = tradingAccountService.getById(req.getAccountId());
            if (account == null) {
                return ApiResponse.error("账户不存在");
            }
            if (account.getBindStatus() == TradingAccount.BindStatus.UNBIND) {
                return ApiResponse.error("账户未绑定");
            }
            if (!account.isApiEnabled()) {
                return ApiResponse.error("账户API未启用");
            }
            Exchange exchange = req.getExchange() != null
                    ? Exchange.valueOf(req.getExchange().toUpperCase()) : account.getMemberPlatform();

            if (req.getSymbol() != null && req.getSymbol().contains("-SWAP")) {
                BigDecimal entryPrice = req.getEntryPrice();
                if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    if (req.getMetadata() != null && req.getMetadata().get("klinePrice") instanceof Number) {
                        entryPrice = new BigDecimal(req.getMetadata().get("klinePrice").toString());
                    }
                }
                if (entryPrice != null && entryPrice.compareTo(BigDecimal.ZERO) > 0) {
                    com.chain.ai.trade.common.entity.dto.ContractSpec spec =
                            com.chain.ai.trade.common.utils.ContractSpecUtils.getContractSpec(
                                    redisCache, exchange, req.getSymbol());
                    if (spec == null) {
                        spec = com.chain.ai.trade.common.entity.dto.ContractSpec.defaultSpec();
                    }
                    double contractSize = spec.getContractSize() != null
                            ? spec.getContractSize().doubleValue() : 1.0;
                    int lev = req.getLeverage() != null ? req.getLeverage() : 1;
                    long contracts = TradingUtil.convertUsdtToContractSize(
                            flooredQty.doubleValue(), entryPrice.doubleValue(), lev, contractSize);
                    flooredQty = BigDecimal.valueOf(contracts);
                    if (flooredQty.compareTo(BigDecimal.ONE) < 0) {
                        return ApiResponse.error("金额计算后不足1张，已拦截");
                    }
                }
            }

            Integer leverage = req.getLeverage();
            if (leverage != null && (leverage < 1 || leverage > 125)) {
                return ApiResponse.error("leverage必须在1-125之间");
            }

            String robotId = req.getRobotId();
            if (robotId != null && !robotId.isBlank()) {
                TradingBot bot = tradingBotService.getByBotId(robotId.trim());
                if (bot == null) {
                    return ApiResponse.error("机器人不存在");
                }
                String botAccountId = bot.getAccountId();
                if (botAccountId != null && !botAccountId.isBlank() && !botAccountId.equals(req.getAccountId())) {
                    return ApiResponse.error("机器人账户不匹配");
                }
                BigDecimal currentCapital = bot.getCurrentCapital();
                if (currentCapital != null && currentCapital.compareTo(BigDecimal.ZERO) > 0
                        && flooredQty.compareTo(currentCapital) > 0) {
                    return ApiResponse.error("下单金额超过机器人当前资金");
                }
            }

            AccountSecrets secrets;
            try {
                secrets = accountSecretsService.getAccountSecrets(account.getId());
            } catch (Exception ex) {
                return ApiResponse.error("账户API配置无效: " + ex.getMessage());
            }

            String apiKey = req.getApiKey() != null && !req.getApiKey().isBlank()
                    ? req.getApiKey() : (secrets.getApiKey() != null ? new String(secrets.getApiKey()) : null);
            String secretKey = req.getSecretKey() != null && !req.getSecretKey().isBlank()
                    ? req.getSecretKey() : (secrets.getApiSecret() != null ? new String(secrets.getApiSecret()) : null);
            String passphrase = req.getPassphrase() != null && !req.getPassphrase().isBlank()
                    ? req.getPassphrase() : (secrets.getPassphrase() != null ? new String(secrets.getPassphrase()) : null);

            com.chain.ai.trade.common.entity.param.TradingStrategyParams.TradingStrategyParamsBuilder builder =
                    com.chain.ai.trade.common.entity.param.TradingStrategyParams.builder()
                            .accountId(req.getAccountId())
                            .symbol(req.getSymbol())
                            .side(mappedSide)
                            .amount(flooredQty)
                            .orderTime(new Date())
                            .leverage(req.getLeverage())
                            .memberPlatform(account.getMemberPlatform())
                            .simulated(account.getSimulated())
                            .apiKey(apiKey)
                            .secretKey(secretKey)
                            .passphrase(passphrase);

            if (isLimit) {
                builder.price(req.getLimitPrice());
            }
            // 从 metadata 中提取止盈/止损价
            if (req.getMetadata() != null) {
                Object tpObj = req.getMetadata().get("tpPrice");
                Object slObj = req.getMetadata().get("slPrice");
                if (tpObj instanceof Number) {
                    builder.takeProfitPrice(new BigDecimal(tpObj.toString()));
                }
                if (slObj instanceof Number) {
                    builder.stopLossPrice(new BigDecimal(slObj.toString()));
                }
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put("source", "MANUAL");
            if (req.getChannel() != null) extras.put("manualChannel", req.getChannel());
            if (req.getRequestId() != null) extras.put("requestId", req.getRequestId());
            if (req.getTimeInForce() != null) extras.put("timeInForce", req.getTimeInForce());
            if (req.getMetadata() != null) extras.put("metadata", req.getMetadata());
            extras.put("orderType", orderTypeStr);
            if (req.getRobotId() != null) builder.robotId(req.getRobotId());
            builder.additionalParams(extras);

            // 检查是否有同方向持仓，有则走加仓逻辑
            List<com.chain.ai.trade.order.entity.vo.OrderVO> existingPositions =
                    tradeOrderService.getPositionOrders(req.getAccountId(), req.getSymbol());
            boolean hasSameSidePosition = existingPositions != null && existingPositions.stream()
                    .anyMatch(o -> o != null && o.getOrderSide() != null
                            && mappedSide.equalsIgnoreCase(o.getOrderSide()));

            if (hasSameSidePosition) {
                boolean suppOk = tradeOrderService.suppOrder(builder.build());
                secrets.clear();
                if (!suppOk) {
                    return ApiResponse.error("加仓失败");
                }
                Map<String, Object> result = new HashMap<>();
                result.put("result", "supplemented");
                return ApiResponse.success("手动加仓已触发", result);
            }

            String idemKey = buildIdemKey(req.getAccountId(), req.getSymbol(), mappedSide, orderTypeStr, req.getRequestId());
            if (req.getRequestId() != null) {
                Object existed = redisCache.get(idemKey);
                if (existed instanceof String && !((String) existed).isBlank()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("orderId", existed);
                    return ApiResponse.success("重复请求，返回已有订单", result);
                }
            }

            String orderId = tradeOrderService.createOrder(builder.build());
            secrets.clear();
            if (orderId == null || orderId.isBlank()) {
                return ApiResponse.error("创建订单失败");
            }
            if (req.getRequestId() != null) {
                redisCache.put(idemKey, orderId, 600L);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            return ApiResponse.success("手动开仓已创建", result);
        } catch (Exception e) {
            log.error("手动开仓请求处理失败", e);
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (isInsufficientMargin(msg)) {
                return ApiResponse.error(400, "账户保证金不足（模拟盘也需要有 USDT 余额）。请在 OKX 模拟盘入金/领取体验金，或降低张数/提高杠杆后重试。");
            }
            return ApiResponse.error("请求处理失败: " + msg);
        }
    }

    private boolean isInsufficientMargin(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return (m.contains("insufficient") && m.contains("margin")) || m.contains("insufficient usdt margin");
    }

    private String buildIdemKey(String accountId, String symbol, String side, String orderType, String requestId) {
        return "manual:open:" + (accountId == null ? "-" : accountId) + ":" + (symbol == null ? "-" : symbol)
                + ":" + (side == null ? "-" : side) + ":" + (orderType == null ? "-" : orderType)
                + ":" + (requestId == null ? "-" : requestId);
    }

    @PostMapping("/close")
    public ApiResponse<Map<String, Object>> close(@RequestBody ManualCloseRequest req) {
        try {
            if (req == null) {
                return ApiResponse.error("请求体不能为空");
            }
            if (req.getAccountId() == null || req.getAccountId().isBlank()) {
                return ApiResponse.error("accountId不能为空");
            }
            if (req.getSymbol() == null || req.getSymbol().isBlank()) {
                return ApiResponse.error("symbol不能为空");
            }
            if (req.getSide() == null || req.getSide().isBlank()) {
                return ApiResponse.error("side不能为空");
            }

            String side = req.getSide().trim().toUpperCase();
            if (!side.equals("LONG") && !side.equals("SHORT") && !side.equals("BUY") && !side.equals("SELL")) {
                return ApiResponse.error("side必须为LONG/SHORT或BUY/SELL");
            }
            String mappedSide = (side.equals("LONG") || side.equals("BUY")) ? "BUY" : "SELL";

            BigDecimal flooredQty = null;
            if (req.getQuantity() != null) {
                if (req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    return ApiResponse.error("quantity必须大于0");
                }
                flooredQty = req.getQuantity().setScale(0, java.math.RoundingMode.FLOOR);
                if (flooredQty.compareTo(BigDecimal.ONE) < 0) {
                    return ApiResponse.error("数量不足1，已拦截");
                }
            }

            String orderTypeStr = req.getOrderType() == null ? "MARKET" : req.getOrderType().trim().toUpperCase();
            if (!orderTypeStr.equals("MARKET") && !orderTypeStr.equals("LIMIT")) {
                return ApiResponse.error("orderType必须为MARKET或LIMIT");
            }
            boolean isLimit = orderTypeStr.equals("LIMIT");
            if (isLimit) {
                if (req.getLimitPrice() == null || req.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    return ApiResponse.error("限价单必须提供有效的limitPrice");
                }
            }

            TradingAccount account = tradingAccountService.getById(req.getAccountId());
            if (account == null) {
                return ApiResponse.error("账户不存在");
            }
            if (account.getBindStatus() == TradingAccount.BindStatus.UNBIND) {
                return ApiResponse.error("账户未绑定");
            }
            if (!account.isApiEnabled()) {
                return ApiResponse.error("账户API未启用");
            }

            String idemKey = buildCloseIdemKey(req.getAccountId(), req.getSymbol(), mappedSide, req.getRequestId());
            if (req.getRequestId() != null) {
                Object existed = redisCache.get(idemKey);
                if (existed instanceof String && !((String) existed).isBlank()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("result", existed);
                    return ApiResponse.success("重复请求，返回已有结果", result);
                }
            }

            List<com.chain.ai.trade.order.entity.vo.OrderVO> positionOrders =
                    tradeOrderService.getPositionOrders(req.getAccountId(), req.getSymbol());
            if (positionOrders == null || positionOrders.isEmpty()) {
                return ApiResponse.error("暂无可平仓订单");
            }

            List<com.chain.ai.trade.order.entity.vo.OrderVO> matched = positionOrders.stream()
                    .filter(o -> o != null && o.getOrderSide() != null && mappedSide.equalsIgnoreCase(o.getOrderSide()))
                    .sorted(Comparator.comparing(com.chain.ai.trade.order.entity.vo.OrderVO::getOrderTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (matched.isEmpty()) {
                return ApiResponse.error("暂无可平仓订单");
            }

            List<String> closedOrderSns = new ArrayList<>();
            List<String> failedOrderSns = new ArrayList<>();

            if (flooredQty == null) {
                for (com.chain.ai.trade.order.entity.vo.OrderVO o : matched) {
                    String orderSn = o.getOrderSn();
                    if (orderSn == null || orderSn.isBlank()) {
                        continue;
                    }
                    boolean ok;
                    if (isLimit) {
                        BigDecimal canClose = tradeOrderService.getRemainingPositionByOrderSn(orderSn);
                        if (canClose != null && canClose.compareTo(BigDecimal.ZERO) > 0) {
                            ok = tradeOrderService.closeOrderByVolume(orderSn, canClose, req.getLimitPrice());
                        } else {
                            ok = false;
                        }
                    } else {
                        ok = tradeOrderService.closeOrderByOrderSn(orderSn);
                    }
                    if (ok) {
                        closedOrderSns.add(orderSn);
                    } else {
                        failedOrderSns.add(orderSn);
                    }
                }
            } else {
                BigDecimal remaining = flooredQty;
                for (com.chain.ai.trade.order.entity.vo.OrderVO o : matched) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    String orderSn = o.getOrderSn();
                    if (orderSn == null || orderSn.isBlank()) {
                        continue;
                    }
                    BigDecimal canClose = tradeOrderService.getRemainingPositionByOrderSn(orderSn);
                    if (canClose == null || canClose.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal toClose = remaining.min(canClose);
                    if (toClose.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    boolean ok = isLimit
                            ? tradeOrderService.closeOrderByVolume(orderSn, toClose, req.getLimitPrice())
                            : tradeOrderService.closeOrderByVolume(orderSn, toClose);
                    if (ok) {
                        closedOrderSns.add(orderSn);
                        remaining = remaining.subtract(toClose);
                    } else {
                        failedOrderSns.add(orderSn);
                    }
                }
            }

            if (closedOrderSns.isEmpty()) {
                return ApiResponse.error("平仓失败");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("closedOrderSns", closedOrderSns);
            if (!failedOrderSns.isEmpty()) {
                result.put("failedOrderSns", failedOrderSns);
            }
            result.put("symbol", req.getSymbol());
            result.put("side", side.equals("BUY") ? "LONG" : side.equals("SELL") ? "SHORT" : side);

            if (req.getRequestId() != null) {
                redisCache.put(idemKey, String.join(",", closedOrderSns), 600L);
            }
            return ApiResponse.success("手动平仓已触发", result);
        } catch (Exception e) {
            log.error("手动平仓请求处理失败", e);
            return ApiResponse.error("请求处理失败: " + e.getMessage());
        }
    }

    private String buildCloseIdemKey(String accountId, String symbol, String side, String requestId) {
        return "manual:close:" + (accountId == null ? "-" : accountId) + ":" + (symbol == null ? "-" : symbol)
                + ":" + (side == null ? "-" : side) + ":" + (requestId == null ? "-" : requestId);
    }

    @Data
    public static class ManualOpenRequest {
        private String accountId;
        private String robotId;
        private String symbol;
        private String side;
        private String orderType;
        private BigDecimal quantity;
        private BigDecimal limitPrice;
        private BigDecimal entryPrice;
        private String timeInForce;
        private Integer leverage;
        private String exchange;
        private String requestId;
        private String channel;
        private Map<String, Object> metadata;
        private String apiKey;
        private String secretKey;
        private String passphrase;
    }

    @PostMapping("/reverse")
    public ApiResponse<Map<String, Object>> reverse(@RequestBody ManualReverseRequest req) {
        try {
            if (req == null) {
                return ApiResponse.error("请求体不能为空");
            }
            if (req.getAccountId() == null || req.getAccountId().isBlank()) {
                return ApiResponse.error("accountId不能为空");
            }
            if (req.getSymbol() == null || req.getSymbol().isBlank()) {
                return ApiResponse.error("symbol不能为空");
            }
            if (req.getFromSide() == null || req.getFromSide().isBlank()) {
                return ApiResponse.error("fromSide不能为空");
            }
            if (req.getToSide() == null || req.getToSide().isBlank()) {
                return ApiResponse.error("toSide不能为空");
            }

            String fromSide = req.getFromSide().trim().toUpperCase();
            String toSide = req.getToSide().trim().toUpperCase();
            if (!fromSide.equals("LONG") && !fromSide.equals("SHORT")) {
                return ApiResponse.error("fromSide必须为LONG或SHORT");
            }
            if (!toSide.equals("LONG") && !toSide.equals("SHORT")) {
                return ApiResponse.error("toSide必须为LONG或SHORT");
            }
            if (fromSide.equals(toSide)) {
                return ApiResponse.error("反手方向不能相同");
            }

            String orderTypeStr = req.getOrderType() == null ? "MARKET" : req.getOrderType().trim().toUpperCase();
            if (!orderTypeStr.equals("MARKET") && !orderTypeStr.equals("LIMIT")) {
                return ApiResponse.error("orderType必须为MARKET或LIMIT");
            }
            boolean isLimit = orderTypeStr.equals("LIMIT");
            if (isLimit && (req.getLimitPrice() == null || req.getLimitPrice().compareTo(BigDecimal.ZERO) <= 0)) {
                return ApiResponse.error("限价单必须提供有效的limitPrice");
            }

            TradingAccount account = tradingAccountService.getById(req.getAccountId());
            if (account == null) {
                return ApiResponse.error("账户不存在");
            }
            if (account.getBindStatus() == TradingAccount.BindStatus.UNBIND) {
                return ApiResponse.error("账户未绑定");
            }
            if (!account.isApiEnabled()) {
                return ApiResponse.error("账户API未启用");
            }

            String closeMappedSide = fromSide.equals("LONG") ? "BUY" : "SELL";
            List<com.chain.ai.trade.order.entity.vo.OrderVO> positionOrders =
                    tradeOrderService.getPositionOrders(req.getAccountId(), req.getSymbol());
            if (positionOrders == null || positionOrders.isEmpty()) {
                return ApiResponse.error("暂无可平仓订单，无法反手");
            }

            List<com.chain.ai.trade.order.entity.vo.OrderVO> matched = positionOrders.stream()
                    .filter(o -> o != null && o.getOrderSide() != null && closeMappedSide.equalsIgnoreCase(o.getOrderSide()))
                    .sorted(Comparator.comparing(com.chain.ai.trade.order.entity.vo.OrderVO::getOrderTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (matched.isEmpty()) {
                return ApiResponse.error("暂无可平仓" + fromSide + "订单");
            }

            BigDecimal totalClosed = BigDecimal.ZERO;
            List<String> closedOrderSns = new ArrayList<>();
            List<String> failedOrderSns = new ArrayList<>();

            BigDecimal targetQty = req.getQuantity() != null && req.getQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? req.getQuantity().setScale(0, java.math.RoundingMode.FLOOR)
                    : null;

            if (targetQty == null) {
                for (com.chain.ai.trade.order.entity.vo.OrderVO o : matched) {
                    String orderSn = o.getOrderSn();
                    if (orderSn == null || orderSn.isBlank()) continue;
                    BigDecimal closeQty = tradeOrderService.getRemainingPositionByOrderSn(orderSn);
                    if (closeQty != null && closeQty.compareTo(BigDecimal.ZERO) > 0) {
                        boolean ok = tradeOrderService.closeOrderByVolume(orderSn, closeQty);
                        if (ok) {
                            closedOrderSns.add(orderSn);
                            totalClosed = totalClosed.add(closeQty);
                        } else {
                            failedOrderSns.add(orderSn);
                        }
                    }
                }
            } else {
                BigDecimal remaining = targetQty;
                for (com.chain.ai.trade.order.entity.vo.OrderVO o : matched) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    String orderSn = o.getOrderSn();
                    if (orderSn == null || orderSn.isBlank()) continue;
                    BigDecimal canClose = tradeOrderService.getRemainingPositionByOrderSn(orderSn);
                    if (canClose == null || canClose.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal toClose = remaining.min(canClose);
                    if (toClose.compareTo(BigDecimal.ZERO) <= 0) continue;
                    boolean ok = tradeOrderService.closeOrderByVolume(orderSn, toClose);
                    if (ok) {
                        closedOrderSns.add(orderSn);
                        totalClosed = totalClosed.add(toClose);
                        remaining = remaining.subtract(toClose);
                    } else {
                        failedOrderSns.add(orderSn);
                    }
                }
            }

            if (closedOrderSns.isEmpty()) {
                return ApiResponse.error("平仓失败，无法反手");
            }

            String openSide = toSide.equals("LONG") ? "BUY" : "SELL";

            AccountSecrets secrets;
            try {
                secrets = accountSecretsService.getAccountSecrets(account.getId());
            } catch (Exception ex) {
                return ApiResponse.error("账户API配置无效: " + ex.getMessage());
            }

            String apiKey = secrets.getApiKey() != null ? new String(secrets.getApiKey()) : null;
            String secretKey = secrets.getApiSecret() != null ? new String(secrets.getApiSecret()) : null;
            String passphrase = secrets.getPassphrase() != null ? new String(secrets.getPassphrase()) : null;

            BigDecimal openQty = targetQty != null ? targetQty : totalClosed;

            com.chain.ai.trade.common.entity.param.TradingStrategyParams params =
                    com.chain.ai.trade.common.entity.param.TradingStrategyParams.builder()
                            .accountId(req.getAccountId())
                            .symbol(req.getSymbol())
                            .side(openSide)
                            .amount(openQty)
                            .orderTime(new Date())
                            .leverage(req.getLeverage())
                            .memberPlatform(account.getMemberPlatform())
                            .simulated(account.getSimulated())
                            .apiKey(apiKey)
                            .secretKey(secretKey)
                            .passphrase(passphrase)
                            .build();

            String newOrderId = tradeOrderService.createOrder(params);
            secrets.clear();
            if (newOrderId == null || newOrderId.isBlank()) {
                return ApiResponse.error("平仓成功，但开仓失败");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("closedOrderSns", closedOrderSns);
            result.put("newOrderId", newOrderId);
            result.put("symbol", req.getSymbol());
            result.put("fromSide", fromSide);
            result.put("toSide", toSide);
            result.put("closedQuantity", totalClosed);
            result.put("openQuantity", openQty);
            if (!failedOrderSns.isEmpty()) {
                result.put("failedOrderSns", failedOrderSns);
            }
            return ApiResponse.success("反手成功", result);
        } catch (Exception e) {
            log.error("反手请求处理失败", e);
            return ApiResponse.error("请求处理失败: " + e.getMessage());
        }
    }

    @Data
    public static class ManualCloseRequest {
        private String accountId;
        private String robotId;
        private String symbol;
        private String side;
        private BigDecimal quantity;
        private String orderType;
        private BigDecimal limitPrice;
        private String requestId;
        private String channel;
        private Map<String, Object> metadata;
    }

    @Data
    public static class ManualReverseRequest {
        private String accountId;
        private String robotId;
        private String symbol;
        private String fromSide;
        private String toSide;
        private BigDecimal quantity;
        private String orderType;
        private BigDecimal limitPrice;
        private Integer leverage;
        private String requestId;
        private String channel;
        private Map<String, Object> metadata;
    }
}
