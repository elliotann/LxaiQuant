package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.CreditsLog;

import java.util.List;

public interface ICreditsService {

    boolean deductCredits(String userId, int cost, String refId, String description);

    void addCredits(String userId, int amount, String type, String refId, String description);

    int getCreditsBalance(String userId);

    List<CreditsLog> getCreditsLogs(String userId, int page, int size);
}
