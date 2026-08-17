package com.chain.ai.trade.engine.xchange.factory;

import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.engine.xchange.huobi.HuobiExchangeService;
import com.chain.ai.trade.engine.xchange.okx.OkxExchangeService;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.member.util.AccountSecretKeyResolver;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import org.knowm.xchange.Exchange;
import org.springframework.core.env.Environment;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.huobi.HuobiExchange;

import static com.chain.ai.trade.common.entity.constants.Exchange.OKX;

/**
 * 交易所服务工厂
 */
public class ExchangeWrapFactory {

    /**
     * 根据账户信息创建对应的交易所服务
     *
     * @param account 账户信息
     * @return 交易所服务实例
     */
    public static ExchangeTradeService createExchangeService(TradingAccount account) {
        if (account == null || account.getMemberPlatform() == null) {
            throw new IllegalArgumentException("Account or platform information is missing");
        }
        return switch (account.getMemberPlatform()) {
            case OKX -> new OkxExchangeService(account);
            case HUOBI -> new HuobiExchangeService(account);
            default -> throw new UnsupportedOperationException("Unsupported exchange: " + account.getMemberPlatform());
        };
    }

    /**
     * 根据账户信息创建对应的需要验证交易所服务
     *
     * @param account 账户信息
     * @return 交易所服务实例
     */
    public static Exchange createAuthExchangeService(TradingAccount account) {
        if (account == null || account.getMemberPlatform() == null) {
            throw new IllegalArgumentException("Account or platform information is missing");
        }
        Environment env = null;
        try {
            env = SpringContextUtil.getBean(Environment.class);
        } catch (Exception ignored) {
            env = null;
        }
        String base64Key = AccountSecretKeyResolver.resolveOrThrow(env);
        AesGcmEncryptor encryptor = new AesGcmEncryptor(base64Key);
        String apiKey = encryptor.decrypt(account.getApiKeyEnc());
        String apiSecret = encryptor.decrypt(account.getApiSecretEnc());
        String passphrase = account.getPassphraseEnc() != null ? encryptor.decrypt(account.getPassphraseEnc()) : null;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("API Secret 不能为空");
        }

        if(OKX==account.getMemberPlatform()){
            if (passphrase == null || passphrase.trim().isEmpty()) {
                throw new IllegalArgumentException("API Passphrase 不能为空");
            }
            String simulated = (account.getSimulated() != null && account.getSimulated()) ? "1" : "0";
            ExchangeSpecification exchangeSpec =  new OkexExchange().getDefaultExchangeSpecification();
            exchangeSpec.setApiKey(apiKey);
            exchangeSpec.setSecretKey(apiSecret);
            exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_PASSPHRASE, passphrase);
            exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, simulated);

            // 配置代理
            configureProxy(exchangeSpec);
            return ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
        }
        throw new UnsupportedOperationException("Unsupported exchange: " + account.getMemberPlatform());
    }

    /**
     * 根据账户信息创建对应的不需要验证交易所服务
     *
     * @param account 账户信息
     * @return 交易所服务实例
     */
    public static Exchange createNoAuthExchangeService(TradingAccount account) {
        if (account == null || account.getMemberPlatform() == null) {
            throw new IllegalArgumentException("Account or platform information is missing");
        }
        return switch (account.getMemberPlatform()) {
            case OKX -> {
                String simulated = (account.getSimulated() != null && account.getSimulated()) ? "1" : "0";
                ExchangeSpecification exchangeSpec = new OkexExchange().getDefaultExchangeSpecification();
                exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, simulated);
                configureProxy(exchangeSpec);
                yield ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
            }
            case HUOBI -> {
                ExchangeSpecification exchangeSpec = new HuobiExchange().getDefaultExchangeSpecification();
                exchangeSpec.setShouldLoadRemoteMetaData(true);
                configureProxy(exchangeSpec);
                yield ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
            }
            default -> throw new UnsupportedOperationException("Unsupported exchange: " + account.getMemberPlatform());
        };
    }

    public static ExchangeTradeService createNoAuthExchangeTradeService(TradingAccount account) {
        if (account == null || account.getMemberPlatform() == null) {
            throw new IllegalArgumentException("Account or platform information is missing");
        }
        return switch (account.getMemberPlatform()) {
            case OKX -> {
                Exchange exchange = createNoAuthExchangeService(account);
                yield new OkxExchangeService(exchange, account, true);
            }
            case HUOBI -> {
                Exchange exchange = createNoAuthExchangeService(account);
                yield new HuobiExchangeService(exchange, account, true);
            }
            default -> throw new UnsupportedOperationException("Unsupported exchange: " + account.getMemberPlatform());
        };
    }

    /**
     * 根据交易所枚举创建无认证的交易所服务（无需TradingAccount）
     * 适用于仅需公共行情数据的场景，如K线数据拉取
     */
    public static ExchangeTradeService createNoAuthExchangeTradeService(com.chain.ai.trade.common.entity.constants.Exchange exchange) {
        return switch (exchange) {
            case OKX -> {
                ExchangeSpecification exchangeSpec = new OkexExchange().getDefaultExchangeSpecification();
                exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, "0");
                configureProxy(exchangeSpec);
                org.knowm.xchange.Exchange xchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
                yield new OkxExchangeService(xchange, null, true);
            }
            case HUOBI -> {
                ExchangeSpecification exchangeSpec = new HuobiExchange().getDefaultExchangeSpecification();
                exchangeSpec.setShouldLoadRemoteMetaData(true);
                configureProxy(exchangeSpec);
                org.knowm.xchange.Exchange xchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
                yield new HuobiExchangeService(xchange, null, true);
            }
            default -> throw new UnsupportedOperationException("Unsupported exchange: " + exchange);
        };
    }

    /**
     * 配置代理设置
     * 支持HTTP/SOCKS代理，通过环境变量或系统属性配置
     * 使用XChange的标准代理配置方法
     */
    private static void configureProxy(ExchangeSpecification exchangeSpec) {
        String proxyHost = System.getProperty("http.proxyHost");
        proxyHost="127.0.0.1";
        exchangeSpec.setProxyHost(proxyHost.trim());
        exchangeSpec.setProxyPort(7890);
    }


}
