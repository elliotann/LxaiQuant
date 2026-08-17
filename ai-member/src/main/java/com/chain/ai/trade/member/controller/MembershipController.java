package com.chain.ai.trade.member.controller;

import com.chain.ai.trade.member.entity.CreditPackage;
import com.chain.ai.trade.member.entity.MembershipBenefit;
import com.chain.ai.trade.member.service.ICreditsService;
import com.chain.ai.trade.member.service.IMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final IMembershipService membershipService;
    private final ICreditsService creditsService;

    @GetMapping("/benefits")
    public Map<String, Object> listBenefits() {
        List<MembershipBenefit> benefits = membershipService.listAllBenefits();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("benefits", benefits);
        return result;
    }

    @GetMapping("/packages")
    public Map<String, Object> listPackages() {
        List<CreditPackage> packages = membershipService.listCreditPackages();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("packages", packages);
        return result;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getMyMembership(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        int balance = creditsService.getCreditsBalance(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("creditsBalance", balance);
        return result;
    }
}
