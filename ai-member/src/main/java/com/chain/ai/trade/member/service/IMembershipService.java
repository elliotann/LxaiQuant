package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.MembershipBenefit;
import com.chain.ai.trade.member.entity.CreditPackage;

import java.util.List;

public interface IMembershipService {

    MembershipBenefit getBenefitByLevel(String level);

    List<MembershipBenefit> listAllBenefits();

    List<CreditPackage> listCreditPackages();
}
