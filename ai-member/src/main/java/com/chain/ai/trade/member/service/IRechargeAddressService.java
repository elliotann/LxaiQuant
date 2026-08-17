package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.RechargeAddress;

public interface IRechargeAddressService {

    RechargeAddress getOrCreateAddress(String userId, String businessType);
}
