package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.annotation.DataScope;
import com.chain.ai.trade.member.entity.PaymentTransaction;

@DataScope(field = "user_id")
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {
}
