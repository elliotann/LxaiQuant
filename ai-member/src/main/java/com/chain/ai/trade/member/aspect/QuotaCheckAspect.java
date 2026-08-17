package com.chain.ai.trade.member.aspect;

import com.chain.ai.trade.member.annotation.QuotaCheck;
import com.chain.ai.trade.member.entity.ApiCostConfig;
import com.chain.ai.trade.member.mapper.ApiCostConfigMapper;
import com.chain.ai.trade.member.service.ICreditsService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class QuotaCheckAspect {

    private final ICreditsService creditsService;
    private final ApiCostConfigMapper apiCostConfigMapper;

    @Around("@annotation(quotaCheck)")
    public Object checkQuota(ProceedingJoinPoint joinPoint, QuotaCheck quotaCheck) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("未认证");
        }

        String apiName = quotaCheck.apiName();
        ApiCostConfig config = apiCostConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiCostConfig>()
                        .eq(ApiCostConfig::getApiName, apiName));

        if (config == null || !config.getEnabled()) {
            throw new RuntimeException("API计费配置未启用");
        }

        String userId = (String) auth.getPrincipal();
        boolean deducted = creditsService.deductCredits(userId, config.getCostCredits(), apiName, config.getDescription());
        if (!deducted) {
            throw new RuntimeException("积分不足，需 " + config.getCostCredits() + " 积分");
        }

        return joinPoint.proceed();
    }
}
