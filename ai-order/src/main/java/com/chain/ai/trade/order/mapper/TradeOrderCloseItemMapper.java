package com.chain.ai.trade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.order.entity.dos.TradeExitItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易订单平仓明细Mapper接口
 */
@Mapper
public interface TradeOrderCloseItemMapper extends BaseMapper<TradeExitItem> {

}