package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.annotation.DataScope;
import com.chain.ai.trade.member.entity.CreditsLog;

@DataScope(field = "user_id")
public interface CreditsLogMapper extends BaseMapper<CreditsLog> {
}
