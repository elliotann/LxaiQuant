package com.chain.ai.trade.backtest.service;

import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.entity.dto.AccountEquityPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BacktestEquityCurveService {

    boolean save(BacktestEquityCurve equityCurve);

    boolean saveEquityCurve(String taskId, List<BacktestEquityCurve> equityCurveList);

    void batchInsertOrUpdate(List<BacktestEquityCurve> list);

    List<BacktestEquityCurve> getEquityCurveByTaskId(String taskId);

    boolean deleteByTaskId(String taskId);

    List<BacktestEquityCurve> getEquitiesByRobotIds(List<String> robotIds, LocalDate startDate, LocalDate endDate);

    List<BacktestEquityCurve> getLatestByRobotIds(List<String> robotIds);

    List<AccountEquityPoint> getAccountEquityCurve(LocalDate startDate, LocalDate endDate);
}
