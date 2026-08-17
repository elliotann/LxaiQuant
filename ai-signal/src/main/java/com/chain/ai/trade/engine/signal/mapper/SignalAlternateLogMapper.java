package com.chain.ai.trade.engine.signal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 信号交替流水Mapper接口
 */
@Mapper
public interface SignalAlternateLogMapper extends BaseMapper<SignalAlternateLog> {

    /**
     * 查询最近一笔未配对的交替记录（exit_signal_id 为空）
     */
    SignalAlternateLog selectLastUnpaired(@Param("strategyName") String strategyName,
                                          @Param("symbol") String symbol,
                                          @Param("timeframe") String timeframe);

    /**
     * 查询指定策略的全部交替记录（按开仓时间升序，用于回测重放）
     */
    List<SignalAlternateLog> selectAll(@Param("strategyName") String strategyName,
                                       @Param("symbol") String symbol,
                                       @Param("timeframe") String timeframe);
}
