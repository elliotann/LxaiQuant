package com.chain.ai.trade.engine.signal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 技术信号Mapper接口
 */
@Mapper
public interface TechnicalSignalMapper extends BaseMapper<TechnicalSignal> {

    /**
     * 批量插入技术信号（用于高性能批量插入）
     */
    int batchInsert(@Param("list") List<TechnicalSignal> list);

    /**
     * 根据时间范围统计信号数量
     */
    Long countByTimeRange(@Param("symbol") String symbol,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    /**
     * 按指标分组统计
     */
    List<Map<String, Object>> groupByIndicator(@Param("symbol") String symbol,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按方向分组统计
     */
    List<Map<String, Object>> groupByDirection(@Param("symbol") String symbol,
                                             @Param("indicator") String indicator,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按时间分组统计（按小时）
     */
    List<Map<String, Object>> groupByHour(@Param("symbol") String symbol,
                                        @Param("timeframe") String timeframe,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    /**
     * 按时间分组统计（按日期）
     */
    List<Map<String, Object>> groupByDay(@Param("symbol") String symbol,
                                       @Param("timeframe") String timeframe,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 获取信号强度分布
     */
    List<Map<String, Object>> getStrengthDistribution(@Param("symbol") String symbol,
                                                    @Param("indicator") String indicator);

    /**
     * 删除过期信号
     */
    int deleteExpiredSignals(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 检查信号哈希是否存在
     */
    boolean existsByHash(@Param("signalHash") String signalHash);

    /**
     * 获取最新信号
     */
    List<TechnicalSignal> selectLatestSignals(@Param("symbol") String symbol,
                                            @Param("indicator") String indicator,
                                            @Param("limit") Integer limit);

    /**
     * 批量更新信号状态（如果需要）
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
