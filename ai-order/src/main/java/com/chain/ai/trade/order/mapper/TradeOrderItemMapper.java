package com.chain.ai.trade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易订单明细Mapper接口
 */
@Mapper
public interface TradeOrderItemMapper extends BaseMapper<TradeEntry> {

}