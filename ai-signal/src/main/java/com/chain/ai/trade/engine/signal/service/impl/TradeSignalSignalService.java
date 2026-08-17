package com.chain.ai.trade.engine.signal.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.*;
import java.time.format.DateTimeFormatter;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.RedisLockConstants;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignalSignal;
import com.chain.ai.trade.engine.signal.entity.dto.KlineQueryParam;
import com.chain.ai.trade.engine.signal.entity.dto.NewSignalDTO;
import com.chain.ai.trade.engine.signal.entity.dto.TradeSignalSignalDto;
import com.chain.ai.trade.engine.signal.entity.vo.SignalSaveReqVO;
import com.chain.ai.trade.engine.signal.entity.vo.TradeSignalSignalVo;
import com.chain.ai.trade.engine.mapper.TradeSignalSignalMapper;
import com.chain.ai.trade.engine.signal.service.ITradeSignalSignalService;
import com.chain.ai.trade.common.utils.BeanUtil;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.utils.TradeSignalSignalUtils;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.chain.ai.trade.common.entity.constants.OrderAction.*;
import static com.chain.ai.trade.engine.signal.entity.constants.RedisLockConstants.KEY_SIGNAL_MULTI_TIME;


@Slf4j
@Service
public class TradeSignalSignalService extends ServiceImpl<TradeSignalSignalMapper, TradeSignalSignal>  implements ITradeSignalSignalService {

    @Autowired
    private RedisCache redisCache;

    @Override
    public Long createSignal(SignalSaveReqVO createReqVO) {

        //根据robotId,周期,K线时间判断是否已经存在
        if(isExist(createReqVO)!=null){
            return null;
        }


        // 插入
        TradeSignalSignal signal = cn.hutool.core.bean.BeanUtil.toBean(createReqVO, TradeSignalSignal.class);

        // 设置 orderAction（仅平仓信号需要）
        String signalStr = createReqVO.getSignalDTO().getSignal();
        log.info("保存信号: 原始信号={}, 是否平仓={}", signalStr, createReqVO.getSignalDTO().getIsCloseOrder());

        if ("CLOSE_LONG".equals(signalStr)) {
            // 平仓信号不设置 OrderAction，只用于信号记录
            signal.setOrderAction(null);
            log.info("平多信号: CLOSE_LONG");
        } else if ("CLOSE_SHORT".equals(signalStr)) {
            // 平仓信号不设置 OrderAction，只用于信号记录
            signal.setOrderAction(null);
            log.info("平空信号: CLOSE_SHORT");
        } else if ("LONG".equals(signalStr)) {
            // 开仓信号不设置 OrderAction，只用于信号记录
            signal.setOrderAction(null);
            log.info("开多信号: LONG");
        } else if ("SHORT".equals(signalStr)) {
            // 开仓信号不设置 OrderAction，只用于信号记录
            signal.setOrderAction(null);
            log.info("开空信号: SHORT");
        } else {
            log.warn("未知信号类型: {}", signalStr);
        }
        String key = String.format(KEY_SIGNAL_MULTI_TIME, createReqVO.getIndicatorType(),createReqVO.getDataFrom(),createReqVO.getSymbol(),createReqVO.getDataInterval(), DateUtil.strTimeToLong(createReqVO.getKlineTime()));
        TradeSignalSignal isValidSign = (TradeSignalSignal) redisCache.get(key);
        if(isValidSign!=null){
            log.info("{}:信号已经存在，不需要重新发送",createReqVO.getIndicatorType());
        }
        //写入缓存,包含开仓和平仓信号
        //key=indicatorType(机器人)+dataFrom+symbol+dataInterval+klineTime+signal
        if(("LONG".equals(createReqVO.getSignalDTO().getSignal())||"SHORT".equals(createReqVO.getSignalDTO().getSignal())||
                "CLOSE_LONG".equals(createReqVO.getSignalDTO().getSignal())||"CLOSE_SHORT".equals(createReqVO.getSignalDTO().getSignal()))){
            redisCache.put(key,signal);
        }
        try {
            this.save(signal);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 返回
        return signal.getId();
    }



    private Long isExist(SignalSaveReqVO createReqVO){
        List<TradeSignalSignal> list = this.lambdaQuery()
                .eq(TradeSignalSignal::getIndicatorType,createReqVO.getIndicatorType())
                .eq(TradeSignalSignal::getDataInterval,createReqVO.getDataInterval())
                .eq(TradeSignalSignal::getSymbol,createReqVO.getSymbol())
                .eq(TradeSignalSignal::getKlineTime,createReqVO.getKlineTime()).list();
        if(!list.isEmpty()){
            return list.get(0).getId();
        }
        return null;
    }
    @Override
    public TradeSignalSignal querySignal(TradeSignalSignalDto weightsDto, Candlestick candlestick) {
        CandlestickIntervalEnum kIntervalEnum = weightsDto.getKlineInterval();
       /* if (CandlestickIntervalEnum.MIN1.equals(weightsDto.getIntervalEnum())) {
            kIntervalEnum = CandlestickIntervalEnum.OKXMIN1;
        } else {
            log.error("查询新信号-当前周期不支持");
            throw new ServiceException("查询新信号-当前周期不支持");
        }*/
        return this.querySignal(weightsDto.getMemberPlatform().name(),
                kIntervalEnum.getCode(),
                weightsDto.getSymbol(), weightsDto.getRobotId(), candlestick.getTimeStr());
    }

    @Override
    public TradeSignalSignal querySignal(TradeSignalSignalDto weightsDto, Candlestick candlestick, List<CandlestickIntervalEnum> intervalEnumList) {
        TradeSignalSignal signalSignal = null;

        for(CandlestickIntervalEnum kIntervalEnum : intervalEnumList){
            CandlestickIntervalEnum kIntervalEnumQuery = convertToOkxInterval(kIntervalEnum);
            if(kIntervalEnumQuery == null) continue;

            // 精确时间对齐 - 找到包含该时间点的对应周期K线时间
            String alignedTime = alignKlineTimePrecise(candlestick.getTimeStr(), kIntervalEnum);
            if(alignedTime == null) continue;

            // 使用精确对齐的时间查询Redis
            String alignedKlineId = generateKlineId(alignedTime, kIntervalEnum);
            String key = String.format(KEY_SIGNAL_MULTI_TIME, weightsDto.getRobotId(),
                    weightsDto.getMemberPlatform().name(), weightsDto.getSymbol(),
                    kIntervalEnumQuery.getCode(), alignedKlineId);

            TradeSignalSignal signal = (TradeSignalSignal) redisCache.get(key);
            if(signal != null){
                log.info("Redis查询到{}周期信号: {}", kIntervalEnum, alignedTime);
                return createSignalResponse(signal);
            }

            // 使用精确对齐的时间查询数据库
            signalSignal = this.querySignalByAlignedTime(weightsDto, kIntervalEnumQuery, alignedTime);
            if(signalSignal != null){
                log.info("DB查询到{}周期信号: {}, {}", kIntervalEnum, alignedTime, signalSignal.getTrend());
                return signalSignal;
            }
        }
        return null;
    }

    /**
     * 精确时间对齐 - 找到包含原始时间点的对应周期K线时间
     */
    private String alignKlineTimePrecise(String originalTime, CandlestickIntervalEnum interval) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date originalDate = sdf.parse(originalTime);
            long originalTimestamp = originalDate.getTime();

            long intervalMillis = getIntervalMillis(interval);

            // 计算该周期K线的起始时间（向下取整）
            long alignedTimestamp = (originalTimestamp / intervalMillis) * intervalMillis;

            // 验证原始时间是否在该K线时间范围内
            long klineEndTime = alignedTimestamp + intervalMillis - 1;
            if (originalTimestamp >= alignedTimestamp && originalTimestamp <= klineEndTime) {
                return sdf.format(new Date(alignedTimestamp));
            } else {
                log.warn("时间对齐异常: 原始时间{}不在{}周期K线时间范围内", originalTime, interval);
                return null;
            }

        } catch (Exception e) {
            log.warn("时间对齐失败: {}, 使用原时间: {}", e.getMessage(), originalTime);
            return originalTime;
        }
    }

    /**
     * 根据对齐后的时间查询信号
     */
    private TradeSignalSignal querySignalByAlignedTime(TradeSignalSignalDto weightsDto,
                                                       CandlestickIntervalEnum interval, String alignedTime) {
        String symbol = weightsDto.getSymbol();
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }

        return this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, weightsDto.getMemberPlatform().name())
                .eq(TradeSignalSignal::getDataInterval, interval.getCode())
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, weightsDto.getRobotId())
                .eq(TradeSignalSignal::getKlineTime, alignedTime)
                .isNull(TradeSignalSignal::getOrderAction)
                .orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit 1")
                .one();
    }

    /**
     * 生成K线ID - 根据业务逻辑可能需要调整
     */
    private String generateKlineId(String timeStr, CandlestickIntervalEnum interval) {
        // 这里根据您的业务逻辑生成K线ID
        // 如果原本candlestick.getId()就是时间字符串，那么直接返回对齐后的时间
        // 如果需要其他格式，请按实际业务调整
        return timeStr;
    }

    // 周期毫秒数映射
    private long getIntervalMillis(CandlestickIntervalEnum interval) {
        switch(interval) {
            case MIN1: return 60 * 1000L;
            case MIN3: return 3 * 60 * 1000L;
            case MIN5: return 5 * 60 * 1000L;
            case MIN15: return 15 * 60 * 1000L;
            case MIN30: return 30 * 60 * 1000L;
            case MIN60: return 60 * 60 * 1000L;
            default: return 60 * 1000L;
        }
    }

    // 周期枚举转换
    private CandlestickIntervalEnum convertToOkxInterval(CandlestickIntervalEnum interval) {
        switch(interval) {
            case MIN1: return CandlestickIntervalEnum.OKXMIN1;
            case MIN3: return CandlestickIntervalEnum.OKXMIN3;
            case MIN5: return CandlestickIntervalEnum.OKXMIN5;
            case MIN15: return CandlestickIntervalEnum.OKXMIN15;
            case MIN30: return CandlestickIntervalEnum.OKXMIN30;
            case MIN60: return CandlestickIntervalEnum.OKXMIN60;
            case OKXMIN60: return CandlestickIntervalEnum.OKXMIN60;
            default: return null;
        }
    }

    private TradeSignalSignal createSignalResponse(TradeSignalSignal signal) {
        TradeSignalSignal response = new TradeSignalSignal();
        response.setTrend(signal.getTrend());
        response.setDataInterval(signal.getDataInterval());
        response.setId(signal.getId());
        return response;
    }

    @Override
    public TradeSignalSignal querySignal(String dataFrom, String dataInterval, String symbol, String indicatorType, String klineTime) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        return this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getDataInterval, dataInterval)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, indicatorType)
                .eq(TradeSignalSignal::getKlineTime, klineTime)
                .isNull(TradeSignalSignal::getOrderAction)
                .orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit 1")
                .one();
    }

    @Override
    public TradeSignalSignal querySignal(String dataFrom, String symbol, String indicatorType, String klineTime) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        return this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, indicatorType)
                .eq(TradeSignalSignal::getKlineTime, klineTime)
                .isNull(TradeSignalSignal::getOrderAction)
                .orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit 1")
                .one();
    }

    @Override
    public TradeSignalSignal queryBeforeSignal(String dataFrom, String dataInterval, String symbol, String indicatorType, String klineTime) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        return this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getDataInterval, dataInterval)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, indicatorType)
                .le(TradeSignalSignal::getKlineTime, klineTime)
                .orderByDesc(TradeSignalSignal::getKlineTime)
                .last(" limit 1")
                .one();
    }


    @Override
    public List<TradeSignalSignalVo> getSignalByQry(KlineQueryParam param) {
        String symbol = param.getSymbol();
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }

        // 构建查询条件 - 查询所有信号（包括已执行和未执行的）
        LambdaQueryWrapper<TradeSignalSignal> query = Wrappers.lambdaQuery(TradeSignalSignal.class)
                .eq(TradeSignalSignal::getDataFrom, param.getMemberPlatform().name())
                .eq(TradeSignalSignal::getDataInterval, param.getInterval().getCode())
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, param.getIndicatorType())
                .and(wq -> wq.like(TradeSignalSignal::getTrend, "LONG")
                        .or().like(TradeSignalSignal::getTrend, "SHORT")
                        .or().like(TradeSignalSignal::getTrend, "CLOSE_LONG")
                        .or().like(TradeSignalSignal::getTrend, "CLOSE_SHORT")
                        .or().like(TradeSignalSignal::getTrend, "LB")
                        .or().like(TradeSignalSignal::getTrend, "SB")
                        .or().like(TradeSignalSignal::getTrend, "SS")
                        .or().like(TradeSignalSignal::getTrend, "LS"));

        // 支持时间范围查询：如果提供了 from 和 to，使用时间范围；否则使用 timeStr
        if (param.getFrom() > 0 && param.getTo() > 0) {
            // 将秒级时间戳转换为时间字符串格式（yyyy-MM-dd HH:mm:ss）
            String fromTimeStr = DateUtil.formatDateTime(new Date(param.getFrom() * 1000));
            String toTimeStr = DateUtil.formatDateTime(new Date(param.getTo() * 1000));
            query.ge(TradeSignalSignal::getKlineTime, fromTimeStr)
                    .le(TradeSignalSignal::getKlineTime, toTimeStr);
        } else if (StringUtils.isNotEmpty(param.getTimeStr())) {
            // 兼容原有的 timeStr 参数（小于某个时间）
            query.lt(TradeSignalSignal::getKlineTime, param.getTimeStr());
        }

        query.orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit 100");

        List<TradeSignalSignal> tradeSignalSignals = this.list(query);
        log.info("信号查询结果: 找到 {} 个信号", tradeSignalSignals.size());

        List<TradeSignalSignalVo> result = BeanUtil.copyList(tradeSignalSignals,TradeSignalSignalVo.class);

        // 构建补充信号查询条件（确保不遗漏任何信号）
        // 虽然第一个查询已经包含所有信号，但这里作为补充查询确保数据完整性
        LambdaQueryWrapper<TradeSignalSignal> orderQuery = Wrappers.lambdaQuery(TradeSignalSignal.class)
                .eq(TradeSignalSignal::getDataFrom, param.getMemberPlatform().name())
                //.eq(TradeSignalSignal::getDataInterval, param.getInterval().getCode())
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, param.getIndicatorType());

        // 支持时间范围查询：如果提供了 from 和 to，使用时间范围；否则使用 timeStr
        if (param.getFrom() > 0 && param.getTo() > 0) {
            // 将秒级时间戳转换为时间字符串格式（yyyy-MM-dd HH:mm:ss）
            String fromTimeStr = DateUtil.formatDateTime(new Date(param.getFrom() * 1000));
            String toTimeStr = DateUtil.formatDateTime(new Date(param.getTo() * 1000));
            orderQuery.ge(TradeSignalSignal::getKlineTime, fromTimeStr)
                    .le(TradeSignalSignal::getKlineTime, toTimeStr);
        } else if (StringUtils.isNotEmpty(param.getTimeStr())) {
            // 兼容原有的 timeStr 参数（小于某个时间）
            orderQuery.lt(TradeSignalSignal::getKlineTime, param.getTimeStr());
        }

        orderQuery.orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit 5000");

        List<TradeSignalSignal> tradeOrderSignalSignals = this.list(orderQuery);


        if(!tradeOrderSignalSignals.isEmpty()){
            List<TradeSignalSignalVo> result1 = BeanUtil.copyList(tradeOrderSignalSignals,TradeSignalSignalVo.class);
            List<TradeSignalSignalVo> result2 = TradeSignalSignalUtils.deduplicate(result1);
            List<TradeSignalSignalVo> newSignals = TradeSignalSignalUtils.mergeAndSumTrend(result,result2);
            return newSignals;
        }

        return result;
    }

    @Override
    public List<TradeSignalSignal> getOrderActionSignals(KlineQueryParam param) {
        String symbol = param.getSymbol();
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }

        // 构建查询条件 - 查询所有信号（包括已执行和未执行的）
        LambdaQueryWrapper<TradeSignalSignal> query = Wrappers.lambdaQuery(TradeSignalSignal.class)
                .eq(TradeSignalSignal::getDataFrom, param.getMemberPlatform().name())
                .eq(TradeSignalSignal::getDataInterval, param.getInterval().getCode())
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, param.getIndicatorType())
                .and(wq -> wq.like(TradeSignalSignal::getTrend, "LONG")
                        .or().like(TradeSignalSignal::getTrend, "SHORT")
                        .or().like(TradeSignalSignal::getTrend, "LB")
                        .or().like(TradeSignalSignal::getTrend, "SB"));

        // 支持时间范围查询：如果提供了 from 和 to，使用时间范围；否则使用 timeStr
        if (param.getFrom() > 0 && param.getTo() > 0) {
            // 将秒级时间戳转换为时间字符串格式（yyyy-MM-dd HH:mm:ss）
            String fromTimeStr = DateUtil.formatDateTime(new Date(param.getFrom() * 1000));
            String toTimeStr = DateUtil.formatDateTime(new Date(param.getTo() * 1000));
            query.ge(TradeSignalSignal::getKlineTime, fromTimeStr)
                    .le(TradeSignalSignal::getKlineTime, toTimeStr);
        } else if (StringUtils.isNotEmpty(param.getTimeStr())) {
            // 兼容原有的 timeStr 参数（小于某个时间）
            query.lt(TradeSignalSignal::getKlineTime, param.getTimeStr());
        }

        query.orderByDesc(TradeSignalSignal::getCreateTime);



        List<TradeSignalSignal> tradeOrderSignalSignals = this.list(query);



        return tradeOrderSignalSignals;
    }

    /**
     * 保存订单信号数据
     * @return
     */
    @Override
    @Async
    public void saveOrderToSignal(TradeSignalSignalDto tradeOrder, Boolean buyFlag, String indicatorType) {
        String symbol = tradeOrder.getSymbol();
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        TradeSignalSignal signalSignal = new TradeSignalSignal();
        signalSignal.setDataFrom(tradeOrder.getMemberPlatform().name());
        CandlestickIntervalEnum kIntervalEnum = tradeOrder.getKlineInterval();
        if (CandlestickIntervalEnum.MIN1.equals(tradeOrder.getKlineInterval())) {
            kIntervalEnum = CandlestickIntervalEnum.OKXMIN1;
        }
        if (CandlestickIntervalEnum.MIN5.equals(tradeOrder.getKlineInterval())) {
            kIntervalEnum = CandlestickIntervalEnum.OKXMIN5;
        }
        signalSignal.setDataInterval(kIntervalEnum.getCode());
        signalSignal.setSymbol(symbol);
        signalSignal.setIndicatorType(indicatorType);
        signalSignal.setClosePrice(tradeOrder.getBuyPrice());
        signalSignal.setOrderSn(tradeOrder.getOrderSn());

        String signalTrend = tradeOrder.getSignalTrend();
        NewSignalDTO signalDTO = new NewSignalDTO();
        signalDTO.setWeight(signalTrend);
        String klineTime = DateUtil.toString(tradeOrder.getOrderTime());
        OrderAction signal;
        if (OrderSideEnum.BUY.equals(tradeOrder.getOrderSideEnum())) {
            signal = buyFlag ? OrderAction.OPEN_LONG : OrderAction.CLOSE_LONG;
        } else {
            signal = buyFlag ? OrderAction.OPEN_SHORT : OrderAction.CLOSE_SHORT;
        }
        signalSignal.setOrderAction(signal);
        signalDTO.setSignal(signal.name());
        signalDTO.setOrderSn(tradeOrder.getOrderSn());
        signalDTO.setOrderAmount(tradeOrder.getAmount());
        if (!buyFlag) {
            klineTime = DateUtil.toString(tradeOrder.getSellTime());
            // 设置订单相关内容
            if(tradeOrder.getSellPrice()!=null){
                signalDTO.setOrderPoint(tradeOrder.getOrderSideEnum().equals(OrderSideEnum.BUY)
                        ? tradeOrder.getSellPrice().subtract(tradeOrder.getBuyPrice())
                        : tradeOrder.getBuyPrice().subtract(tradeOrder.getSellPrice()));
            }
            signalDTO.setIncome(tradeOrder.getIncome());
            signalDTO.setFee(tradeOrder.getCharge());
            signalDTO.setRealIncome(tradeOrder.getIncome().subtract(tradeOrder.getCharge()));
            String today = klineTime.substring(0, 10);
            String todayIncomeKey = String.format(RedisLockConstants.KEY_TODAY_INCOME, tradeOrder.getAccountId(), tradeOrder.getRobotId(), today);
            String todayIncome = redisCache.getString(todayIncomeKey);
            signalDTO.setTodayIncome(ObjectUtil.isEmpty(todayIncome) ? BigDecimal.ZERO : new BigDecimal(todayIncome));
        }
        if (!klineTime.endsWith("00")) {
            klineTime = klineTime.substring(0, 17) + "00";
        }
        signalSignal.setKlineTime(klineTime);
        signalSignal.setTrend(JSONUtil.toJsonStr(signalDTO));
        this.save(signalSignal);
    }

    @Override
    public void saveOrderSignal(TradeSignalSignalDto tradeOrder, OrderAction orderAction, String indicatorType, Candlestick candlestick) {
        String symbol = tradeOrder.getSymbol();
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        TradeSignalSignal signalSignal = new TradeSignalSignal();
        signalSignal.setDataFrom(tradeOrder.getMemberPlatform().name());
        CandlestickIntervalEnum kIntervalEnum = tradeOrder.getKlineInterval();
        if (CandlestickIntervalEnum.MIN1.equals(tradeOrder.getKlineInterval())) {
            kIntervalEnum = CandlestickIntervalEnum.OKXMIN1;
        }
        if (CandlestickIntervalEnum.MIN5.equals(tradeOrder.getKlineInterval())) {
            kIntervalEnum = CandlestickIntervalEnum.OKXMIN5;
        }
        signalSignal.setDataInterval(kIntervalEnum.getCode());
        signalSignal.setSymbol(symbol);
        signalSignal.setIndicatorType(indicatorType);
        signalSignal.setClosePrice(tradeOrder.getBuyPrice());
        signalSignal.setOrderSn(tradeOrder.getOrderSn());

        String signalTrend = tradeOrder.getSignalTrend();
        NewSignalDTO signalDTO = new NewSignalDTO();
        signalDTO.setWeight(signalTrend);
        // 使用UTC时间基准存储信号时间
        String klineTime;
        if (tradeOrder.getKlineTime() != null && !tradeOrder.getKlineTime().isEmpty()) {
            // 优先使用DTO中设置的klineTime（yyyy-MM-dd HH:mm:ss格式）
            klineTime = tradeOrder.getKlineTime();
            log.info("使用DTO中的klineTime: {} (开仓信号)", klineTime);
        } else if (candlestick == null) {
            klineTime = DateUtil.getCurrentDateStr();
            log.debug("使用当前时间作为klineTime: {}", klineTime);
        } else {
            Instant instant = Instant.ofEpochMilli(candlestick.getId());
            ZonedDateTime utcTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
            klineTime = utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            log.debug("使用K线时间作为klineTime: {}", klineTime);
        }

        signalSignal.setOrderAction(orderAction);
        signalDTO.setSignal(orderAction.name());
        signalDTO.setOrderSn(tradeOrder.getOrderSn());
        signalDTO.setOrderAmount(tradeOrder.getAmount());
        if (LBSP==orderAction||SBSP==orderAction||OrderAction.CLOSE_SHORT==orderAction||OrderAction.CLOSE_LONG==orderAction
                ||OrderAction.LONG_GAIN==orderAction||OrderAction.LONG_LOSS==orderAction
                ||OrderAction.SHORT_GAIN==orderAction||OrderAction.SHORT_LOSS==orderAction) {
            // 使用UTC时间基准存储信号时间
            if (tradeOrder.getKlineTime() != null && !tradeOrder.getKlineTime().isEmpty()) {
                // 优先使用DTO中设置的klineTime（yyyy-MM-dd HH:mm:ss格式）
                klineTime = tradeOrder.getKlineTime();
                log.info("使用DTO中的klineTime(平仓): {}", klineTime);
            } else if (candlestick == null) {
                klineTime = DateUtil.getCurrentDateStr();
                log.debug("使用当前时间作为klineTime(平仓): {}", klineTime);
            } else {
                Instant instant = Instant.ofEpochMilli(candlestick.getId());
                ZonedDateTime utcTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
                klineTime = utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                log.debug("使用K线时间作为klineTime(平仓): {}", klineTime);
            }
            // 设置订单相关内容
            if(tradeOrder.getSellPrice()!=null){
                signalDTO.setOrderPoint(tradeOrder.getOrderSideEnum().equals(OrderSideEnum.BUY)
                        ? tradeOrder.getSellPrice().subtract(tradeOrder.getBuyPrice())
                        : tradeOrder.getBuyPrice().subtract(tradeOrder.getSellPrice()));
            }
            BigDecimal realIncome = tradeOrder.getIncome()==null?BigDecimal.ZERO:tradeOrder.getIncome();
            signalDTO.setIncome(realIncome);
            signalDTO.setFee(tradeOrder.getCharge());
            signalDTO.setRealIncome(realIncome.subtract(tradeOrder.getCharge()==null?BigDecimal.ZERO:tradeOrder.getCharge()));
            String today = klineTime.substring(0, 10);
            String todayIncomeKey = String.format(RedisLockConstants.KEY_TODAY_INCOME, tradeOrder.getAccountId(), tradeOrder.getRobotId(), today);
            String todayIncome = redisCache.getString(todayIncomeKey);
            signalDTO.setTodayIncome(ObjectUtil.isEmpty(todayIncome) ? BigDecimal.ZERO : new BigDecimal(todayIncome));
        }
        if (!klineTime.endsWith("00")) {
            //klineTime = klineTime.substring(0, 17) + "00";
        }
        signalSignal.setKlineTime(klineTime);
        signalSignal.setTrend(JSONUtil.toJsonStr(signalDTO));
        this.save(signalSignal);
    }

    @Override
    public TradeSignalSignal queryLatestOpenSignal(String dataFrom, String symbol, String indicatorType) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        // 查询最近的信号，然后在内存中过滤出开仓信号
        List<TradeSignalSignal> signals = this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, indicatorType)
                .isNull(TradeSignalSignal::getOrderAction) // 信号的 orderAction 为 null
                .orderByDesc(TradeSignalSignal::getKlineTime)  // 按K线时间降序，确保最新的信号排在前面
                .last(" limit 10") // 查询最近10条信号
                .list();

        // 在内存中过滤出开仓信号（LB 或 SB）
        for (TradeSignalSignal signal : signals) {
            if (signal.getTrend() != null) {
                try {
                    // 解析 JSON 中的 signal 字段
                    com.alibaba.fastjson.JSONObject trendObj = JSON.parseObject(signal.getTrend());
                    String signalType = trendObj.getString("signal");
                    if ("LONG".equals(signalType) || "SHORT".equals(signalType)
                            || "LB".equals(signalType) || "SB".equals(signalType)) {
                        return signal;
                    }
                } catch (Exception e) {
                    // 忽略解析错误，继续检查下一个
                }
            }
        }

        return null;
    }

    @Override
    public TradeSignalSignal queryLatestOpenSignal(String dataFrom, String symbol, String indicatorType, String robotId) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        // 查询最近的信号，然后在内存中过滤出指定机器人的开仓信号
        List<TradeSignalSignal> signals = this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, robotId)       // 机器人ID匹配indicatorType字段
                .eq(TradeSignalSignal::getStrategyType, indicatorType)  // 指标类型匹配strategyType字段
                .isNull(TradeSignalSignal::getOrderAction) // 信号的 orderAction 为 null
                .orderByDesc(TradeSignalSignal::getKlineTime)  // 按K线时间降序，确保最新的信号排在前面
                .last(" limit 10") // 查询最近10条信号
                .list();

        // 在内存中过滤出开仓信号（LONG 或 SHORT，兼容旧值 LB/SB）
        for (TradeSignalSignal signal : signals) {
            if (signal.getTrend() != null) {
                try {
                    // 解析 JSON 中的 signal 字段
                    com.alibaba.fastjson.JSONObject trendObj = JSON.parseObject(signal.getTrend());
                    String signalType = trendObj.getString("signal");
                    if ("LONG".equals(signalType) || "SHORT".equals(signalType)
                            || "LB".equals(signalType) || "SB".equals(signalType)) {
                        return signal;
                    }
                } catch (Exception e) {
                    // 忽略解析错误，继续检查下一个
                }
            }
        }

        return null;
    }

    @Override
    public TradeSignalSignal queryLatestOpenSignalBeforeTime(String dataFrom, String symbol, String indicatorType, String robotId, String beforeTime) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }
        // 查询指定时间之前的信号，获取该指标类型的开仓信号（不限制机器人）
        List<TradeSignalSignal> signals = this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, robotId)  // 指标类型匹配strategyType字段
                .isNull(TradeSignalSignal::getOrderAction) // 信号的 orderAction 为 null
                .lt(TradeSignalSignal::getKlineTime, beforeTime) // 时间过滤：查询指定时间之前的信号
                .orderByDesc(TradeSignalSignal::getKlineTime)  // 按K线时间降序，确保最新的信号排在前面
                .last(" limit 10") // 查询最近10条信号
                .list();

        // 在内存中过滤出开仓信号（LONG 或 SHORT，兼容旧值 LB/SB）
        for (TradeSignalSignal signal : signals) {
            if (signal.getTrend() != null) {
                try {
                    // 解析 JSON 中的 signal 字段
                    com.alibaba.fastjson.JSONObject trendObj = JSON.parseObject(signal.getTrend());
                    String signalType = trendObj.getString("signal");
                    if ("LONG".equals(signalType) || "SHORT".equals(signalType)
                            || "LB".equals(signalType) || "SB".equals(signalType)) {
                        return signal;
                    }
                } catch (Exception e) {
                    // 忽略解析错误，继续检查下一个
                }
            }
        }

        return null;
    }

    @Override
    public List<TradeSignalSignal> queryRecentSignals(String dataFrom, String dataInterval, String symbol, String indicatorType, int limit) {
        if (!symbol.endsWith("-SWAP")) {
            symbol = symbol + "-SWAP";
        }

        // 查询最近N条该机器人的信号，按创建时间倒序
        return this.lambdaQuery()
                .eq(TradeSignalSignal::getDataFrom, dataFrom)
                .eq(TradeSignalSignal::getDataInterval, dataInterval)
                .eq(TradeSignalSignal::getSymbol, symbol)
                .eq(TradeSignalSignal::getIndicatorType, indicatorType)
                .orderByDesc(TradeSignalSignal::getCreateTime)
                .last(" limit " + Math.min(limit, 50)) // 最多查询50条，避免过度查询
                .list();
    }
}
