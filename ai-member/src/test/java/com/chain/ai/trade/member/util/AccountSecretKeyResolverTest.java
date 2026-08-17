package com.chain.ai.trade.member.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class AccountSecretKeyResolverTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(AccountSecretKeyResolver.KEY_SYS_PROP);
        System.clearProperty(AccountSecretKeyResolver.ALLOW_DEFAULT_SYS_PROP);
    }

    @Test
    void shouldPreferSpringPropertyOverSystemProperty() {
        System.setProperty(AccountSecretKeyResolver.KEY_SYS_PROP, "SYS_KEY");
        MockEnvironment env = new MockEnvironment();
        env.setProperty(AccountSecretKeyResolver.KEY_PROPERTY, "PROP_KEY");
        assertEquals("PROP_KEY", AccountSecretKeyResolver.resolve(env));
    }

    @Test
    void shouldFallbackToSystemProperty() {
        System.setProperty(AccountSecretKeyResolver.KEY_SYS_PROP, "SYS_KEY");
        MockEnvironment env = new MockEnvironment();
        assertEquals("SYS_KEY", AccountSecretKeyResolver.resolve(env));
    }

    @Test
    void shouldReturnDefaultKeyWhenAllowed() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(AccountSecretKeyResolver.ALLOW_DEFAULT_PROPERTY, "true");
        String key = AccountSecretKeyResolver.resolve(env);
        assertNotNull(key);
        assertFalse(key.isBlank());
        assertEquals(AccountSecretKeyResolver.LEGACY_DEFAULT_KEY_BASE64, key);
    }

    @Test
    void shouldThrowWhenMissingAndNotAllowed() {
        MockEnvironment env = new MockEnvironment();
        assertThrows(IllegalStateException.class, () -> AccountSecretKeyResolver.resolveOrThrow(env));
    }
}
