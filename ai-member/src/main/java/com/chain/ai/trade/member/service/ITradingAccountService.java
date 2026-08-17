package com.chain.ai.trade.member.service;


import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.member.entity.TradingAccount;

/**
 * 第三方账户服务接口
 *
 * 参考原工程实现，提供了完整的第三方账户管理功能
 */
public interface ITradingAccountService {

    /**
     * 根据ID获取账户信息
     * @param id 账户ID
     * @return 账户信息
     */
    TradingAccount getById(String id);

    /**
     * 根据账户ID获取账户信息
     * @param accountId 账户ID (Long类型)
     * @return 账户信息
     */
    TradingAccount getByAccountId(String accountId);

    /**
     * 根据用户ID和平台类型查询账户
     * 这是原工程中常用的查询方式
     * @param memberId 用户ID
     * @param platform 平台类型
     * @return 账户信息
     */
    TradingAccount getByMemberIdAndPlatform(Long memberId, Exchange platform);

    /**
     * 根据用户ID（字符串）和平台类型查询账户
     * @param memberId 用户ID（字符串格式）
     * @param platform 平台类型
     * @return 账户信息
     */
    TradingAccount getByMemberIdAndPlatform(String memberId, Exchange platform);

    /**
     * 获取所有有效的交易账户
     * @return 所有有效账户列表
     */
    java.util.List<TradingAccount> getAllAccounts();

    /**
     * 更新账户余额信息
     * @param accountId 账户ID
     * @param balances 余额信息
     * @return 是否更新成功
     */
    boolean updateAccountBalances(String accountId, java.util.Map<String, java.math.BigDecimal> balances);

    /**
     * 更新账户信息
     * @param account 账户实体
     */
    void updateAccount(TradingAccount account);
}
