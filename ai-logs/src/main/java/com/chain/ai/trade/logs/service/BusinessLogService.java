package com.chain.ai.trade.logs.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.logs.entity.*;
import com.chain.ai.trade.logs.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessLogService {
    
    private final OrderLogMapper orderLogMapper;
    private final TradeLogMapper tradeLogMapper;
    private final AccountFundChangeLogMapper accountFundChangeLogMapper;
    private final RiskControlLogMapper riskControlLogMapper;
    private final StrategyStatusChangeLogMapper strategyStatusChangeLogMapper;
    private final SystemErrorLogMapper systemErrorLogMapper;
    
    /**
     * 批量保存订单日志
     */
    public void saveOrderLogs(List<OrderLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(orderLogMapper::insert);
            log.info("Saved {} order logs", logs.size());
        }
    }
    
    /**
     * 批量保存成交日志
     */
    public void saveTradeLogs(List<TradeLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(tradeLogMapper::insert);
            log.info("Saved {} trade logs", logs.size());
        }
    }
    
    /**
     * 批量保存账户资金变动日志
     */
    public void saveAccountFundChangeLogs(List<AccountFundChangeLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(accountFundChangeLogMapper::insert);
            log.info("Saved {} account fund change logs", logs.size());
        }
    }
    
    /**
     * 批量保存风控日志
     */
    public void saveRiskControlLogs(List<RiskControlLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(riskControlLogMapper::insert);
            log.info("Saved {} risk control logs", logs.size());
        }
    }
    
    /**
     * 批量保存策略状态变更日志
     */
    public void saveStrategyStatusChangeLogs(List<StrategyStatusChangeLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(strategyStatusChangeLogMapper::insert);
            log.info("Saved {} strategy status change logs", logs.size());
        }
    }
    
    /**
     * 批量保存系统错误日志
     */
    public void saveSystemErrorLogs(List<SystemErrorLogEntity> logs) {
        if (logs != null && !logs.isEmpty()) {
            logs.forEach(systemErrorLogMapper::insert);
            log.info("Saved {} system error logs", logs.size());
        }
    }
    
    /**
     * 查询订单日志
     */
    public Page<OrderLogEntity> queryOrderLogs(Long userId, String symbol, LocalDateTime startTime, 
                                               LocalDateTime endTime, int pageNum, int pageSize) {
        QueryWrapper<OrderLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (symbol != null && !symbol.isEmpty()) {
            wrapper.eq("symbol", symbol);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return orderLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 查询成交日志
     */
    public Page<TradeLogEntity> queryTradeLogs(Long userId, String symbol, LocalDateTime startTime, 
                                               LocalDateTime endTime, int pageNum, int pageSize) {
        QueryWrapper<TradeLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (symbol != null && !symbol.isEmpty()) {
            wrapper.eq("symbol", symbol);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return tradeLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 查询账户资金变动日志
     */
    public Page<AccountFundChangeLogEntity> queryAccountFundChangeLogs(Long userId, String currency, 
                                                                       LocalDateTime startTime, LocalDateTime endTime, 
                                                                       int pageNum, int pageSize) {
        QueryWrapper<AccountFundChangeLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (currency != null && !currency.isEmpty()) {
            wrapper.eq("currency", currency);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return accountFundChangeLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 查询风控日志
     */
    public Page<RiskControlLogEntity> queryRiskControlLogs(Long userId, String riskType, String riskLevel,
                                                         LocalDateTime startTime, LocalDateTime endTime, 
                                                         int pageNum, int pageSize) {
        QueryWrapper<RiskControlLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (riskType != null && !riskType.isEmpty()) {
            wrapper.eq("risk_type", riskType);
        }
        if (riskLevel != null && !riskLevel.isEmpty()) {
            wrapper.eq("risk_level", riskLevel);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return riskControlLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 查询策略状态变更日志
     */
    public Page<StrategyStatusChangeLogEntity> queryStrategyStatusChangeLogs(Long userId, String strategyId,
                                                                           LocalDateTime startTime, LocalDateTime endTime, 
                                                                           int pageNum, int pageSize) {
        QueryWrapper<StrategyStatusChangeLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (strategyId != null && !strategyId.isEmpty()) {
            wrapper.eq("strategy_id", strategyId);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return strategyStatusChangeLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
    
    /**
     * 查询系统错误日志
     */
    public Page<SystemErrorLogEntity> querySystemErrorLogs(Long userId, String errorType, String errorLevel,
                                                         Boolean resolved, LocalDateTime startTime, LocalDateTime endTime, 
                                                         int pageNum, int pageSize) {
        QueryWrapper<SystemErrorLogEntity> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (errorType != null && !errorType.isEmpty()) {
            wrapper.eq("error_type", errorType);
        }
        if (errorLevel != null && !errorLevel.isEmpty()) {
            wrapper.eq("error_level", errorLevel);
        }
        if (resolved != null) {
            wrapper.eq("resolved", resolved);
        }
        if (startTime != null) {
            wrapper.ge("event_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("event_time", endTime);
        }
        wrapper.orderByDesc("event_time");
        
        return systemErrorLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}