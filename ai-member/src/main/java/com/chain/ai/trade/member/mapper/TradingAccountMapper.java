package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.chain.ai.trade.member.annotation.DataScope;
import com.chain.ai.trade.member.entity.TradingAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方账户Mapper接口
 */
@Mapper
@DataScope(field = "member_id")
public interface TradingAccountMapper extends BaseMapper<TradingAccount> {
}