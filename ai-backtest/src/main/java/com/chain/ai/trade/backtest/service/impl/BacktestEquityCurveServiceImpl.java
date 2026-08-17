package com.chain.ai.trade.backtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.entity.dto.AccountEquityPoint;
import com.chain.ai.trade.backtest.mapper.BacktestEquityCurveMapper;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestEquityCurveServiceImpl extends ServiceImpl<BacktestEquityCurveMapper, BacktestEquityCurve>
        implements BacktestEquityCurveService {

    private final BacktestEquityCurveMapper equityCurveMapper;

    @Override
    public boolean save(BacktestEquityCurve equityCurve) {
        return super.save(equityCurve);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchInsertOrUpdate(List<BacktestEquityCurve> list) {
        if (list == null || list.isEmpty()) return;
        equityCurveMapper.batchInsertOrUpdate(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveEquityCurve(String taskId, List<BacktestEquityCurve> equityCurveList) {
        try {
            if (equityCurveList == null || equityCurveList.isEmpty()) {
                log.warn("净值曲线数据为空，跳过保存: taskId={}", taskId);
                return true;
            }
            deleteByTaskId(taskId);
            for (BacktestEquityCurve curve : equityCurveList) {
                curve.setTaskId(taskId);
            }
            saveBatch(equityCurveList);
            log.info("保存净值曲线数据成功: taskId={}, 记录数={}", taskId, equityCurveList.size());
            return true;
        } catch (Exception e) {
            log.error("保存净值曲线数据失败: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public List<BacktestEquityCurve> getEquityCurveByTaskId(String taskId) {
        try {
            return equityCurveMapper.selectByTaskId(taskId);
        } catch (Exception e) {
            log.error("查询净值曲线数据失败: taskId={}", taskId, e);
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByTaskId(String taskId) {
        try {
            equityCurveMapper.deleteByTaskId(taskId);
            log.info("删除净值曲线数据成功: taskId={}", taskId);
            return true;
        } catch (Exception e) {
            log.error("删除净值曲线数据失败: taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public List<BacktestEquityCurve> getEquitiesByRobotIds(List<String> robotIds, LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = endDate.atTime(LocalTime.MAX);
            return equityCurveMapper.selectByRobotIds(robotIds, startTime, endTime);
        } catch (Exception e) {
            log.error("查询机器人权益数据失败", e);
            return List.of();
        }
    }

    @Override
    public List<BacktestEquityCurve> getLatestByRobotIds(List<String> robotIds) {
        try {
            return equityCurveMapper.selectLatestByRobotIds(robotIds);
        } catch (Exception e) {
            log.error("查询机器人最新权益数据失败", e);
            return List.of();
        }
    }

    @Override
    public List<AccountEquityPoint> getAccountEquityCurve(LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = endDate.atTime(LocalTime.MAX);
            return equityCurveMapper.selectAccountEquityCurve(startTime, endTime);
        } catch (Exception e) {
            log.error("查询账户权益曲线失败", e);
            return List.of();
        }
    }
}
