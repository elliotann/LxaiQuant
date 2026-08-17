package com.chain.ai.trade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易订单Mapper接口
 */
@Mapper
public interface TradeOrderMapper extends BaseMapper<TradePosition> {

}