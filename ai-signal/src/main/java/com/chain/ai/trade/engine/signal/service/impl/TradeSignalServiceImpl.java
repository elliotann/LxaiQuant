package com.chain.ai.trade.engine.signal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.SignalQueryDTO;
import com.chain.ai.trade.engine.signal.entity.vo.TradeSignalVO;
import com.chain.ai.trade.engine.signal.mapper.TradeSignalMapper;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交易信号服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSignalServiceImpl extends ServiceImpl<TradeSignalMapper, TradeSignal>
        implements ITradeSignalService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTradeSignal(TradeSignal tradeSignal) {
        try {
            Date now = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
            tradeSignal.setUpdateTime(now);
            if (tradeSignal.getCreateTime() == null) {
                tradeSignal.setCreateTime(now);
            }
            if (tradeSignal.getDeleted() == null) {
                tradeSignal.setDeleted(false);
            }

            if (TradeStatus.PENDING.equals(tradeSignal.getStatus())
                    && tradeSignal.getOrderAction() != null
                    && tradeSignal.getSymbol() != null && !tradeSignal.getSymbol().isBlank()
                    && tradeSignal.getTimeframe() != null && !tradeSignal.getTimeframe().isBlank()
                    && tradeSignal.getKlineTime() != null && !tradeSignal.getKlineTime().isBlank()) {
                TradeSignal existing = this.lambdaQuery()
                        .eq(TradeSignal::getDeleted, false)
                        .eq(TradeSignal::getStatus, TradeStatus.PENDING)
                        .eq(TradeSignal::getSymbol, tradeSignal.getSymbol())
                        .eq(TradeSignal::getTimeframe, tradeSignal.getTimeframe())
                        .eq(TradeSignal::getKlineTime, tradeSignal.getKlineTime())
                        .eq(TradeSignal::getOrderAction, tradeSignal.getOrderAction())
                        .orderByDesc(TradeSignal::getId)
                        .last("limit 1")
                        .one();

                if (existing != null) {
                    if (tradeSignal.getOrderSn() == null || tradeSignal.getOrderSn().isBlank()) {
                        tradeSignal.setOrderSn(existing.getOrderSn());
                    }
                    if (tradeSignal.getOrderItemSn() == null || tradeSignal.getOrderItemSn().isBlank()) {
                        tradeSignal.setOrderItemSn(existing.getOrderItemSn());
                    }
                    tradeSignal.setId(existing.getId());
                    tradeSignal.setCreateTime(existing.getCreateTime());
                    tradeSignal.setDeleted(existing.getDeleted());
                    boolean updated = this.updateById(tradeSignal);
                    if (updated) {
                        log.info("交易信号已存在，更新为最新: id={}, symbol={}, orderAction={}, orderSn={}",
                                existing.getId(), tradeSignal.getSymbol(), tradeSignal.getOrderAction(), tradeSignal.getOrderSn());
                        return existing.getId();
                    }
                    log.error("交易信号更新失败: id={}, symbol={}, orderAction={}",
                            existing.getId(), tradeSignal.getSymbol(), tradeSignal.getOrderAction());
                    return null;
                }
            }

            boolean saved = this.save(tradeSignal);
            if (saved) {
                log.info("交易信号保存成功: id={}, symbol={}, orderAction={}",
                        tradeSignal.getId(), tradeSignal.getSymbol(), tradeSignal.getOrderAction());
                return tradeSignal.getId();
            }

            log.error("交易信号保存失败: {}", tradeSignal);
            return null;
        } catch (Exception e) {
            log.error("保存交易信号异常", e);
            throw new RuntimeException("保存交易信号失败", e);
        }
    }

    @Override
    public List<TradeSignal> queryTradeSignalsByTechnicalSignalId(Long technicalSignalId) {
        LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeSignal::getTechnicalSignalId, technicalSignalId)
                .orderByDesc(TradeSignal::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public TradeSignal queryTradeSignalByOrderSn(String orderSn) {
        LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeSignal::getOrderSn, orderSn)
                .in(TradeSignal::getOrderAction, OrderAction.OPEN_LONG, OrderAction.OPEN_SHORT)
                .orderByDesc(TradeSignal::getCreateTime);
        List<TradeSignal> list = this.list(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }


    @Override
    public List<TradeSignal> queryTradeSignalsByStatus(TradeStatus status) {
        LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeSignal::getStatus, status)
                .orderByDesc(TradeSignal::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<TradeSignal> queryTradeSignalsBySymbolAndStatus(String symbol, TradeStatus status) {
        LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeSignal::getSymbol, symbol)
                .eq(TradeSignal::getStatus, status)
                .orderByDesc(TradeSignal::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTradeSignalStatus(Long signalId, TradeStatus status) {
        try {
            TradeSignal signal = new TradeSignal();
            signal.setId(signalId);
            signal.setStatus(status);
            signal.setUpdateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));

            if (TradeStatus.FILLED.equals(status)) {
                signal.setExecutedTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
            }

            boolean updated = this.updateById(signal);
            if (updated) {
                log.info("交易信号状态更新成功: id={}, status={}", signalId, status);
            }
            return updated;
        } catch (Exception e) {
            log.error("更新交易信号状态异常: id={}, status={}", signalId, status, e);
            throw new RuntimeException("更新交易信号状态失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeTradeSignal(Long signalId) {
        try {
            TradeSignal signal = this.getById(signalId);
            if (signal == null) {
                log.warn("交易信号不存在: id={}", signalId);
                return false;
            }

            if (!TradeStatus.PENDING.equals(signal.getStatus()) &&
                !TradeStatus.APPROVED.equals(signal.getStatus())) {
                log.warn("交易信号状态不允许执行: id={}, status={}", signalId, signal.getStatus());
                return false;
            }

            // 更新状态为执行中
            signal.setStatus(TradeStatus.EXECUTING);
            signal.setUpdateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));

            boolean updated = this.updateById(signal);
            if (updated) {
                log.info("交易信号开始执行: id={}", signalId);
                // 这里可以添加实际的交易执行逻辑
                // 例如调用交易引擎、发送到消息队列等

                // 模拟执行成功，更新为已成交状态
                signal.setStatus(TradeStatus.FILLED);
                signal.setExecutedTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
                this.updateById(signal);

                log.info("交易信号执行完成: id={}", signalId);
            }

            return updated;
        } catch (Exception e) {
            log.error("执行交易信号异常: id={}", signalId, e);
            // 执行失败，更新状态
            TradeSignal signal = this.getById(signalId);
            if (signal != null) {
                signal.setStatus(TradeStatus.FAILED);
                signal.setUpdateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
                this.updateById(signal);
            }
            throw new RuntimeException("执行交易信号失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTradeSignal(Long signalId, String reason) {
        try {
            TradeSignal signal = this.getById(signalId);
            if (signal == null) {
                log.warn("交易信号不存在: id={}", signalId);
                return false;
            }

            if (TradeStatus.FILLED.equals(signal.getStatus()) ||
                TradeStatus.CANCELLED.equals(signal.getStatus())) {
                log.warn("交易信号状态不允许取消: id={}, status={}", signalId, signal.getStatus());
                return false;
            }

            signal.setStatus(TradeStatus.CANCELLED);
            signal.setUpdateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
            signal.setExecutionNote(reason != null ? "取消原因: " + reason : "用户取消");

            boolean updated = this.updateById(signal);
            if (updated) {
                log.info("交易信号取消成功: id={}, reason={}", signalId, reason);
            }
            return updated;
        } catch (Exception e) {
            log.error("取消交易信号异常: id={}", signalId, e);
            throw new RuntimeException("取消交易信号失败", e);
        }
    }

    @Override
    public List<TradeSignal> queryPendingTradeSignals() {
        return queryTradeSignalsByStatus(TradeStatus.PENDING);
    }

    @Override
    public List<TradeSignal> queryExecutingTradeSignals() {
        return queryTradeSignalsByStatus(TradeStatus.EXECUTING);
    }

    @Override
    public List<TradeSignal> queryTradeSignalsByTimeRange(String startTime, String endTime) {
        try {
            LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(TradeSignal::getKlineTime, startTime, endTime)
                    .orderByDesc(TradeSignal::getCreateTime);
            return this.list(wrapper);
        } catch (Exception e) {
            log.error("查询时间范围内的交易信号异常: startTime={}, endTime={}", startTime, endTime, e);
            return List.of();
        }
    }

    @Override
    public List<TradeSignal> queryTradeSignalsBySymbolAndTimeRange(String symbol, String startTime, String endTime) {
        try {
            LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradeSignal::getSymbol, symbol)
                    .between(TradeSignal::getKlineTime, startTime, endTime)
                    .orderByDesc(TradeSignal::getCreateTime);
            return this.list(wrapper);
        } catch (Exception e) {
            log.error("查询交易对时间范围内交易信号异常: symbol={}, startTime={}, endTime={}", symbol, startTime, endTime, e);
            return List.of();
        }
    }

    // ==================== 私有辅助方法 ====================

    private IPage<TradeSignal> createPage(SignalQueryDTO query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    private LambdaQueryWrapper<TradeSignal> buildQueryWrapper(SignalQueryDTO query) {
        LambdaQueryWrapper<TradeSignal> wrapper = new LambdaQueryWrapper<>();

        if (query.getSymbol() != null) {
            wrapper.eq(TradeSignal::getSymbol, query.getSymbol());
        }

        if (query.getStatuses() != null && !query.getStatuses().isEmpty()) {
            wrapper.in(TradeSignal::getStatus, query.getStatuses());
        }

        if (query.getOrderAction() != null) {
            wrapper.eq(TradeSignal::getOrderAction, query.getOrderAction());
        }

        if (query.getRiskLevel() != null) {
            wrapper.eq(TradeSignal::getRiskLevel, query.getRiskLevel());
        }

        if (query.getStartTime() != null) {
            wrapper.ge(TradeSignal::getCreateTime, query.getStartTime());
        }

        if (query.getEndTime() != null) {
            wrapper.le(TradeSignal::getCreateTime, query.getEndTime());
        }

        if (query.getIsProfitable() != null) {
            if (query.getIsProfitable()) {
                wrapper.gt(TradeSignal::getPnlAmount, BigDecimal.ZERO);
            } else {
                wrapper.le(TradeSignal::getPnlAmount, BigDecimal.ZERO);
            }
        }

        return wrapper;
    }

    private TradeSignalVO convertToVO(TradeSignal entity) {
        if (entity == null) {
            return null;
        }

        TradeSignalVO vo = new TradeSignalVO();
        BeanUtils.copyProperties(entity, vo);

        // 设置描述信息
        if (entity.getOrderAction() != null) {
            vo.setOrderActionDesc(entity.getOrderAction().getDescription());
        }

        if (entity.getStatus() != null) {
            vo.setStatusDesc(entity.getStatus().getDescription());
        }

        // 计算是否盈利
        if (entity.getPnlAmount() != null) {
            vo.setIsProfitable(entity.getPnlAmount().compareTo(BigDecimal.ZERO) > 0);
        }

        return vo;
    }
}
