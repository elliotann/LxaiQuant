package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一标的Mapper接口
 */
@Mapper
public interface SymbolsMapper extends BaseMapper<Symbol> {

}
