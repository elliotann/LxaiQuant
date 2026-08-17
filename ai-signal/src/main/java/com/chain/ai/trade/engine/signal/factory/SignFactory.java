package com.chain.ai.trade.engine.signal.factory;


import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.engine.signal.service.ISignService;
import com.chain.ai.trade.engine.signal.service.support.*;
import org.springframework.stereotype.Component;

@Component
public class SignFactory {
    public enum SignType{
        BOLL_RSI,
        MACD,
        FIB_BANDS,
        RANGE_FILTER,
        LOGREG_CHANNEL_TREND,
        SSL_CHANNEL,
        COMBINED,
        AI_TREND,
        AI_GRID,
        AI_MEAN_REVERSION,
        AI_BREAKOUT,
        AI_SCALPING,
        SMOOTH
    }
    public static ISignService getInstance(SignType signType){
        if (SignType.RANGE_FILTER.equals(signType)){
            return SpringContextUtil.getBean(PriceTrendChannelSignService.class);
        }
        if (SignType.MACD.equals(signType)){
            return SpringContextUtil.getBean(MacdSignService.class);
        }
        if (SignType.BOLL_RSI.equals(signType)){
            return SpringContextUtil.getBean(BollingerRsiSignService.class);
        }
        if (SignType.FIB_BANDS.equals(signType)){
            return SpringContextUtil.getBean(FibonacciBandsSignService.class);
        }
        if (SignType.SSL_CHANNEL.equals(signType)){
            return SpringContextUtil.getBean(SslChannelSignService.class);
        }
        if (SignType.LOGREG_CHANNEL_TREND.equals(signType)){
            return SpringContextUtil.getBean(LogRegChannelTrendSignService.class);
        }
        if (SignType.COMBINED.equals(signType)){
            return SpringContextUtil.getBean(CombinedSignService.class);
        }
        if (signType.name().startsWith("AI_")){
            return SpringContextUtil.getBean(AiSignService.class);
        }
        if (SignType.SMOOTH.equals(signType)){
            return SpringContextUtil.getBean(SmoothSignService.class);
        }
        throw new RuntimeException("不支持的信号类型");
    }
}
