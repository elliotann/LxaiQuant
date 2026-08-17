package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.annotation.DataScope;
import com.chain.ai.trade.member.entity.MembershipSubscription;

@DataScope(field = "user_id")
public interface MembershipSubscriptionMapper extends BaseMapper<MembershipSubscription> {
}
