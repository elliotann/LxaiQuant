package com.chain.ai.trade.backtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.entity.dto.AccountEquityPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BacktestEquityCurveMapper extends BaseMapper<BacktestEquityCurve> {

    List<BacktestEquityCurve> selectByTaskId(@Param("taskId") String taskId);

    void deleteByTaskId(@Param("taskId") String taskId);

    void batchInsert(@Param("list") List<BacktestEquityCurve> list);

    void batchInsertOrUpdate(@Param("list") List<BacktestEquityCurve> list);

    List<BacktestEquityCurve> selectByRobotIds(@Param("robotIds") List<String> robotIds,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    List<BacktestEquityCurve> selectLatestByRobotIds(@Param("robotIds") List<String> robotIds);

    List<AccountEquityPoint> selectAccountEquityCurve(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    List<BacktestEquityCurve> selectByRobotIdAndTimeRange(@Param("robotId") String robotId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);
}
