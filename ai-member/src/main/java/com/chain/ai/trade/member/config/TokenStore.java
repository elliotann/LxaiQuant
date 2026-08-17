package com.chain.ai.trade.member.config;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    public void storeAccessToken(String token, String userId) {
    }

    public void storeRefreshToken(String token, String userId) {
    }

    public boolean isValidAccessToken(String token) {
        return !blacklistedTokens.contains(token);
    }

    public boolean isValidRefreshToken(String token) {
        return !blacklistedTokens.contains(token);
    }

    public void blacklistAccessToken(String token) {
        blacklistedTokens.add(token);
    }

    public void removeRefreshToken(String token) {
    }

    public void removeAccessToken(String token) {
    }
}
