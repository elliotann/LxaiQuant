package com.chain.ai.trade.logs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.logs.entity.*;
import com.chain.ai.trade.logs.service.BusinessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务日志查询控制器
 * 提供日志查询的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogQueryController {
    
    private final BusinessLogService businessLogService;
    
    /**
     * 查询订单日志
     */
    @GetMapping("/orders")
    public Map<String, Object> queryOrderLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<OrderLogEntity> page = businessLogService.queryOrderLogs(userId, symbol, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying order logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询订单日志失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询交易日志
     */
    @GetMapping("/trades")
    public Map<String, Object> queryTradeLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<TradeLogEntity> page = businessLogService.queryTradeLogs(userId, symbol, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying trade logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询交易日志失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询账户资金变动日志
     */
    @GetMapping("/account-fund-changes")
    public Map<String, Object> queryAccountFundChangeLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<AccountFundChangeLogEntity> page = businessLogService.queryAccountFundChangeLogs(userId, currency, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying account fund change logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询账户资金变动日志失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询风控日志
     */
    @GetMapping("/risk-controls")
    public Map<String, Object> queryRiskControlLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<RiskControlLogEntity> page = businessLogService.queryRiskControlLogs(userId, riskType, riskLevel, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying risk control logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询风控日志失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询策略状态变更日志
     */
    @GetMapping("/strategy-status-changes")
    public Map<String, Object> queryStrategyStatusChangeLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<StrategyStatusChangeLogEntity> page = businessLogService.queryStrategyStatusChangeLogs(userId, strategyId, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying strategy status change logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询策略状态变更日志失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询系统错误日志
     */
    @GetMapping("/system-errors")
    public Map<String, Object> querySystemErrorLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorLevel,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        try {
            Page<SystemErrorLogEntity> page = businessLogService.querySystemErrorLogs(userId, errorType, errorLevel, resolved, startTime, endTime, pageNum, pageSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return result;
        } catch (Exception e) {
            log.error("Error querying system error logs", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询系统错误日志失败: " + e.getMessage());
            return result;
        }
    }
    

}
