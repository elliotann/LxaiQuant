package com.chain.ai.trade.engine.xchange.factory;



import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.engine.xchange.IMarketService;
import com.chain.ai.trade.engine.xchange.ITradeService;
import com.chain.ai.trade.engine.xchange.okx.OkxTradeService;


public class PlatformApiServiceFactory {
    public static ITradeService getTradeService(Exchange platform){
        if(Exchange.OKX.name().equals(platform.name())){
            return SpringContextUtil.getBean(OkxTradeService.class);
        }
        return null;
    }
    public static IMarketService getMarketService(Exchange platform){
        if(Exchange.OKX.name().equals(platform.name())){
            return SpringContextUtil.getBean(IMarketService.class);
        }
        return null;
    }
}
