package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.mapper.TradeOrderItemMapper;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.order.service.impl.GateioDirectTradeService;
import com.chain.ai.trade.order.service.impl.OkxDirectTradeService;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Api(tags = "交易订单管理")
@RestController
@RequestMapping("/api/trade-order")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TradeOrderController {

    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ITradeOrderService tradeOrderService;
    private final ITradingAccountService tradingAccountService;

    @ApiOperation("修改订单项止盈止损价")
    @PutMapping("/item/{id}/gain-loss")
    public ApiResponse<Void> updateGainLoss(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            TradeEntry item = tradeOrderItemMapper.selectById(id);
            if (item == null) {
                return ApiResponse.error("订单项不存在");
            }

            Object gainPriceObj = body.get("gainPrice");
            Object lossPriceObj = body.get("lossPrice");

            BigDecimal gainPrice = null;
            BigDecimal lossPrice = null;
            if (gainPriceObj != null) {
                if (gainPriceObj instanceof Number) {
                    gainPrice = new BigDecimal(gainPriceObj.toString());
                }
            }
            if (lossPriceObj != null) {
                if (lossPriceObj instanceof Number) {
                    lossPrice = new BigDecimal(lossPriceObj.toString());
                }
            }

            // 先调用交易所修改止盈止损
            String symbol = item.getSymbol();
            TradePosition order = tradeOrderService.getOrderByOrderSn(item.getPositionId());
            if (order != null&&!order.isTest()) {
                TradingAccount account = tradingAccountService.getById(order.getAccountId());
                if (account != null) {
                    ExchangeTradeService exchangeService;
                    Exchange ex = account.getMemberPlatform();
                    if (ex == Exchange.GATEIO) {
                        exchangeService = new GateioDirectTradeService(account);
                    } else {
                        exchangeService = new OkxDirectTradeService(account);
                    }
                    boolean exchangeSuccess = exchangeService.amendTpSl(item.getPositionId(), symbol, gainPrice, lossPrice);
                    if (!exchangeSuccess) {
                        log.warn("交易所修改止盈止损失败，但继续更新本地数据库: id={}", id);
                    }
                } else {
                    log.warn("未找到交易账户: accountId={}", order.getAccountId());
                }
            } else {
                log.warn("未找到父订单: orderSn={}", item.getPositionId());
            }

            // 更新本地数据库
            if (gainPrice != null) {
                item.setGainPrice(gainPrice);
            } else if (gainPriceObj != null) {
                item.setGainPrice(null);
            }
            if (lossPrice != null) {
                item.setLossPrice(lossPrice);
            } else if (lossPriceObj != null) {
                item.setLossPrice(null);
            }

            tradeOrderItemMapper.updateById(item);
            log.info("订单项止盈止损修改成功: id={}, gainPrice={}, lossPrice={}", id, item.getGainPrice(), item.getLossPrice());
            return ApiResponse.<Void>success("修改成功", null);
        } catch (Exception e) {
            log.error("修改订单项止盈止损失败: id={}", id, e);
            return ApiResponse.error("修改失败: " + e.getMessage());
        }
    }

    @ApiOperation("撤销订单")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable String id) {
        try {
            boolean success = tradeOrderService.cancelOrder(id);
            if (success) {
                log.info("订单撤销成功: id={}", id);
                return ApiResponse.success("撤销成功", null);
            }
            return ApiResponse.error("撤销失败，订单不存在或非待成交状态");
        } catch (Exception e) {
            log.error("撤销订单失败: id={}", id, e);
            return ApiResponse.error("撤销失败: " + e.getMessage());
        }
    }
}
