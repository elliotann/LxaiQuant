package com.chain.ai.trade.engine.xchange.okx;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.chain.ai.trade.engine.xchange.ITradeService;
import com.chain.ai.trade.engine.xchange.dto.MarketOrder;
import com.chain.ai.trade.engine.xchange.utils.InputChecker;
import com.chain.ai.trade.engine.xchange.utils.OkxOptions;
import com.chain.ai.trade.engine.xchange.utils.OkxRestConnection;
import com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;


import static com.chain.ai.trade.common.entity.constants.OrderSideEnum.BUY;
import static com.chain.ai.trade.common.entity.constants.OrderSideEnum.SELL;

@Slf4j
@Service
public class OkxTradeService implements ITradeService {
    public static final String OKX_CREATE_ORDER_PATH = "/api/v5/trade/order";
    @Override
    public String placeMarketOrder(MarketOrder request) throws IOException {
        OkxRestConnection restConnection = new OkxRestConnection(OkxOptions.builder()
                .apiKey(request.getApiKey())
                .secretKey(request.getSecretKey())
                .passphrase(request.getPassphrase())
                .simulated(request.getSimulated())
                .build());
        InputChecker.checker().checkSymbol(request.getSymbol())
                .shouldNotNull(request.getSymbol(), "instId")
                .shouldNotNull(request.getSide(), "side")

                .shouldNotNull(request.getAmount(), "amount");
        if(StringUtils.isEmpty(request.getTdMode())){
            request.setTdMode("cross");
        }


        UrlParamsBuilder builder = UrlParamsBuilder.build()
                .putToPost("instId", request.getSymbol())
                .putToPost("tdMode", request.getTdMode())
                .putToPost("clOrdId", request.getClientOrderId())
                .putToPost("ccy", "USDT")
                .putToPost("side", request.getSide().getCode())
                .putToPost("ordType","market")
                .putToPost("sz", request.getAmount())
                .putToPost("px", request.getPrice());
        if(request.getOffset().equals("close")){
            if(request.getSide().getCode().equals(BUY.getCode())){
                builder.putToPost("side", SELL.getCode());
                builder.putToPost("posSide", "long");
            }else{
                builder.putToPost("side", BUY.getCode());
                builder.putToPost("posSide", "short");
            }
        }else {
            if (BUY.getCode().equals(request.getSide().getCode())) {
                builder.putToPost("posSide", "long");
            } else if (SELL.getCode().equals(request.getSide().getCode())) {
                builder.putToPost("posSide", "short");
            }
        }
        // 开仓时附加固定止盈/止损
        if (!"close".equalsIgnoreCase(request.getOffset())) {
            if (request.getStopGain() != null && request.getStopGain().signum() > 0) {
                builder.putToPost("tpTriggerPx", request.getStopGain());
                builder.putToPost("tpOrdPx", request.getStopGain());
                builder.putToPost("tpTriggerPxType", "last");
            }
            if (request.getStopLoss() != null && request.getStopLoss().signum() > 0) {
                builder.putToPost("slTriggerPx", request.getStopLoss());
                builder.putToPost("slOrdPx", request.getStopLoss());
                builder.putToPost("slTriggerPxType", "last");
            }
            if (org.apache.commons.lang3.StringUtils.isNotBlank(request.getAlgoClOrdId())) {
                builder.putToPost("attachAlgoClOrdId", request.getAlgoClOrdId());
            }
        }
        log.info("请求平台参数:{}", JSONUtil.toJsonStr(builder.getPostBodyMap()));
        JSONObject jsonObject = restConnection.executePostWithSignature(OKX_CREATE_ORDER_PATH, builder);
        log.info("请求平台返回:{}", jsonObject!=null?jsonObject.toJSONString():"");
        if("51169".equals(jsonObject.getJSONArray("data").getJSONObject(0).getString("sCode"))){
            log.warn("无可用仓位");
            return null;
        }
        return jsonObject.getJSONArray("data").getJSONObject(0).getString("ordId");
    }
}
