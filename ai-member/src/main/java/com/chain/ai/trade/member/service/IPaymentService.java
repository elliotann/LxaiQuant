package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.PaymentTransaction;

import java.math.BigDecimal;

public interface IPaymentService {

    PaymentTransaction createPayment(String userId, Integer planId, String type, BigDecimal amountUsdt, String paymentAddress);

    PaymentTransaction getPaymentById(String paymentId);

    PaymentTransaction getPaymentByTxId(String txId);

    boolean processCallback(String paymentId, String txId, String status);
}
