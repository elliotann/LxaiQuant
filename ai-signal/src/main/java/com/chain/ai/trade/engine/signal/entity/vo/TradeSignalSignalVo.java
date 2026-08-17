package com.chain.ai.trade.engine.signal.entity.vo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chain.ai.trade.common.entity.constants.OrderAction;

import com.chain.ai.trade.common.utils.DateUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
// import com.google.gson.JsonElement;
// import com.google.gson.JsonParser;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.ANY)
@Data
public class TradeSignalSignalVo {

    private Long id;

    private Long timestamp;

    private String lable;

    @ApiModelProperty(value = "币种")
    private String symbol;

    private String signal;

    private String klineTime;

    private OrderAction orderAction;

    private Set<OrderAction> orderActions;

    private Set<String> trends;

    private String orderActionLable;

    private BigDecimal closePrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;


    private String income;

    private String weight;

    @ApiModelProperty(value = "数据来源")
    private String dataFrom;

    @ApiModelProperty(value = "数据周期")
    private String dataInterval;

    @ApiModelProperty(value = "指标名称")
    private String indicatorType;

    @ApiModelProperty(value = "趋势")
    private String trend;

    public String getSignal() {
        if(StringUtils.isNotEmpty(trend)){
            JsonElement element = JsonParser.parseString(trend);
            if (element.isJsonObject()){
                return JSONUtil.parseObj(trend).getStr("signal");
            }
            if (element.isJsonArray()){
                return JSONUtil.parseArray(trend).getJSONObject(0).getStr("signal");
            }


        }
        return signal;
    }

    public String getIncome() {

        if(CollUtil.isNotEmpty(trends)){
            BigDecimal sumIncom=BigDecimal.ZERO;
            for(String trendNew:trends){
                if (JSONUtil.parseObj(trendNew).getBigDecimal("income")!=null){
                    sumIncom=sumIncom.add(JSONUtil.parseObj(trendNew).getBigDecimal("income"));
                }
            }
            if(sumIncom.compareTo(BigDecimal.ZERO)!=0){
                return sumIncom.toString();
            }
        }else{
            if (JSONUtil.parseObj(trend).getBigDecimal("income")!=null){
                return JSONUtil.parseObj(trend).getBigDecimal("income").toString();
            }
        }
        return income;
    }

    public String getWeight() {
        JsonElement element = JsonParser.parseString(trend);
        if (element.isJsonObject()){
            return JSONUtil.parseObj(trend).getStr("weight");
        }
        if (element.isJsonArray()){
            return JSONUtil.parseArray(trend).getJSONObject(0).getStr("weight");
        }
        return weight;
    }

    public Long getTimestamp() {
        if(StringUtils.isNotEmpty(klineTime)){
            return DateUtil.strTimeToLong(klineTime);
        }
        return timestamp;
    }

    public String getLable() {
        signal=getSignal();
        StringBuffer result = new StringBuffer();

        if(StringUtils.isNotEmpty(signal)){
            if("SHORT".equals(signal)){
                result.append("多");
            }

            if("LONG".equals(signal)){
                result.append("空");
            }
            if("CLOSE_LONG".equals(signal)){
                result.append("平多");
            }
            if("CLOSE_SHORT".equals(signal)){
                result.append("平空");
            }
        }

        // 添加权重信息
        String weight = getWeight();
        if(StringUtils.isNotEmpty(weight)){
            result.append("(").append(weight).append(")");
        }

        if(this.getOrderActions()!=null&&!this.getOrderActions().isEmpty()){
            for(OrderAction orderAction1:this.getOrderActions()){
                if(StringUtils.isNotEmpty(result)){
                    result.append("-");
                }
                result.append(orderAction1.getLabel());
            }
        }else{
            if(orderAction!=null){
                if(StringUtils.isNotEmpty(result)){
                    result.append("-");
                }
                result.append(orderAction.getLabel());
            }
        }
        return result.toString();
    }

    public void addOrderAction(OrderAction orderAction){
        if(orderActions==null){
            orderActions = new HashSet<>();
        }
        orderActions.add(orderAction);
    }
    public void addTrend(String trend){
        if(trends==null){
            trends = new HashSet<>();
        }
        trends.add(trend);
    }
    public void addTrendList(Set<String> trend){
        if(trends==null){
            trends = new HashSet<>();
        }
        trends.addAll(trend);
    }

    public Set<OrderAction> getOrderActions() {
        return orderActions;
    }

    public void setOrderActions(Set<OrderAction> orderActions) {
        this.orderActions = orderActions;
    }
}
