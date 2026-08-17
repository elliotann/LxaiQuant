package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
@Slf4j
public class RobotListController {

    private final ITradingBotService tradingBotService;

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<TradingBot>>> listAllBots() {
        try {
            List<TradingBot> bots = tradingBotService.list();
            return ResponseEntity.ok(ApiResponse.success(bots));
        } catch (Exception e) {
            log.error("查询机器人列表失败", e);
            return ResponseEntity.ok(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }
}
