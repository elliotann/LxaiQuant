package com.chain.ai.trade.member.controller;

import com.chain.ai.trade.member.entity.CreditPackage;
import com.chain.ai.trade.member.entity.PaymentTransaction;
import com.chain.ai.trade.member.entity.RechargeAddress;
import com.chain.ai.trade.member.mapper.CreditPackageMapper;
import com.chain.ai.trade.member.service.ICreditsService;
import com.chain.ai.trade.member.service.IPaymentService;
import com.chain.ai.trade.member.service.IRechargeAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IPaymentService paymentService;
    private final ICreditsService creditsService;
    private final CreditPackageMapper creditPackageMapper;
    private final IRechargeAddressService rechargeAddressService;

    @PostMapping("/credits")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> createCreditsPayment(Authentication auth, @RequestBody Map<String, Object> data) {
        String userId = (String) auth.getPrincipal();
        Object pkgIdObj = data.get("packageId");
        if (!(pkgIdObj instanceof Integer)) {
            return failure("packageId 参数无效");
        }
        Integer packageId = (Integer) pkgIdObj;

        CreditPackage pkg = creditPackageMapper.selectById(packageId);
        if (pkg == null || !pkg.getEnabled()) {
            return failure("积分包不存在或已禁用");
        }

        RechargeAddress addr = rechargeAddressService.getOrCreateAddress(userId, "RECHARGE");
        PaymentTransaction tx = paymentService.createPayment(userId, packageId, "CREDITS_PACKAGE", pkg.getPriceUsdt(), addr.getRechargeAddress());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("paymentId", tx.getId());
        result.put("paymentAddress", addr.getRechargeAddress());
        result.put("amountUsdt", pkg.getPriceUsdt());
        result.put("credits", pkg.getCredits());
        result.put("bonusCredits", pkg.getBonusCredits());
        return result;
    }

    @PostMapping("/callback")
    public Map<String, Object> paymentCallback(@RequestBody Map<String, Object> data) {
        String paymentId = (String) data.get("paymentId");
        String txId = (String) data.get("txId");
        String status = (String) data.get("status");

        boolean ok = paymentService.processCallback(paymentId, txId, status);
        if (ok && "SUCCESS".equals(status)) {
            PaymentTransaction tx = paymentService.getPaymentById(paymentId);
            if (tx != null && "CREDITS_PACKAGE".equals(tx.getType())) {
                CreditPackage pkg = creditPackageMapper.selectById(tx.getPlanId());
                if (pkg != null) {
                    creditsService.addCredits(tx.getUserId(),
                            pkg.getCredits() + pkg.getBonusCredits(),
                            "PURCHASE", tx.getId(),
                            "购买积分包: " + pkg.getName());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        return result;
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getPayment(@PathVariable String paymentId, Authentication auth) {
        PaymentTransaction tx = paymentService.getPaymentById(paymentId);
        if (tx == null) {
            return failure("订单不存在");
        }
        String userId = (String) auth.getPrincipal();
        if (!tx.getUserId().equals(userId)) {
            return failure("无权查看此订单");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("payment", tx);
        return result;
    }

    @GetMapping("/recharge-address")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getRechargeAddress(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        RechargeAddress addr = rechargeAddressService.getOrCreateAddress(userId, "RECHARGE");
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("rechargeAddress", addr.getRechargeAddress());
        return result;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
