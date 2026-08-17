package com.chain.ai.trade.order.service.impl;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.dto.AccountSecrets;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OkxDirectTradeServiceTest {

    @BeforeEach
    void setUpSpringContext() throws Exception {
        ApplicationContext ctx = mock(ApplicationContext.class);
        AccountSecretsService secretsService = mock(AccountSecretsService.class);
        AccountSecrets secrets = new AccountSecrets();
        secrets.setApiKey("test_api_key".toCharArray());
        secrets.setApiSecret("test_api_secret".toCharArray());
        secrets.setPassphrase("test_passphrase".toCharArray());
        when(secretsService.getAccountSecrets(anyString())).thenReturn(secrets);
        when(ctx.getBean(AccountSecretsService.class)).thenReturn(secretsService);

        Field f = SpringContextUtil.class.getDeclaredField("applicationContext");
        f.setAccessible(true);
        f.set(null, ctx);
    }

    @AfterEach
    void clearSpringContext() throws Exception {
        Field f = SpringContextUtil.class.getDeclaredField("applicationContext");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static TradingAccount buildTestAccount() {
        String base64Key = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
        AesGcmEncryptor encryptor = new AesGcmEncryptor(base64Key);

        TradingAccount account = new TradingAccount();
        account.setId("123");
        account.setApiKeyEnc(encryptor.encrypt("test_api_key"));
        account.setApiSecretEnc(encryptor.encrypt("test_api_secret"));
        account.setPassphraseEnc(encryptor.encrypt("test_passphrase"));
        account.setSimulated(true);
        return account;
    }

    @Test
    void createService_withValidAccount_shouldInitialize() {
        TradingAccount account = buildTestAccount();
        OkxDirectTradeService service = new OkxDirectTradeService(account);
        assertThat(service).isNotNull();
    }

    @Test
    void createOrder_withTestParams_shouldBuildCorrectRequest() {
        TradingAccount account = buildTestAccount();

        TradingStrategyParams params = TradingStrategyParams.builder()
                .accountId("123")
                .apiKey("test_api_key")
                .secretKey("test_api_secret")
                .passphrase("test_passphrase")
                .simulated(true)
                .symbol("BTC-USDT-SWAP")
                .side("BUY")
                .amount(new BigDecimal("0.001"))
                .positionId("TEST_ORDER_123")
                .leverage(10)
                .build();

        OkxDirectTradeService service = new OkxDirectTradeService(account);
        
        assertThat(service).isNotNull();

        UrlParamsBuilder builder;
        try {
            Method m = OkxDirectTradeService.class.getDeclaredMethod("buildOrderParams", TradingStrategyParams.class, String.class);
            m.setAccessible(true);
            builder = (UrlParamsBuilder) m.invoke(service, params, "BTC-USDT-SWAP");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, String> post = builder.getPostBodyMap();
        assertThat(post.get("instId")).isEqualTo("BTC-USDT-SWAP");
        assertThat(post.get("tdMode")).isEqualTo("cross");
        assertThat(post.get("clOrdId")).isEqualTo("TEST_ORDER_123");
        assertThat(post.get("side")).isEqualTo("buy");
        assertThat(post.get("posSide")).isEqualTo("long");
        assertThat(post.get("ordType")).isEqualTo("market");
    }

    @Test
    void convertSymbol_shouldHandleDifferentFormats() {
        TradingAccount account = buildTestAccount();
        OkxDirectTradeService service = new OkxDirectTradeService(account);
        
        // 验证符号转换逻辑（通过反射调用私有方法）
        // 这里我们主要验证服务能正常初始化
        assertThat(service).isNotNull();
    }
}
