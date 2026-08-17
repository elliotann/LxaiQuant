package com.chain.ai.trade.engine.task.jobhandler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.xchange.factory.PlatformApiServiceFactory;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.chain.ai.trade.common.entity.constants.Exchange.OKX;

/**
 * 欧意K数据
 */
@Slf4j
@Component
public class OkxAllKlinesDataTaskExecute {
    @Autowired
    private ICandlestickService candlestickService;
    @XxlJob("okxAllKlinesDataTaskExecute")
    public void execute() {
        log.info("同步历史k线-获取OKX K线数据.....");
        String param = XxlJobHelper.getJobParam();
        JSONObject params = null;
        if(StringUtils.isNotEmpty(param)){
            log.info("同步历史k线-获取OKX K线数据参数{}.....",param);
            params = JSONUtil.parseObj(param);
        }
        if(params!=null&&StringUtils.isNotEmpty(params.getStr("symbols"))) {
            for (String symbol : params.getStr("symbols").split(",")) {

                CandlestickRequest request = CandlestickRequest.builder()
                        .symbol(symbol)
                        .interval(params.getEnum(CandlestickIntervalEnum.class,"interval"))
                        .accountId("3242342423")
                        .memberId("param.getMemberId()").build();
                request.setSize(10);
                if(params!=null&&params.getEnum(CandlestickIntervalEnum.class,"interval")!=null){
                    request.setInterval(params.getEnum(CandlestickIntervalEnum.class,"interval"));
                }

                if(params!=null&&params.getInt("size")!=null){
                    request.setSize(params.getInt("size"));
                }
                Long startTime=null;
                long curTime = System.currentTimeMillis() / 1000;
                if(params!=null&&params.getLong("startTime")!=null){
                    startTime = params.getLong("startTime");
                    request.setFrom(params.getLong("startTime")*1000);

                    List<Candlestick> calcKlines =null;
                    boolean lastFlag = false;
                    while (startTime<=(System.currentTimeMillis()/1000)){
                        log.info("同步历史k线-获取OKX K线数据参数 {}.....", DateUtil.longConvertDateTime(startTime));
                        request.setTo(startTime);
                        calcKlines = PlatformApiServiceFactory.getMarketService(OKX).getHistoryCandlestick(request);
                        candlestickService.batchSaveHistory(calcKlines);
                        if (lastFlag) {
                            break;
                        }
                        if(CandlestickIntervalEnum.OKXMIN30.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=30*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKXMIN60.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=60*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKXMIN5.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=5*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKXMIN3.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=3*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKXMIN1.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=1*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKXMIN15.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=15*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKX4HOUR.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=120*60*calcKlines.size();
                        }
                        if(CandlestickIntervalEnum.OKX1D.getCode().equals(params.getEnum(CandlestickIntervalEnum.class,"interval").getCode())){
                            startTime+=24*60*60*calcKlines.size();
                        }
                        // 保证最后一次同步
                        if (startTime >= curTime) {
                            startTime = curTime;
                            lastFlag = true;
                        }
                    }
                }


            }
        }
        log.info("获取OKX K线数据完成.....");

    }
}
