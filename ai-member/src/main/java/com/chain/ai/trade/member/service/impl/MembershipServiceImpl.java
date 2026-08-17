package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.CreditPackage;
import com.chain.ai.trade.member.entity.MembershipBenefit;
import com.chain.ai.trade.member.mapper.CreditPackageMapper;
import com.chain.ai.trade.member.mapper.MembershipBenefitMapper;
import com.chain.ai.trade.member.service.IMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements IMembershipService {

    private final MembershipBenefitMapper membershipBenefitMapper;
    private final CreditPackageMapper creditPackageMapper;

    @Override
    public MembershipBenefit getBenefitByLevel(String level) {
        return membershipBenefitMapper.selectOne(
                new LambdaQueryWrapper<MembershipBenefit>()
                        .eq(MembershipBenefit::getLevel, level));
    }

    @Override
    public List<MembershipBenefit> listAllBenefits() {
        return membershipBenefitMapper.selectList(null);
    }

    @Override
    public List<CreditPackage> listCreditPackages() {
        return creditPackageMapper.selectList(
                new LambdaQueryWrapper<CreditPackage>()
                        .eq(CreditPackage::getEnabled, true)
                        .orderByAsc(CreditPackage::getSortOrder));
    }
}
