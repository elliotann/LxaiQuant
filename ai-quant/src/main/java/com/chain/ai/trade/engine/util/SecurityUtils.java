package com.chain.ai.trade.engine.util;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类，从 JWT token 中提取当前用户信息
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户的 ID，由 JwtAuthFilter 从 JWT token 中解析并设置
     */
    public static String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("无法获取当前用户身份，请检查是否已登录");
        }
        return auth.getName();
    }
}
