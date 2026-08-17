package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.controller.dto.TradingSummaryDTO;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.entity.dos.TradeExitBatch;
import com.chain.ai.trade.order.entity.dos.TradeExitItem;
import com.chain.ai.trade.order.entity.dto.OrderQueryDTO;
import org.springframework.security.core.context.SecurityContextHolder;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.entity.vo.PageVO;
import com.chain.ai.trade.order.entity.vo.RobotOrderReportVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 订单管理控制器
 * 提供订单查询和管理相关的 REST API 接口
 */
@Api(tags = "订单管理")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderManagerController {

    private final ITradeOrderService tradeOrderService;
    private final ITradingAccountService tradingAccountService;

    /**
     * 分页查询订单列表
     */
    @ApiOperation("分页查询订单列表")
    @GetMapping("/list")
    public ApiResponse<PageVO<OrderVO>> queryOrders(
            @ApiParam("机器人ID（推荐使用，替代memberId和accountId）") @RequestParam(required = false) String robotId,
            @ApiParam("会员ID（已废弃，建议使用robotId）") @RequestParam(required = false) String memberId,
            @ApiParam("账户ID（已废弃，建议使用robotId）") @RequestParam(required = false) Long accountId,
            @ApiParam("订单号") @RequestParam(required = false) String orderSn,
            @ApiParam("交易对") @RequestParam(required = false) String symbol,
            @ApiParam("订单状态") @RequestParam(required = false) String status,
            @ApiParam("订单方向(BUY/SELL)") @RequestParam(required = false) String orderSide,
            @ApiParam("开始时间") @RequestParam(required = false) Long startTime,
            @ApiParam("结束时间") @RequestParam(required = false) Long endTime,
            @ApiParam("平仓日期(yyyy-MM-dd)") @RequestParam(required = false) String closeDate,
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @ApiParam("排序字段") @RequestParam(defaultValue = "orderTime") String sortField,
            @ApiParam("排序方向") @RequestParam(defaultValue = "desc") String sortOrder) {

        log.info("收到订单分页查询请求: memberId={}, accountId={}, robotId={}, symbol={}, status={}, orderSide={}, startTime={}, endTime={}, pageNum={}, pageSize={}",
                memberId, accountId, robotId, symbol, status, orderSide, startTime, endTime, pageNum, pageSize);

        try {
            Date closeStart = null;
            Date closeEnd = null;
            if (closeDate != null && !closeDate.isBlank()) {
                LocalDate d = LocalDate.parse(closeDate.trim());
                ZoneId zoneId = ZoneId.systemDefault();
                long startMs = d.atStartOfDay(zoneId).toInstant().toEpochMilli();
                long endMs = d.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L;
                closeStart = new Date(startMs);
                closeEnd = new Date(endMs);
            }

            // 从 JWT token 获取当前登录用户，管理员可查看全部订单，普通用户只能看自己的
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() != null) {
                String currentMemberId = authentication.getPrincipal().toString();
                String role = authentication.getDetails() != null ? authentication.getDetails().toString() : "";
                if (!"ADMIN".equalsIgnoreCase(role)) {
                    // 非管理员强制使用当前用户ID过滤
                    memberId = currentMemberId;
                }
                // 管理员保留前端传入的 memberId（null 则不限制，可查看全部）
            }

            // 构建查询DTO
            OrderQueryDTO queryDTO = OrderQueryDTO.builder()
                    .memberId(memberId)
                    .accountId(accountId)
                    .robotId(robotId)
                .orderSn(orderSn)
                    .symbol(symbol)
                    .status(status)
                    .orderSide(orderSide)
                    .startTime(startTime != null ? new java.util.Date(startTime) : null)
                    .endTime(endTime != null ? new java.util.Date(endTime) : null)
                    .closeStartTime(closeStart)
                    .closeEndTime(closeEnd)
                    .pageNum(pageNum)
                    .pageSize(pageSize)
                    .sortField(sortField)
                    .sortOrder(sortOrder)
                    .build();

            // 执行查询
            PageVO<OrderVO> result = tradeOrderService.queryOrders(queryDTO);

            log.info("订单分页查询成功: 总记录数={}, 当前页={}, 每页大小={}, 总页数={}",
                    result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());

            return ApiResponse.success("查询成功", result);

        } catch (Exception e) {
            log.error("订单分页查询失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 交易汇总（资产、余额、当日盈亏、累计盈亏）
     */
    @ApiOperation("交易汇总")
    @GetMapping("/summary")
    public ApiResponse<TradingSummaryDTO> getTradingSummary(
            @ApiParam("账户ID") @RequestParam(required = false) String accountId,
            @ApiParam("机器人ID") @RequestParam(required = false) String robotId) {
        try {
            BigDecimal totalAssets = BigDecimal.ZERO;
            BigDecimal availableBalance = BigDecimal.ZERO;

            if (accountId != null && !accountId.isBlank()) {
                TradingAccount account = tradingAccountService.getByAccountId(accountId);
                if (account != null && account.getBalances() != null) {
                    Map<String, BigDecimal> balances = parseBalances(account.getBalances());
                    BigDecimal allocationsSum = parseAllocationsSum(account.getAllocations());
                    totalAssets = balances.values().stream()
                            .filter(v -> v != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    availableBalance = totalAssets.subtract(allocationsSum);
                }
            }

            Date now = new Date();
            Date startOfDay = new Date(now.toInstant().atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

            BigDecimal dailyPnL = BigDecimal.ZERO;
            BigDecimal totalPnL = BigDecimal.ZERO;
            if (robotId != null && !robotId.isBlank()) {
                dailyPnL = tradeOrderService.getNetProfitByRobotId(robotId, startOfDay, now);
                totalPnL = tradeOrderService.getCumulativeNetProfitByRobotId(robotId);
            } else if (accountId != null && !accountId.isBlank()) {
                dailyPnL = tradeOrderService.getNetProfitByAccountId(accountId, startOfDay, now);
                totalPnL = tradeOrderService.getCumulativeNetProfitByAccountId(accountId);
            }

            BigDecimal dailyPercent = BigDecimal.ZERO;
            BigDecimal totalPercent = BigDecimal.ZERO;
            if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                dailyPercent = dailyPnL.multiply(new BigDecimal("100"))
                        .divide(totalAssets, 4, java.math.RoundingMode.HALF_UP);
                totalPercent = totalPnL.multiply(new BigDecimal("100"))
                        .divide(totalAssets, 4, java.math.RoundingMode.HALF_UP);
            }

            TradingSummaryDTO dto = TradingSummaryDTO.builder()
                    .totalAssets(totalAssets)
                    .availableBalance(availableBalance)
                    .dailyPnL(dailyPnL)
                    .totalPnL(totalPnL)
                    .dailyPnLPercent(dailyPercent)
                    .totalPnLPercent(totalPercent)
                    .build();
            return ApiResponse.success("查询成功", dto);
        } catch (Exception e) {
            log.error("交易汇总查询失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    private Map<String, BigDecimal> parseBalances(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private BigDecimal parseAllocationsSum(String json) {
        if (json == null || json.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, BigDecimal> map = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, BigDecimal>>() {});
            return map.values().stream()
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 机器人订单收益报表（按日或月聚合）
     */
    @ApiOperation("机器人订单收益报表")
    @GetMapping("/report/by-robot")
    public ApiResponse<RobotOrderReportVO> getRobotOrderReport(
            @ApiParam("机器人ID") @RequestParam String robotId,
            @ApiParam("开始时间(毫秒时间戳)") @RequestParam(required = false) Long startTime,
            @ApiParam("结束时间(毫秒时间戳)") @RequestParam(required = false) Long endTime,
            @ApiParam("粒度：day 按日，month 按月") @RequestParam(defaultValue = "day") String granularity) {

        log.info("收到机器人订单收益报表请求: robotId={}, startTime={}, endTime={}, granularity={}", robotId, startTime, endTime, granularity);

        try {
            java.util.Date start = startTime != null ? new java.util.Date(startTime) : null;
            java.util.Date end = endTime != null ? new java.util.Date(endTime) : null;
            RobotOrderReportVO result = tradeOrderService.getRobotOrderReport(robotId, start, end, granularity);
            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("机器人订单收益报表查询失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单号查询订单项列表
     */
    @ApiOperation("查询订单项列表")
    @GetMapping("/{orderSn}/items")
    public ApiResponse<java.util.List<TradeEntry>> listOrderItems(
            @ApiParam("订单号") @PathVariable String orderSn) {
        try {
            java.util.List<TradeEntry> items = tradeOrderService.listOrderItemsByOrderSn(orderSn);
            return ApiResponse.success("查询成功", items);
        } catch (Exception e) {
            log.error("订单项查询失败: orderSn={}", orderSn, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单号查询平仓记录列表
     */
    @ApiOperation("查询平仓记录列表")
    @GetMapping("/{orderSn}/closes")
    public ApiResponse<java.util.List<TradeExitBatch>> listOrderCloses(
            @ApiParam("订单号") @PathVariable String orderSn) {
        try {
            java.util.List<TradeExitBatch> closes = tradeOrderService.listOrderClosesByOrderSn(orderSn);
            return ApiResponse.success("查询成功", closes);
        } catch (Exception e) {
            log.error("平仓记录查询失败: orderSn={}", orderSn, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单号查询平仓明细列表（带 orderItemSn 关联）
     */
    @ApiOperation("查询平仓明细列表")
    @GetMapping("/{orderSn}/close-items")
    public ApiResponse<java.util.List<TradeExitItem>> listOrderCloseItems(
            @ApiParam("订单号") @PathVariable String orderSn) {
        try {
            java.util.List<TradeExitItem> items = tradeOrderService.listOrderCloseItemsByOrderSn(orderSn);
            return ApiResponse.success("查询成功", items);
        } catch (Exception e) {
            log.error("平仓明细查询失败: orderSn={}", orderSn, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单ID查询订单详情
     */
    @ApiOperation("查询订单详情")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderVO> getOrderById(
            @ApiParam("订单ID") @PathVariable Long orderId) {

        log.info("收到订单详情查询请求: orderId={}", orderId);

        try {
            // 这里可以添加具体的订单详情查询逻辑
            // 暂时返回空实现
            return ApiResponse.error("功能暂未实现");

        } catch (Exception e) {
            log.error("订单详情查询失败: orderId={}", orderId, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
