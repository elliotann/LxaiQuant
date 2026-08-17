package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.PaymentTransaction;
import com.chain.ai.trade.member.mapper.PaymentTransactionMapper;
import com.chain.ai.trade.member.service.IPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final PaymentTransactionMapper paymentTransactionMapper;

    @Override
    @Transactional
    public PaymentTransaction createPayment(String userId, Integer planId, String type, BigDecimal amountUsdt, String paymentAddress) {
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .type(type)
                .planId(planId)
                .amountUsdt(amountUsdt)
                .paymentCurrency("USDT")
                .paymentAddress(paymentAddress)
                .status("PENDING")
                .expireAt(LocalDateTime.now().plusMinutes(30))
                .createTime(LocalDateTime.now())
                .build();
        paymentTransactionMapper.insert(tx);
        return tx;
    }

    @Override
    public PaymentTransaction getPaymentById(String paymentId) {
        return paymentTransactionMapper.selectById(paymentId);
    }

    @Override
    public PaymentTransaction getPaymentByTxId(String txId) {
        return paymentTransactionMapper.selectOne(
                new LambdaQueryWrapper<PaymentTransaction>()
                        .eq(PaymentTransaction::getTxId, txId));
    }

    @Override
    @Transactional
    public boolean processCallback(String paymentId, String txId, String status) {
        PaymentTransaction tx = paymentTransactionMapper.selectById(paymentId);
        if (tx == null || !"PENDING".equals(tx.getStatus())) {
            return false;
        }
        tx.setTxId(txId);
        tx.setStatus(status);
        tx.setCompletedAt(LocalDateTime.now());
        paymentTransactionMapper.updateById(tx);
        return true;
    }
}
