package com.chain.ai.trade.engine.xchange.okx;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.xchange.IMarketService;
import com.chain.ai.trade.engine.xchange.utils.OkxOptions;
import com.chain.ai.trade.engine.xchange.utils.OkxRestConnection;
import com.chain.ai.trade.engine.xchange.utils.Options;
import com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * U本位合约市场行情
 */
@Service
public class OkxMarketService implements IMarketService {

    private Options options;
    public static final String REST_MARKET_DEPTH_PATH = "/market/depth";
    public static final String REST_CANDLESTICK_PATH = "/api/v5/market/candles";

    public static final String REST_HISTORY_CANDLESTICK_PATH = "/api/v5/market/history-candles";
    public static final String REST_MARKET_HISTORY_TRADE_PATH = "/linear-swap-ex/market/history/trade";
    public static final String OKX_REST_MARKET_NOW_PRICE_PATH = "/api/v5/public/mark-price";
    public static final String GET_POSITION_RATIO_PATH  = "/linear-swap-api/v1/swap_cross_account_info";

    public static final String OKX_GET_CONTRACT_INFO_PATH  = "/api/v5/public/instruments";

    private OkxRestConnection restConnection;
    public OkxMarketService(){
    }
    public OkxMarketService(Options options) {
        this.options = options;
        restConnection = new OkxRestConnection(options);
    }

    @Override
    public List<Candlestick> getHistoryCandlestick(CandlestickRequest request) {
        OkxRestConnection restConnection = new OkxRestConnection(OkxOptions.builder()
                .apiKey(request.getApiKey())
                .secretKey(request.getSecretKey())
                .passphrase(request.getPassphrase())
                .simulated(request.getSimulated())
                .build());
        // 参数构建
        UrlParamsBuilder paramBuilder = UrlParamsBuilder.build()
                .putToUrl("instId", request.getSymbol())
                .putToUrl("bar", request.getInterval().getCode());
        if(request.getFrom()!=0&&request.getTo()!=0){
            paramBuilder.putToUrl("after",request.getTo()*1000);
        }
        paramBuilder.putToUrl("limit", request.getSize());
        JSONObject json = restConnection.executeGet(REST_HISTORY_CANDLESTICK_PATH, paramBuilder);
        JSONArray data = json.getJSONArray("data");
        return parseArray(data,request.getSymbol(),request.getInterval());
    }

    public List<Candlestick> parseArray(JSONArray jsonArray, String symbol, CandlestickIntervalEnum candlestickIntervalEnum) {
        List<Candlestick> candlestickList = new ArrayList<>(jsonArray.size());
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONArray jsonObject = jsonArray.getJSONArray(i);
            candlestickList.add(parse(jsonObject,symbol,candlestickIntervalEnum));
        }
        return candlestickList;
    }
    public Candlestick parse(JSONArray json, String symbol, CandlestickIntervalEnum candlestickIntervalEnum) {
        int pricePrecision = 2;

        Candlestick candlestick = new Candlestick();
        candlestick.setId(json.getLong(0));
        candlestick.setOpenPrice(json.getBigDecimal(1).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        candlestick.setHighPrice(json.getBigDecimal(2).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        candlestick.setLowPrice(json.getBigDecimal(3).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        candlestick.setClosePrice(json.getBigDecimal(4).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        candlestick.setVolume(json.getBigDecimal(7).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        candlestick.setConfirm(json.getString(8));
        candlestick.setTimeStr(DateUtil.longConvertDateTime(candlestick.getId()));
        candlestick.setCandlestickIntervalEnum(candlestickIntervalEnum);
        candlestick.setSymbol(symbol);
        candlestick.setMarketType(MarketType.CRYPTO);
        candlestick.setExchange(Exchange.OKX);
        return candlestick;
    }


}
