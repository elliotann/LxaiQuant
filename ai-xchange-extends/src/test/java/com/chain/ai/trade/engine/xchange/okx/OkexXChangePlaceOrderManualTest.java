package com.chain.ai.trade.engine.xchange.okx;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 手动测试：用 XChange 的原生路径（ResCU）执行下单，验证通过代理访问 OKX 沙箱
 * 的连通性和耗时。此测试不自动运行，需设置环境变量后手动启用。
 *
 * <p>启动前需设置以下环境变量：<br>
 * - OKX_SANDBOX_API_KEY<br>
 * - OKX_SANDBOX_API_SECRET<br>
 * - OKX_SANDBOX_API_PASSPHRASE<br>
 *
 * <p>运行方式：mvn test -pl ai-xchange-extends -Dtest=OkexXChangePlaceOrderManualTest
 */
@Disabled("仅手动运行，需设置沙箱 API 密钥")
class OkexXChangePlaceOrderManualTest {

    private static final Logger log = LoggerFactory.getLogger(OkexXChangePlaceOrderManualTest.class);

    private static final String SYMBOL = "BTC-USDT-SWAP";
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 7890;

    @Test
    void testXChangeConnectivity() throws Exception {
        String apiKey = System.getenv("OKX_SANDBOX_API_KEY");
        String apiSecret = System.getenv("OKX_SANDBOX_API_SECRET");
        String apiPassphrase = System.getenv("OKX_SANDBOX_API_PASSPHRASE");

        assertNotNull(apiKey, "请设置环境变量 OKX_SANDBOX_API_KEY");
        assertNotNull(apiSecret, "请设置环境变量 OKX_SANDBOX_API_SECRET");
        assertNotNull(apiPassphrase, "请设置环境变量 OKX_SANDBOX_API_PASSPHRASE");

        ExchangeSpecification spec = new ExchangeSpecification(OkexExchange.class);
        spec.setApiKey(apiKey);
        spec.setSecretKey(apiSecret);
        spec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, "1");
        spec.setProxyHost(PROXY_HOST);
        spec.setProxyPort(PROXY_PORT);
        spec.setHttpConnTimeout(15000);
        spec.setHttpReadTimeout(20000);

        log.info("创建 OkexExchange（代理 {}:{}，sandbox）...", PROXY_HOST, PROXY_PORT);
        long t0 = System.nanoTime();

        OkexExchange exchange = (OkexExchange) ExchangeFactory.INSTANCE.createExchange(spec);

        long t1 = System.nanoTime();
        log.info("Exchange 创建完成，耗时: {}ms", TimeUnit.NANOSECONDS.toMillis(t1 - t0));

        ExchangeMetaData metaData = exchange.getExchangeMetaData();
        FuturesContract instrument = new FuturesContract(CurrencyPair.BTC_USDT, "SWAP");
        InstrumentMetaData instrMeta = metaData.getInstruments().get(instrument);
        if (instrMeta != null) {
            log.info("合约 {} 元数据: contractValue={}, minimumAmount={}",
                    SYMBOL, instrMeta.getContractValue(), instrMeta.getMinimumAmount());
        } else {
            log.warn("未找到合约元数据，将使用默认 contractValue=0.01");
        }

        MarketDataService marketDataService = exchange.getMarketDataService();
        long t2 = System.nanoTime();
        Ticker ticker = marketDataService.getTicker(instrument);
        long t3 = System.nanoTime();
        log.info("获取 Ticker 完成，耗时: {}ms, last={}", TimeUnit.NANOSECONDS.toMillis(t3 - t2), ticker.getLast());

        AccountService accountService = exchange.getAccountService();
        long t4 = System.nanoTime();
        AccountInfo accountInfo = accountService.getAccountInfo();
        long t5 = System.nanoTime();
        log.info("获取 AccountInfo 完成，耗时: {}ms, wallet={}",
                TimeUnit.NANOSECONDS.toMillis(t5 - t4), accountInfo.getWallet());

        TradeService tradeService = exchange.getTradeService();
        assertNotNull(tradeService);

        BigDecimal contractValue = instrMeta != null ? instrMeta.getContractValue() : new BigDecimal("0.01");
        BigDecimal amount = contractValue.multiply(BigDecimal.valueOf(1));
        MarketOrder order = new MarketOrder.Builder(Order.OrderType.BID, instrument)
                .originalAmount(amount)
                .build();

        log.info("准备下单: instrument={}, side=buy, amount={}(sz={})",
                SYMBOL, amount, amount.divide(contractValue, 0, java.math.RoundingMode.DOWN));

        long t6 = System.nanoTime();
        String orderId = tradeService.placeMarketOrder(order);
        long t7 = System.nanoTime();
        log.info("下单完成，耗时: {}ms, orderId={}", TimeUnit.NANOSECONDS.toMillis(t7 - t6), orderId);

        log.info("==================== 耗时汇总 ====================");
        log.info("Exchange 创建: {}ms", TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        log.info("获取 Ticker : {}ms", TimeUnit.NANOSECONDS.toMillis(t3 - t2));
        log.info("获取 Account: {}ms", TimeUnit.NANOSECONDS.toMillis(t5 - t4));
        log.info("下单       : {}ms", TimeUnit.NANOSECONDS.toMillis(t7 - t6));
        log.info("==================================================");
    }
}
