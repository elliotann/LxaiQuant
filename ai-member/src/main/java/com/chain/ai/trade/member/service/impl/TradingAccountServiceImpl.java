package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.mapper.TradingAccountMapper;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 第三方账户服务实现类
 *
 * 参考原工程实现：
 * 1. 根据memberId查询账户
 * 2. 账户状态验证（启用状态、未删除）
 * 3. 支持多个账户的查询逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingAccountServiceImpl implements ITradingAccountService {

    private final TradingAccountMapper memberThirdAccountMapper;
    private final ObjectMapper objectMapper;

    @Override
    public TradingAccount getById(String id) {
        try {
            if (!StringUtils.hasText(id)) {
                log.warn("查询第三方账户失败: id为空");
                return null;
            }

            log.debug("查询第三方账户: id={}", id);
            TradingAccount account = memberThirdAccountMapper.selectById(id);

            // 验证账户状态
            if (account != null && !isAccountValid(account)) {
                log.warn("第三方账户状态无效: id={}", id);
                return null;
            }

            return account;
        } catch (Exception e) {
            log.error("查询第三方账户失败: id={}", id, e);
            return null;
        }
    }

    @Override
    public TradingAccount getByAccountId(String accountId) {
        try {
            if (accountId == null) {
                log.warn("查询第三方账户失败: accountId为空");
                return null;
            }

            log.debug("查询第三方账户: accountId={}", accountId);

            // 参考原工程实现：根据memberId查询账户
            // accountId通常就是memberId
            LambdaQueryWrapper<TradingAccount> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradingAccount::getId, String.valueOf(accountId));

            // 添加状态过滤条件（参考原工程）
            // wrapper.eq(MemberThirdAccount::getStatus, 1); // 启用状态
            // wrapper.eq(MemberThirdAccount::getDeleted, false); // 未删除
            wrapper.eq(TradingAccount::getBindStatus, TradingAccount.BindStatus.BIND); // 已绑定

            TradingAccount account = memberThirdAccountMapper.selectOne(wrapper);
            if (account != null && isAccountValid(account)) {
                return account;
            }

            log.warn("未找到有效的第三方账户: accountId={}", accountId);
            return null;

        } catch (Exception e) {
            log.error("查询第三方账户失败: accountId={}", accountId, e);
            return null;
        }
    }

    /**
     * 验证账户是否有效
     * 参考原工程的账户验证逻辑
     */
    private boolean isAccountValid(TradingAccount account) {
        if (account == null) {
            return false;
        }

        // 检查平台类型
        if (account.getMemberPlatform() == null) {
            log.warn("第三方账户平台类型为空: id={}", account.getId());
            return false;
        }

        // 兼容历史数据：检查加密字段或明文字段
        // 新数据使用加密字段，历史数据使用明文字段
        boolean hasEncryptedKeys = StringUtils.hasText(account.getApiKeyEnc()) && 
                                   StringUtils.hasText(account.getApiSecretEnc());
        
        // 注意：这里假设历史数据有对应的明文字段，如果需要可以添加明文字段检查
        // 由于实体类中只有加密字段定义，这里暂时只检查加密字段
        
        if (!hasEncryptedKeys) {
            log.warn("第三方账户API密钥不完整（兼容历史数据）: id={}", account.getId());
            // 为了兼容历史数据，暂时返回true，允许通过验证
            // 在实际业务中，这里应该根据具体情况决定是否允许
            return true;
        }

        // 这里可以添加更多验证逻辑，比如：
        // - 账户状态检查
        // - 删除标志检查
        // - 过期时间检查
        // - 权限验证

        return true;
    }

    /**
     * 根据用户ID和平台类型查询账户
     * 这是原工程中常用的查询方式
     */
    public TradingAccount getByMemberIdAndPlatform(Long memberId, Exchange platform) {
        try {
            if (memberId == null || platform == null) {
                log.warn("查询第三方账户失败: 参数为空 memberId={}, platform={}", memberId, platform);
                return null;
            }

            log.debug("根据用户ID和平台查询账户: memberId={}, platform={}", memberId, platform);

            LambdaQueryWrapper<TradingAccount> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradingAccount::getMemberId, String.valueOf(memberId));
            wrapper.eq(TradingAccount::getMemberPlatform, platform);

            // 添加状态过滤（参考原工程）
            wrapper.eq(TradingAccount::getBindStatus, TradingAccount.BindStatus.BIND);
            // wrapper.eq(MemberThirdAccount::getStatus, 1); // 如果有status字段
            // wrapper.eq(MemberThirdAccount::getDeleted, false); // 如果有deleted字段

            TradingAccount account = memberThirdAccountMapper.selectOne(wrapper);
            if (account != null && isAccountValid(account)) {
                return account;
            }

            log.warn("未找到有效的第三方账户: memberId={}, platform={}", memberId, platform);
            return null;

        } catch (Exception e) {
            log.error("根据用户ID和平台查询账户失败: memberId={}, platform={}", memberId, platform, e);
            return null;
        }
    }

    /**
     * 根据用户ID（字符串）和平台类型查询账户
     */
    @Override
    public TradingAccount getByMemberIdAndPlatform(String memberId, Exchange platform) {
        try {
            if (!StringUtils.hasText(memberId) || platform == null) {
                log.warn("查询第三方账户失败: 参数为空 memberId={}, platform={}", memberId, platform);
                return null;
            }

            log.debug("根据用户ID（字符串）和平台查询账户: memberId={}, platform={}", memberId, platform);

            LambdaQueryWrapper<TradingAccount> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradingAccount::getMemberId, memberId);
            wrapper.eq(TradingAccount::getMemberPlatform, platform);

            // 添加状态过滤
            wrapper.eq(TradingAccount::getBindStatus, TradingAccount.BindStatus.BIND);

            TradingAccount account = memberThirdAccountMapper.selectOne(wrapper);
            if (account != null && isAccountValid(account)) {
                return account;
            }

            log.warn("未找到有效的第三方账户: memberId={}, platform={}", memberId, platform);
            return null;

        } catch (Exception e) {
            log.error("根据用户ID（字符串）和平台查询账户失败: memberId={}, platform={}", memberId, platform, e);
            return null;
        }
    }

    /**
     * 获取所有有效的交易账户
     * 管理员查询所有账户，非管理员只能查询自己的账户
     */
    @Override
    public List<TradingAccount> getAllAccounts() {
        try {
            log.debug("查询所有第三方账户");

            LambdaQueryWrapper<TradingAccount> wrapper = new LambdaQueryWrapper<>();
            // 判断当前用户是否为管理员
            if (!isCurrentUserAdmin()) {
                // 非管理员只能查询自己的账户
                String currentMemberId = getCurrentMemberId();
                if (currentMemberId != null) {
                    wrapper.eq(TradingAccount::getMemberId, currentMemberId);
                }
            }

            List<TradingAccount> accounts = memberThirdAccountMapper.selectList(wrapper);

            log.debug("查询到账户数量: {}", accounts.size());
            return accounts;

        } catch (Exception e) {
            log.error("查询所有第三方账户失败", e);
            return List.of(); // 返回空列表而不是null
        }
    }

    /**
     * 更新账户余额信息
     */
    @Override
    public boolean updateAccountBalances(String accountId, Map<String, BigDecimal> balances) {
        if (!StringUtils.hasText(accountId) || balances == null || balances.isEmpty()) {
            log.warn("更新账户余额失败: accountId或balances为空");
            return false;
        }
        
        try {
            // 查询账户
            TradingAccount account = getById(accountId);
            if (account == null) {
                log.warn("更新账户余额失败: 账户不存在: accountId={}", accountId);
                return false;
            }
            
            // 序列化余额信息为JSON
            String balancesJson = objectMapper.writeValueAsString(balances);
            account.setBalances(balancesJson);
            
            // 更新数据库
            updateAccount(account);
            log.info("账户 {} 余额更新成功: {}", accountId, balances);
            return true;
            
        } catch (Exception e) {
            log.error("更新账户余额失败: accountId={}, error={}", accountId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void updateAccount(TradingAccount account) {
        if (account == null || !StringUtils.hasText(account.getId())) {
            log.warn("更新账户失败: 账户或ID为空");
            return;
        }
        memberThirdAccountMapper.updateById(account);
    }

    /**
     * 从 SecurityContext 获取当前登录用户ID
     */
    private String getCurrentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof String ? (String) principal : null;
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
