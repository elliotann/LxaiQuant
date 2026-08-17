package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.CreditsLog;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.mapper.CreditsLogMapper;
import com.chain.ai.trade.member.mapper.UserMapper;
import com.chain.ai.trade.member.service.ICreditsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditsServiceImpl implements ICreditsService {

    private final UserMapper userMapper;
    private final CreditsLogMapper creditsLogMapper;

    @Override
    @Transactional
    public boolean deductCredits(String userId, int cost, String refId, String description) {
        int rows = userMapper.deductCredits(userId, cost);
        if (rows == 0) {
            return false;
        }
        User user = userMapper.selectById(userId);
        CreditsLog log = CreditsLog.builder()
                .userId(userId).amount(-cost)
                .balanceAfter(user.getCreditsBalance())
                .type("API_COST")
                .refId(refId).description(description)
                .build();
        creditsLogMapper.insert(log);
        return true;
    }

    @Override
    @Transactional
    public void addCredits(String userId, int amount, String type, String refId, String description) {
        userMapper.addCredits(userId, amount);
        User user = userMapper.selectById(userId);
        CreditsLog log = CreditsLog.builder()
                .userId(userId).amount(amount)
                .balanceAfter(user.getCreditsBalance())
                .type(type)
                .refId(refId).description(description)
                .build();
        creditsLogMapper.insert(log);
    }

    @Override
    public int getCreditsBalance(String userId) {
        User user = userMapper.selectById(userId);
        return user != null ? user.getCreditsBalance() : 0;
    }

    @Override
    public List<CreditsLog> getCreditsLogs(String userId, int page, int size) {
        return creditsLogMapper.selectList(
                new LambdaQueryWrapper<CreditsLog>()
                        .eq(CreditsLog::getUserId, userId)
                        .orderByDesc(CreditsLog::getCreatedAt)
                        .last("LIMIT " + size + " OFFSET " + (page - 1) * size));
    }
}
