package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import org.apache.ibatis.annotations.Mapper;

/**
 * K线数据Mapper接口
 */
@Mapper
public interface CandlestickMapper extends BaseMapper<Candlestick> {

}