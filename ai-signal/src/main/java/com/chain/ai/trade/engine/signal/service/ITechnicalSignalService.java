package com.chain.ai.trade.engine.signal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.engine.signal.entity.vo.IndicatorPerformanceVO;
import com.chain.ai.trade.engine.signal.entity.vo.SignalStatisticsVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 技术信号服务接口
 * 负责技术指标的信号生成、存储、查询和分析
 * 继承MyBatis-Plus IService以获得通用CRUD操作
 *
 * 注意：这个接口只负责技术信号的相关操作，不涉及任何业务逻辑（比如下单、风控等）
 */
public interface ITechnicalSignalService extends IService<TechnicalSignal> {

    // ==================== 信号生成与保存 ====================

    /**
     * 生成并保存技术信号
     *
     * @param signalDTO 技术信号数据
     * @return 保存后的信号ID
     */
    Long saveTechnicalSignal(TechnicalSignalDTO signalDTO);

    /**
     * 批量保存技术信号
     *
     * @param signalDTOList 信号列表
     * @return 保存数量
     */
    Integer batchSaveTechnicalSignals(List<TechnicalSignalDTO> signalDTOList);

    /**
     * 生成技术信号哈希（用于去重）
     *
     * @param signalDTO 信号数据
     * @return 唯一哈希值
     */
    String generateSignalHash(TechnicalSignalDTO signalDTO);

    /**
     * 检查信号是否已存在
     *
     * @param signalHash 信号哈希
     * @return 是否存在
     */
    Boolean existsByHash(String signalHash);

    // ==================== 增强查询方法 ====================

    /**
     * 根据哈希查询技术信号
     *
     * @param signalHash 信号哈希
     * @return 技术信号实体
     */
    TechnicalSignal getByHash(String signalHash);

    /**
     * 分页查询技术信号
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<TechnicalSignal> pageTechnicalSignals(TechnicalSignalQuery query);

    /**
     * 查询指定时间段的有效技术信号，signalStrength 大于 0 的过滤条件
     *
     * @param symbol 交易对
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 信号列表
     */
    List<TechnicalSignal> getSignalsByTimeRange(String symbol, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询指定策略在时间段内的开仓信号（LONG/SHORT），按 K 线时间戳升序，用于回测离线重放
     *
     * @param symbol    交易对
     * @param indicator 信号指标（strategyName）
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 开仓信号列表（升序）
     */
    List<TechnicalSignal> getOpenSignalsByStrategy(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定时间段内的信号数量
     *
     * @param symbol 交易对
     * @param indicator 指标类型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param directions 信号方向列表，如果为空则统计所有方向
     * @return 信号数量
     */
    default long countSignals(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime, List<String> directions) {
        List<TechnicalSignal> signals = getSignalsByTimeRange(symbol, startTime, endTime);
        if (signals == null) {
            return 0;
        }
        
        return signals.stream()
                .filter(s -> (indicator == null || indicator.equals(s.getIndicator())))
                .filter(s -> (directions == null || directions.isEmpty() || directions.contains(s.getTechnicalDirection())))
                .count();
    }

    /**
     * 查询最新N个技术信号
     *
     * @param symbol 交易对（可选）
     * @param indicator 指标类型（可选）
     * @param limit 数量限制
     * @return 最新信号列表
     */
    List<TechnicalSignal> getLatestSignals(String symbol, String indicator, Integer limit);

    /**
     * 查询指定条件的信号列表
     *
     * @param query 查询条件
     * @return 信号列表
     */
    List<TechnicalSignal> listTechnicalSignals(TechnicalSignalQuery query);

    /**
     * 查询特定指标在某个方向上的信号
     *
     * @param symbol 交易对
     * @param indicator 指标
     * @param direction 信号方向
     * @param limit 数量限制
     * @return 信号列表
     */
    List<TechnicalSignal> getSignalsByDirection(String symbol, String indicator, String direction, Integer limit);

    // ==================== 信号统计与分析 ====================

    /**
     * 获取信号统计信息
     *
     * @param query 查询条件
     * @return 统计视图对象
     */
    SignalStatisticsVO getSignalStatistics(TechnicalSignalQuery query);

    /**
     * 计算指标命中率
     *
     * @param symbol 交易对
     * @param indicator 指标
     * @param lookbackPeriod 回看周期（小时）
     * @return 命中率（0-1）
     */
    BigDecimal calculateIndicatorHitRate(String symbol, String indicator, Integer lookbackPeriod);

    /**
     * 计算信号强度分布
     *
     * @param symbol 交易对
     * @param indicator 指标
     * @return 强度分布Map
     */
    Map<String, Integer> getSignalStrengthDistribution(String symbol, String indicator);

    /**
     * 获取指标性能分析
     *
     * @param symbol 交易对
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 指标性能列表
     */
    List<IndicatorPerformanceVO> analyzeIndicatorPerformance(String symbol, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 计算信号相关性
     *
     * @param indicator1 指标1
     * @param indicator2 指标2
     * @param symbol 交易对
     * @return 相关系数
     */
    BigDecimal calculateSignalCorrelation(String indicator1, String indicator2, String symbol);

    // ==================== 信号处理与转换 ====================

    /**
     * 将技术信号转换为业务信号（供业务层调用）
     *
     * @param technicalSignalId 技术信号ID
     * @return 是否可以生成业务信号
     */
    Boolean canGenerateTradeSignal(Long technicalSignalId);

    /**
     * 获取技术信号的摘要信息
     *
     * @param technicalSignalId 技术信号ID
     * @return 摘要信息
     */
    String getSignalBrief(Long technicalSignalId);

    /**
     * 标记信号被拒绝（如低波过滤）：将 signalStrength 置 0
     *
     * @param signalId 信号ID
     */
    void markRejected(Long signalId);

    /**
     * 提取技术信号中的关键特征
     *
     * @param technicalSignalId 技术信号ID
     * @return 特征Map
     */
    Map<String, Object> extractSignalFeatures(Long technicalSignalId);

    /**
     * 验证信号有效性
     *
     * @param technicalSignalId 技术信号ID
     * @return 是否有效
     */
    Boolean validateSignal(Long technicalSignalId);

    // ==================== 信号聚合与分组 ====================

    /**
     * 按时间分组统计信号
     *
     * @param symbol 交易对
     * @param timeframe 时间周期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分组统计结果
     */
    Map<String, Long> groupSignalsByTime(String symbol, String timeframe, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按指标分组统计信号
     *
     * @param symbol 交易对
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 指标统计结果
     */
    Map<String, Long> groupSignalsByIndicator(String symbol, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按方向分组统计信号
     *
     * @param symbol 交易对
     * @param indicator 指标
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 方向统计结果
     */
    Map<String, Long> groupSignalsByDirection(String symbol, String indicator, LocalDateTime startTime, LocalDateTime endTime);

    // ==================== 信号缓存相关 ====================

    /**
     * 缓存技术信号
     *
     * @param signal 技术信号
     * @param expireSeconds 过期时间（秒）
     */
    void cacheTechnicalSignal(TechnicalSignal signal, Long expireSeconds);

    /**
     * 从缓存获取技术信号
     *
     * @param signalHash 信号哈希
     * @return 技术信号
     */
    TechnicalSignal getTechnicalSignalFromCache(String signalHash);

    /**
     * 清除缓存中的技术信号
     *
     * @param signalHash 信号哈希
     */
    void evictTechnicalSignalFromCache(String signalHash);

    /**
     * 获取缓存中的最新信号
     *
     * @param symbol 交易对
     * @param indicator 指标
     * @return 最新信号
     */
    TechnicalSignal getLatestSignalFromCache(String symbol, String indicator);

    // ==================== 信号监控与告警 ====================

    /**
     * 监控信号生成频率
     *
     * @param indicator 指标
     * @param windowMinutes 时间窗口（分钟）
     * @return 信号数量
     */
    Long monitorSignalFrequency(String indicator, Integer windowMinutes);

    /**
     * 检测异常信号
     *
     * @param symbol 交易对
     * @param threshold 异常阈值
     * @return 异常信号列表
     */
    List<TechnicalSignal> detectAnomalousSignals(String symbol, BigDecimal threshold);

    /**
     * 触发信号告警
     *
     * @param signal 技术信号
     * @param alarmType 告警类型
     */
    void triggerSignalAlarm(TechnicalSignal signal, String alarmType);

    // ==================== 批量操作 ====================

    /**
     * 批量删除过期信号
     *
     * @param expireDays 过期天数
     * @return 删除数量
     */
    Integer batchDeleteExpiredSignals(Integer expireDays);

    /**
     * 批量更新信号状态
     *
     * @param signalIds 信号ID列表
     * @param status 新状态
     * @return 更新数量
     */
    Integer batchUpdateSignalStatus(List<Long> signalIds, String status);

    /**
     * 批量导出信号数据
     *
     * @param query 查询条件
     * @param format 导出格式（CSV/JSON）
     * @return 文件路径
     */
    String batchExportSignals(TechnicalSignalQuery query, String format);

    // ==================== 数据质量检查 ====================

    /**
     * 检查数据完整性
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 完整性报告
     */
    Map<String, Object> checkDataIntegrity(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 修复异常数据
     *
     * @param signalId 信号ID
     * @return 修复是否成功
     */
    Boolean repairAbnormalData(Long signalId);

    /**
     * 验证信号一致性
     *
     * @param signal 技术信号
     * @return 一致性检查结果
     */
    Map<String, Boolean> validateSignalConsistency(TechnicalSignal signal);

    /**
     * 根据指定时间搓获取技术信号
     *
     * @param symbol 交易对
     * @return 修复是否成功
     */
    TechnicalSignal getTechnicalSignalByTime(String indicator,String symbol,String technicalDirection,Long klineTimestamp);
}
