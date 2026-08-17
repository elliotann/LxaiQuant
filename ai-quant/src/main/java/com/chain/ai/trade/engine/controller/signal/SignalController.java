package com.chain.ai.trade.engine.controller.signal;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.PriceTargetsInfo;
import com.chain.ai.trade.engine.signal.entity.query.TechnicalSignalQuery;
import com.chain.ai.trade.engine.signal.entity.vo.TechnicalSignalVO;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 信号数据查询服务（内部组件，供 OpenClawBridgeController 使用）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalController {

    private final ITechnicalSignalService technicalSignalService;
    private final ITradeSignalService tradeSignalService;

    /**
     * 根据K线范围查询信号信息
     */
    public ResponseEntity<Map<String, Object>> getSignalsData(
            String memberId,
            String thirdAccountId,
            String symbol,
            String interval,
            String dataInterval,
            String indicatorType,
            Integer pageNumber,
            Integer pageSize,
            Long from,
            Long to) {

        log.info("收到信号数据查询请求: memberId={}, thirdAccountId={}, symbol={}, interval={}, indicatorType={}, from={}, to={}",
                memberId, thirdAccountId, symbol, interval, indicatorType, from, to);

        try {

            // 构建技术信号查询参数
            TechnicalSignalQuery query = new TechnicalSignalQuery();
            query.setSymbol(symbol);
            query.setIndicator(indicatorType);

            // 设置时间周期
            if (interval != null) {
                query.setTimeframe(dataInterval);
            } else {
                query.setTimeframe("3m"); // 默认15分钟
            }

            // 设置分页参数
            query.setPageNum(pageNumber);
            query.setPageSize(pageSize);
            if (from != null) {
                query.setKlineTimestampStart(from);
            }
            if (to != null) {
                query.setKlineTimestampEnd(to);
            }

            // 调用服务层查询技术信号数据
            IPage<TechnicalSignal> pageResult =
                    technicalSignalService.pageTechnicalSignals(query);
            // 转换为VO对象
            List<TechnicalSignalVO> technicalSignals = pageResult.getRecords().stream()
                    .map(this::convertTechnicalSignalToVO)
                    .collect(Collectors.toList());

            // 查询业务信号数据
            List<TradeSignal> tradeSignals = null;
            if (from != null && to != null) {
                // 将时间戳转换为字符串格式进行查询
                String startTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(from));
                String endTimeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(to));
                tradeSignals = tradeSignalService.queryTradeSignalsByTimeRange(startTimeStr, endTimeStr);
            } else {
                // 如果没有时间范围限制，查询所有业务信号（可能需要分页，这里先简化处理）
                tradeSignals = tradeSignalService.list();
            }

            // 转换技术信号数据格式以符合前端期望
            List<Map<String, Object>> processedTechnicalSignals = technicalSignals.stream()
                    .map(this::convertTechnicalSignalToShowVO)
                    .toList();

            // 转换业务信号数据格式
            List<Map<String, Object>> processedTradeSignals = (tradeSignals != null) ?
                    tradeSignals.stream()
                            .map(this::convertTradeSignalToVO)
                            .collect(Collectors.toList()) :
                    List.of();

            // 合并技术信号和业务信号
            List<Map<String, Object>> processedSignals = new java.util.ArrayList<>();
            processedSignals.addAll(processedTechnicalSignals);
            processedSignals.addAll(processedTradeSignals);

            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("result", processedSignals);
            result.put("message", "查询成功");

            log.info("信号数据查询完成，返回{}条数据", processedSignals.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("查询信号数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询信号数据失败: " + e.getMessage());
            result.put("result", null);
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 将TechnicalSignal实体转换为VO对象
     */
    private TechnicalSignalVO convertTechnicalSignalToVO(com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal signal) {
        TechnicalSignalVO vo = new TechnicalSignalVO();
        vo.setId(signal.getId());
        vo.setSymbol(signal.getSymbol());
        vo.setTimeframe(signal.getTimeframe());
        vo.setKlineTime(signal.getKlineTime());
        vo.setKlineTimestamp(signal.getKlineTimestamp());
        vo.setOpenPrice(signal.getOpenPrice());
        vo.setClosePrice(signal.getClosePrice());
        vo.setHighPrice(signal.getHighPrice());
        vo.setLowPrice(signal.getLowPrice());
        vo.setVolume(signal.getVolume());
        vo.setIndicator(signal.getIndicator());
        vo.setStrategyName(signal.getStrategyName());
        vo.setTechnicalDirection(signal.getTechnicalDirection());
        vo.setSignalStrength(signal.getSignalStrength());
        vo.setIndicatorValue(signal.getIndicatorValue());
        vo.setThreshold(signal.getThreshold());
        vo.setSignalHash(signal.getSignalHash());
        vo.setExtraParams(signal.getExtraParams());

        // 转换 Date 为 String（使用毫秒时间戳）
        if (signal.getCreateTime() != null) {
            vo.setCreateTime(String.valueOf(signal.getCreateTime().getTime()));
        }

        return vo;
    }

    private  Map<String, Object> convertTechnicalSignalToShowVO(TechnicalSignalVO signal) {
        Map<String, Object> map = new HashMap<>();

        // 基本字段
        map.put("id", signal.getId());
        map.put("symbol", signal.getSymbol());
        map.put("signalType", signal.getTechnicalDirection()); // 使用getSignal()方法获取信号类型
        map.put("price", signal.getClosePrice()); // 使用收盘价作为信号价格
        String weight = signal.getSignalStrength()!=null?signal.getSignalStrength().toString():"";
        PriceTargetsInfo priceTargetsInfo = null;
        if(StringUtils.isNotEmpty(signal.getExtraParams())){
            priceTargetsInfo = JSONUtil.toBean(signal.getExtraParams(),PriceTargetsInfo.class);
        }
        String signalDesc = signal.getTechnicalDirection()+"("+weight+")";
        if(priceTargetsInfo!=null){
            signalDesc = signalDesc+" ("+priceTargetsInfo.getOptimalStopLoss()+"-"+priceTargetsInfo.getOptimalTakeProfit()+")";
        }
        map.put("description", signalDesc); // 使用getLable()方法获取描述

        // 时间戳字段（前端需要秒级时间戳）
        if (signal.getKlineTimestamp() != null) {
            map.put("timestamp", signal.getKlineTimestamp());
        }

        // 设置默认的position字段（前端可能需要）
        map.put("position", "overlay");


        return map;
    }

    /**
     * 将TradeSignal实体转换为前端期望的Map格式
     */
    private Map<String, Object> convertTradeSignalToVO(TradeSignal signal) {
        Map<String, Object> map = new HashMap<>();

        // 基本字段
        map.put("id", signal.getId());
        map.put("symbol", signal.getSymbol());

        // 从订单操作推导信号类型
        String signalType = convertOrderActionToSignalType(signal.getOrderAction());
        map.put("signalType", signalType);

        // 使用预计开仓价格作为信号价格
        map.put("price", signal.getExpectedPrice());

        // 描述信息：优先使用技术信号摘要，否则使用决策原因
        String description = signal.getOrderAction().getDescription();
        if (description == null || description.isEmpty()) {
            description = signal.getDecisionReason();
        }
        if (description == null || description.isEmpty()) {
            description = signal.getOrderAction() != null ? signal.getOrderAction().toString() : "业务信号";
        }
        if(signal.getPnlPercentage()!=null){
            description=description+"("+signal.getPnlPercentage()+")";
        }
        map.put("description", description);

        // 时间戳字段：将klineTime字符串转换为时间戳
        if (signal.getKlineTime() != null) {
            try {
                long timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .parse(signal.getKlineTime()).getTime();
                map.put("timestamp", timestamp);
            } catch (Exception e) {
                // 如果转换失败，使用创建时间
                if (signal.getCreateTime() != null) {
                    map.put("timestamp", signal.getCreateTime().getTime());
                }
            }
        } else if (signal.getCreateTime() != null) {
            map.put("timestamp", signal.getCreateTime().getTime());
        }

        // 设置默认的position字段
        map.put("position", "overlay");

        // 设置信号强度：基于仓位比例和优先级计算
        int signalStrength = 80; // 默认强度
        if (signal.getPositionRatio() != null) {
            // 仓位比例转换为强度 (0.1 -> 50, 1.0 -> 100)
            signalStrength = Math.max(50, Math.min(100, signal.getPositionRatio().multiply(new java.math.BigDecimal(100)).intValue()));
        } else if (signal.getPriority() != null) {
            // 优先级转换为强度 (1 -> 50, 10 -> 100)
            signalStrength = 45 + (signal.getPriority() * 5);
            signalStrength = Math.max(50, Math.min(100, signalStrength));
        }
        map.put("signalStrength", signalStrength);

        // 添加业务信号特有的字段标识
        map.put("signalSource", "trade"); // 标识这是业务信号而不是技术信号
        map.put("status", signal.getStatus() != null ? signal.getStatus().toString() : null);
        map.put("orderSn", signal.getOrderSn());

        return map;
    }

    /**
     * 将订单操作转换为信号类型
     */
    private String convertOrderActionToSignalType(OrderAction orderAction) {
        if (orderAction == null) {
            return "UNKNOWN";
        }

        switch (orderAction) {
            case OPEN_LONG:
                return "LONG"; // 多头开仓信号
            case OPEN_SHORT:
                return "SHORT"; // 空头开仓信号
            case CLOSE_LONG:
                return "CLOSE_LONG"; // 多头平仓信号
            case CLOSE_SHORT:
                return "CLOSE_SHORT"; // 空头平仓信号
            default:
                return orderAction.toString();
        }
    }
}
