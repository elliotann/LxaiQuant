package com.chain.ai.trade.member.util;

import org.springframework.core.env.Environment;

public final class AccountSecretKeyResolver {

    public static final String KEY_PROPERTY = "account.secrets.key";
    public static final String ALLOW_DEFAULT_PROPERTY = "account.secrets.allowDefault";
    public static final String KEY_SYS_PROP = "ACCOUNT_SECRET_KEY";
    public static final String ALLOW_DEFAULT_SYS_PROP = "ACCOUNT_SECRET_KEY_ALLOW_DEFAULT";
    public static final String LEGACY_DEFAULT_KEY_BASE64 = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";

    private AccountSecretKeyResolver() {
    }

    public static String resolve(Environment environment) {
        String k = firstNonBlank(
                environment == null ? null : environment.getProperty(KEY_PROPERTY),
                System.getProperty(KEY_SYS_PROP),
                System.getenv(KEY_SYS_PROP)
        );
        if (k != null) {
            return k;
        }
        if (allowDefault(environment)) {
            return defaultDevKeyBase64();
        }
        return null;
    }

    public static String resolveOrThrow(Environment environment) {
        String key = resolve(environment);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(KEY_SYS_PROP + " 未配置");
        }
        return key;
    }

    public static boolean allowDefault(Environment environment) {
        if (Boolean.getBoolean(ALLOW_DEFAULT_SYS_PROP)) {
            return true;
        }
        String allow = environment == null ? null : environment.getProperty(ALLOW_DEFAULT_PROPERTY);
        if ("true".equalsIgnoreCase(allow)) {
            return true;
        }
        String profiles = environment == null ? null : environment.getProperty("spring.profiles.active");
        if (profiles == null || profiles.isBlank()) {
            profiles = environment == null ? null : environment.getProperty("spring.profiles.default");
        }
        if (profiles == null) {
            profiles = "";
        }
        String p = profiles.toLowerCase();
        return p.contains("dev") || p.contains("local");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String defaultDevKeyBase64() {
        return LEGACY_DEFAULT_KEY_BASE64;
    }
}
