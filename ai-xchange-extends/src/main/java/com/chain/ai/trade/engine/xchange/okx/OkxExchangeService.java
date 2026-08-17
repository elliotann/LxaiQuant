package com.chain.ai.trade.engine.xchange.okx;

import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.engine.xchange.factory.PlatformApiServiceFactory;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.dto.AccountSecrets;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.engine.xchange.dto.MarketDepth;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.service.OkexMarketDataService;
import org.knowm.xchange.okex.service.OkexMarketDataServiceRaw;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.marketdata.OkexCandleStick;
import org.knowm.xchange.okex.dto.marketdata.OkexInstrument;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;

import cn.hutool.json.JSONArray;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.knowm.xchange.dto.Order.OrderType.EXIT_ASK;
import static org.knowm.xchange.dto.Order.OrderType.EXIT_BID;

/**
 * OKX交易所服务实现
 * 基于Xchange实现与OKX交易所的交互
 * 如果OKX模块可用，使用真实API；否则使用模拟实现
 */
@Slf4j
public class OkxExchangeService implements ExchangeTradeService {

    private final Exchange exchange; // Exchange实例，根据账户的simulated属性决定环境
    private final TradingAccount account;
    private final boolean okxAvailable;
    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;

    public OkxExchangeService(TradingAccount account) {
        this.account = account;

        // 检查OKXExchange类是否可用
        this.okxAvailable = isOkxExchangeAvailable();

        if (!okxAvailable) {
            throw new RuntimeException("OKX模块不可用，请检查系统配置");
        }

        String k = null;
        String s = null;
        String p = null;
        Exception secretsError = null;
        try {
            AccountSecretsService secretsService = SpringContextUtil.getBean(AccountSecretsService.class);
            AccountSecrets secrets = secretsService.getAccountSecrets(account.getId());
            k = secrets.getApiKey() != null ? new String(secrets.getApiKey()) : null;
            s = secrets.getApiSecret() != null ? new String(secrets.getApiSecret()) : null;
            p = secrets.getPassphrase() != null ? new String(secrets.getPassphrase()) : null;
        } catch (Exception e) {
            secretsError = e;
        }
        this.apiKey = k;
        this.apiSecret = s;
        this.passphrase = p;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key 不能为空" + (secretsError != null ? (": " + secretsError.getMessage()) : ""));
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("API Secret 不能为空" + (secretsError != null ? (": " + secretsError.getMessage()) : ""));
        }

        try {
            // 根据账户的simulated属性决定使用的环境
            // 如果账户是模拟账户（simulated=true），使用沙箱环境；否则使用真实环境
            String simulated = (account.getSimulated() != null && account.getSimulated()) ? "1" : "0";
            ExchangeSpecification exchangeSpec = createExchangeSpecification(simulated);
            this.exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpec);
            log.info("初始化OKX Exchange（{}）成功，账户ID: {}",
                    simulated.equals("1") ? "沙箱环境" : "真实环境", account.getId());

        } catch (Exception e) {
            log.error("初始化OKX交易所服务失败，账户ID: {}, 错误: {}", account.getId(), e.getMessage());
            throw new RuntimeException("OKX交易所服务初始化失败: " + e.getMessage(), e);
        }
    }

    public OkxExchangeService(Exchange exchange, TradingAccount account, boolean noAuth) {
        this.account = account;
        this.okxAvailable = isOkxExchangeAvailable();
        if (!okxAvailable) {
            throw new RuntimeException("OKX模块不可用，请检查系统配置");
        }
        this.apiKey = null;
        this.apiSecret = null;
        this.passphrase = null;
        this.exchange = exchange;
    }

    /**
     * 检查OKXExchange类是否可用
     */
    private boolean isOkxExchangeAvailable() {
        try {
            Class.forName("org.knowm.xchange.okex.OkexExchange");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 创建Exchange配置规范
     * @param simulated "1" 表示模拟环境（沙盒），"0" 表示真实环境
     * @return ExchangeSpecification
     */
    private ExchangeSpecification createExchangeSpecification(String simulated) {
        ExchangeSpecification exchangeSpec = new ExchangeSpecification(OkexExchange.class);
        exchangeSpec.setApiKey(apiKey);
        exchangeSpec.setSecretKey(apiSecret);
        exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_PASSPHRASE, passphrase);
        exchangeSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_SIMULATED, simulated);

        // 配置代理
        configureProxy(exchangeSpec);

        exchangeSpec.setHttpConnTimeout(10000);
        exchangeSpec.setHttpReadTimeout(20000);

        return exchangeSpec;
    }

    /**
     * 配置代理设置
     * 支持HTTP/SOCKS代理，通过环境变量或系统属性配置
     * 使用XChange的标准代理配置方法
     */
    private static void configureProxy(ExchangeSpecification exchangeSpec) {
        exchangeSpec.setProxyHost("127.0.0.1");
        exchangeSpec.setProxyPort(7890);
    }

    /**
     * 通用的交易所操作执行器
     *
     * @param operation     操作函数
     * @param fallback      模拟操作结果
     * @param operationName 操作名称，用于日志
     * @param <T>           返回类型
     * @return 操作结果
     */
    private <T> T executeExchangeOperation(ExchangeOperation<T> operation, T fallback, String operationName) {
        if (exchange == null) {
            throw new RuntimeException("交易所服务未初始化，无法执行" + operationName);
        }

        try {
            T result = operation.execute();
            log.info("{}成功", operationName);
            return result;
        } catch (Exception e) {
            log.error("{}失败", operationName, e);
            throw new RuntimeException(operationName + "失败: " + e.getMessage(), e);
        }
    }

    /**
     * 交易所操作函数式接口
     */
    @FunctionalInterface
    private interface ExchangeOperation<T> {
        T execute() throws Exception;
    }

    @Override
    public String createOrder(TradingStrategyParams params) {
        log.info("在OKX上创建订单: symbol={}, side={}, amount={} (张数)",
                params.getSymbol(), params.getSide(), params.getAmount());

        return executeExchangeOperation(
                () -> {
                    String okexSymbol = convertToOkexSymbol(params.getSymbol());
                    boolean isContract = okexSymbol.contains("-SWAP") || okexSymbol.matches(".*-\\d{6}$");

                    if (isContract) {
                        // 简化方案：直接使用CurrencyPair，让XChange内部处理合约
                        return createContractOrderSimple(params, okexSymbol);
                    } else {
                        return createSpotOrder(params);
                    }
                },
                "OKX_" + System.currentTimeMillis(),
                "订单创建"
        );
    }

    /**
     * 简化方案：创建合约订单
     * 直接使用CurrencyPair，让XChange的OkexAdapters去处理合约转换
     */
    private String createContractOrderSimple(TradingStrategyParams params, String okexSymbol) throws Exception {
        // 1. 创建CurrencyPair（只包含基础货币和计价货币）
        CurrencyPair currencyPair = parseCurrencyPair(okexSymbol);
        Instrument instrument = createOkexInstrument(okexSymbol);
        FuturesContract contract = new FuturesContract(currencyPair,"SWAP");

        // 2. 创建市场订单
        com.chain.ai.trade.engine.xchange.dto.MarketOrder marketOrder = new com.chain.ai.trade.engine.xchange.dto.MarketOrder();
        marketOrder.setSymbol(okexSymbol);
        marketOrder.setSide("BUY".equals(params.getSide()) ? OrderSideEnum.BUY : OrderSideEnum.SELL);
        marketOrder.setAmount(params.getAmount().setScale(0, RoundingMode.HALF_DOWN));
        marketOrder.setOffset("open");
        // 在下单前设置杠杆（OKX需要单独调用账户杠杆设置接口）
        if (params.getLeverage() != null && params.getLeverage() > 0) {
            String tdMode = "cross"; // 默认使用cross模式
            String posSide = marketOrder.getSide() == OrderSideEnum.BUY ? "long" : "short";
            try {
                boolean levOk = setLeverageInternal(okexSymbol, params.getLeverage(), tdMode, posSide);
                if (!levOk) {
                    log.warn("设置杠杆失败，继续尝试下单: instId={}, leverage={}", okexSymbol, params.getLeverage());
                } else {
                    log.info("已设置杠杆: instId={}, leverage={}, tdMode={}, posSide={}", okexSymbol, params.getLeverage(), tdMode, posSide);
                }
            } catch (Exception e) {
                log.warn("设置杠杆接口异常，继续下单: {}", e.getMessage());
            }
        }
        if (params.getLeverage() != null && params.getLeverage() > 0) {
            marketOrder.setLeverRate(params.getLeverage());
        }
        if(EXIT_BID==params.getOrderType()||EXIT_ASK==params.getOrderType()){
            marketOrder.setOffset("close");
        }
        // 传递固定止盈/止损价格（仅开仓时附加，且非模拟账户）
        if (!"close".equalsIgnoreCase(marketOrder.getOffset()) && !account.getSimulated()) {
            if (params.getTakeProfitPrice() != null && params.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0) {
                marketOrder.setStopGain(params.getTakeProfitPrice());
            }
            if (params.getStopLossPrice() != null && params.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0) {
                marketOrder.setStopLoss(params.getStopLossPrice());
            }
            if ((params.getTakeProfitPrice() != null && params.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0)
                    || (params.getStopLossPrice() != null && params.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0)) {
                marketOrder.setGainAndLossType("oco");
                marketOrder.setAlgoClOrdId(params.getPositionId());
            }
        }
        marketOrder.setOrderId(params.getPositionId());
        marketOrder.setApiKey(this.apiKey);
        marketOrder.setSecretKey(this.apiSecret);
        marketOrder.setPassphrase(this.passphrase);
        marketOrder.setSimulated(account.getSimulated());
        log.info("创建合约订单 - 简化方案: symbol={}, currencyPair={}, amount={}",
                okexSymbol, currencyPair, params.getAmount());
        return PlatformApiServiceFactory.getTradeService(com.chain.ai.trade.common.entity.constants.Exchange.OKX).placeMarketOrder(marketOrder);
        /*MarketOrder marketOrder = new MarketOrder.Builder(
                "BUY".equals(params.getSide()) ? Order.OrderType.BID : Order.OrderType.ASK,
                contract)
                .originalAmount(BigDecimal.valueOf(1000))
                .instrument(contract)
                .build();*/

        // 3. 让XChange的OkexTradeService处理订单
        // XChange的OkexAdapters会在内部将CurrencyPair转换为OKX需要的合约格式
        //return exchange.getTradeService().placeMarketOrder(marketOrder);
    }

    /**
     * 备选方案：创建自定义的Instrument实现
     */
    private String createContractOrderCustomInstrument(TradingStrategyParams params, String okexSymbol) throws Exception {
        // 创建自定义的Instrument实现
        Instrument customInstrument = new Instrument() {
            @Override
            public Currency getBase() {
                String[] parts = okexSymbol.split("-");
                return Currency.getInstance(parts[0]);
            }

            @Override
            public Currency getCounter() {
                String[] parts = okexSymbol.split("-");
                return Currency.getInstance(parts[1]);
            }

            // 重写toString方法，返回完整的OKX符号
            @Override
            public String toString() {
                return okexSymbol;
            }

            // 重写equals和hashCode方法
            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                Instrument that = (Instrument) obj;
                return okexSymbol.equals(that.toString());
            }

            @Override
            public int hashCode() {
                return okexSymbol.hashCode();
            }
        };

        // 创建市场订单
        MarketOrder marketOrder = new MarketOrder(
                "BUY".equals(params.getSide()) ? Order.OrderType.BID : Order.OrderType.ASK,
                params.getAmount(),
                customInstrument
        );

        log.info("创建合约订单 - 自定义Instrument: symbol={}, instrument={}, amount={}",
                okexSymbol, customInstrument, params.getAmount());

        return exchange.getTradeService().placeMarketOrder(marketOrder);
    }



    /**
     * 解析交易对字符串为CurrencyPair
     * 无论是否为合约，都只提取前两部分作为base和counter
     */
    private CurrencyPair parseCurrencyPair(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        String[] parts = symbol.split("-");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid symbol format: " + symbol);
        }

        // 只取前两部分作为基础货币和计价货币
        String base = parts[0].toUpperCase();
        String counter = parts[1].toUpperCase();

        log.debug("解析交易对: {} -> CurrencyPair({}/{})", symbol, base, counter);
        return new CurrencyPair(base, counter);
    }


    



    /**
     * 创建Okex专用的Instrument对象
     */
    private Instrument createOkexInstrument(String symbol) {
        // 手动解析
        return createInstrumentManually(symbol);
    }

    /**
     * 手动创建Instrument对象
     */
    private Instrument createInstrumentManually(String symbol) {
        if (symbol.contains("-SWAP")) {
            // 永续合约
            String[] parts = symbol.replace("-SWAP", "").split("-");
            if (parts.length == 2) {
                Currency base = Currency.getInstance(parts[0]);
                Currency counter = Currency.getInstance(parts[1]);
                CurrencyPair currencyPair = new CurrencyPair(base, counter);

                // 关键：创建FuturesContract时传入完整的symbol字符串
                // 而不是只传入currencyPair
                try {
                    return new FuturesContract("ETH/USDT/SWAP") {
                        @Override
                        public String toString() {
                            return "ETH/USDT/SWAP"; // 返回完整的symbol
                        }
                    };
                } catch (Exception e) {
                    log.warn("创建FuturesContract失败: {}, 使用CurrencyPair", e.getMessage());
                    return currencyPair;
                }
            }
        } else if (symbol.matches(".*-\\d{6}$")) {
            // 期货合约
            String[] allParts = symbol.split("-");
            if (allParts.length == 3) {
                Currency base = Currency.getInstance(allParts[0]);
                Currency counter = Currency.getInstance(allParts[1]);
                String expiry = allParts[2];
                CurrencyPair currencyPair = new CurrencyPair(base, counter);

                try {
                    return new FuturesContract(currencyPair, expiry) {
                        @Override
                        public String toString() {
                            return symbol; // 返回完整的symbol
                        }
                    };
                } catch (Exception e) {
                    log.warn("创建FuturesContract失败: {}, 使用CurrencyPair", e.getMessage());
                    return currencyPair;
                }
            }
        } else {
            // 现货
            String[] parts = symbol.split("-");
            if (parts.length == 2) {
                Currency base = Currency.getInstance(parts[0]);
                Currency counter = Currency.getInstance(parts[1]);
                return new CurrencyPair(base, counter);
            }
        }

        throw new IllegalArgumentException("无法解析合约符号: " + symbol);
    }
    /**
     * 转换交易对格式
     */
    private String convertToOkexSymbol(String symbol) {
        if (symbol == null) return null;
        return symbol.toUpperCase().replace("/", "-").replace("_", "-");
    }

    /**
     * 调用OKX接口设置杠杆倍数
     */
    private boolean setLeverageInternal(String instId, int leverage, String tdMode, String posSide) throws Exception {
        com.chain.ai.trade.engine.xchange.utils.OkxRestConnection rest = new com.chain.ai.trade.engine.xchange.utils.OkxRestConnection(
                com.chain.ai.trade.engine.xchange.utils.OkxOptions.builder()
                        .apiKey(this.apiKey)
                        .secretKey(this.apiSecret)
                        .passphrase(this.passphrase)
                        .simulated(account.getSimulated())
                        .build()
        );
        com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder builder = com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder.build()
                .putToPost("instId", instId)
                .putToPost("mgnMode", tdMode != null ? tdMode : "cross")
                .putToPost("lever", leverage);
        if (posSide != null && !posSide.isBlank()) {
            builder.putToPost("posSide", posSide);
        }
        String path = "/api/v5/account/set-leverage";
        com.alibaba.fastjson.JSONObject resp = rest.executePostWithSignature(path, builder);
        try {
            String code = resp.getString("code");
            boolean ok = "0".equals(code);
            if (!ok) {
                log.warn("OKX设置杠杆返回非0: {}", resp.toJSONString());
            }
            return ok;
        } catch (Exception e) {
            log.warn("解析杠杆设置响应失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 替代方案：使用OKX的TradeServiceRaw直接创建订单
     */
    private String createContractOrderUsingRawService(TradingStrategyParams params, String okexSymbol) throws Exception {
        // 获取TradeService
        TradeService tradeService = exchange.getTradeService();

        // 检查是否是OkexTradeService
        if (tradeService instanceof org.knowm.xchange.okex.service.OkexTradeService) {
            org.knowm.xchange.okex.service.OkexTradeService okexTradeService =
                    (org.knowm.xchange.okex.service.OkexTradeService) tradeService;

            // 使用OkexTradeService的placeMarketOrder方法
            // 创建Instrument
            Instrument instrument = createOkexInstrument(okexSymbol);

            // 创建市场订单
            MarketOrder marketOrder = new MarketOrder(
                    "BUY".equals(params.getSide()) ? Order.OrderType.BID : Order.OrderType.ASK,
                    params.getAmount(),
                    instrument
            );

            return okexTradeService.placeMarketOrder(marketOrder);
        }

        throw new RuntimeException("TradeService不是OkexTradeService");
    }

    /**
     * 创建现货订单
     */
    private String createSpotOrder(TradingStrategyParams params) throws Exception {
        CurrencyPair currencyPair = parseCurrencyPair(params.getSymbol());
        Order.OrderType orderType = "BUY".equals(params.getSide())
                ? Order.OrderType.BID
                : Order.OrderType.ASK;

        MarketOrder marketOrder = new MarketOrder.Builder(orderType, currencyPair)
                .originalAmount(params.getAmount())
                .build();

        return exchange.getTradeService().placeMarketOrder(marketOrder);
    }

    @Override
    public boolean cancelOrder(String orderId, String symbol) {
        log.info("在OKX上取消订单: orderId={}, symbol={}", orderId, symbol);

        try {
            String okexSymbol = symbol != null ? convertToOkexSymbol(symbol) : null;

            com.chain.ai.trade.engine.xchange.utils.OkxRestConnection rest = new com.chain.ai.trade.engine.xchange.utils.OkxRestConnection(
                    com.chain.ai.trade.engine.xchange.utils.OkxOptions.builder()
                            .apiKey(this.apiKey)
                            .secretKey(this.apiSecret)
                            .passphrase(this.passphrase)
                            .simulated(account.getSimulated())
                            .build()
            );

            com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder builder = com.chain.ai.trade.engine.xchange.utils.UrlParamsBuilder.build()
                    .putToPost("ordId", orderId);
            if (okexSymbol != null) {
                builder.putToPost("instId", okexSymbol);
            }

            com.alibaba.fastjson.JSONObject resp = rest.executePostWithSignature("/api/v5/trade/cancel-order", builder);
            String code = resp.getString("code");
            boolean success = "0".equals(code);
            if (success) {
                log.info("取消订单成功: orderId={}", orderId);
            } else {
                log.warn("取消订单失败: orderId={}, response={}", orderId, resp.toJSONString());
            }
            return success;
        } catch (Exception e) {
            log.error("取消订单失败: orderId={}", orderId, e);
            return false;
        }
    }

    @Override
    public List<String> cancelAllOrders(String symbol) {
        log.info("在OKX上取消所有订单，交易对: {}", symbol);

        return executeExchangeOperation(
                () -> cancelAllOrdersInternal(symbol),
                new ArrayList<>(),
                "取消所有订单"
        );
    }

    /**
     * 取消所有订单的内部实现
     */
    private List<String> cancelAllOrdersInternal(String symbol) throws Exception {
        List<String> canceledOrderIds = new ArrayList<>();
        OpenOrders openOrders = exchange.getTradeService().getOpenOrders();

        for (Order order : openOrders.getAllOpenOrders()) {
            if (symbol == null || order.getInstrument().toString().equals(symbol)) {
                try {
                    boolean canceled = cancelOrder(order.getId(), symbol);
                    if (canceled) {
                        canceledOrderIds.add(order.getId());
                    }
                } catch (Exception e) {
                    log.warn("取消订单失败: {}", order.getId(), e);
                }
            }
        }

        log.info("已取消 {} 个订单", canceledOrderIds.size());
        return canceledOrderIds;
    }

    @Override
    public String getOrderStatus(String orderId) {
        log.info("获取订单状态: orderId={}", orderId);

        return executeExchangeOperation(
                () -> {
                    java.util.Collection<Order> orders = exchange.getTradeService().getOrder(orderId);
                    if (orders != null && !orders.isEmpty()) {
                        return orders.iterator().next().getStatus().toString();
                    } else {
                        log.warn("订单不存在: orderId={}", orderId);
                        return "NOT_FOUND";
                    }
                },
                "FILLED",
                "获取订单状态"
        );
    }

    @Override
    public BigDecimal getAccountBalance(String currency) {
        log.info("获取账户余额，货币: {}", currency != null ? currency : "ALL");

        return executeExchangeOperation(
                () -> {
                    AccountInfo accountInfo = exchange.getAccountService().getAccountInfo();

                    // 获取所有钱包而不是单个钱包，以支持多个钱包的情况
                    Map<String, Wallet> wallets = accountInfo.getWallets();

                    if (currency != null) {
                        // 在所有钱包中查找指定货币的余额
                        Currency targetCurrency = Currency.getInstance(currency.toUpperCase());
                        for (Wallet wallet : wallets.values()) {
                            if (wallet.getBalance(targetCurrency) != null) {
                                return wallet.getBalance(targetCurrency).getTotal();
                            }
                        }
                        // 如果没找到，返回0
                        return BigDecimal.ZERO;
                    } else {
                        // 返回USDT余额作为默认，如果没找到则返回0
                        Currency usdt = Currency.USDT;
                        for (Wallet wallet : wallets.values()) {
                            if (wallet.getBalance(usdt) != null) {
                                return wallet.getBalance(usdt).getTotal();
                            }
                        }
                        return BigDecimal.ZERO;
                    }
                },
                new BigDecimal("10000.00"),
                "获取账户余额"
        );
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        log.info("获取当前价格，交易对: {}", symbol);

        return executeExchangeOperation(
                () -> {
                    CurrencyPair currencyPair = parseCurrencyPair(symbol);
                    Ticker ticker = exchange.getMarketDataService().getTicker((Instrument) currencyPair);
                    return ticker.getLast();
                },
                new BigDecimal("50000.00"),
                "获取当前价格"
        );
    }

    @Override
    public MarketDepth getMarketDepth(String symbol, int depth) {
        log.info("获取市场深度，交易对: {}, 深度: {}", symbol, depth);

        return executeExchangeOperation(
                () -> getMarketDepthInternal(symbol, depth),
                createMockMarketDepth(symbol, depth),
                "获取市场深度"
        );
    }

    /**
     * 获取市场深度的内部实现
     */
    private MarketDepth getMarketDepthInternal(String symbol, int depth) throws Exception {
        CurrencyPair currencyPair = parseCurrencyPair(symbol);
        OrderBook orderBook = exchange.getMarketDataService().getOrderBook((Instrument) currencyPair);

        List<MarketDepth.DepthLevel> bids = orderBook.getBids().stream()
                .limit(depth)
                .map(bid -> new MarketDepth.DepthLevel(bid.getLimitPrice(), bid.getOriginalAmount()))
                .toList();

        List<MarketDepth.DepthLevel> asks = orderBook.getAsks().stream()
                .limit(depth)
                .map(ask -> new MarketDepth.DepthLevel(ask.getLimitPrice(), ask.getOriginalAmount()))
                .toList();

        return MarketDepth.builder()
                .symbol(symbol)
                .bids(bids)
                .asks(asks)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建模拟的市场深度数据
     */
    private MarketDepth createMockMarketDepth(String symbol, int depth) {
        List<MarketDepth.DepthLevel> bids = new ArrayList<>();
        List<MarketDepth.DepthLevel> asks = new ArrayList<>();

        for (int i = 0; i < Math.min(depth, 5); i++) {
            bids.add(new MarketDepth.DepthLevel(
                    new BigDecimal("49990").subtract(new BigDecimal(i * 10)),
                    new BigDecimal("1.0").add(new BigDecimal(i * 0.1))
            ));
            asks.add(new MarketDepth.DepthLevel(
                    new BigDecimal("50010").add(new BigDecimal(i * 10)),
                    new BigDecimal("1.0").add(new BigDecimal(i * 0.1))
            ));
        }

        return MarketDepth.builder()
                .symbol(symbol)
                .bids(bids)
                .asks(asks)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public boolean setLeverage(String symbol, int leverage) {
        log.info("设置杠杆，交易对: {}, 杠杆: {}", symbol, leverage);

        if (exchange == null) {
            log.info("Exchange未初始化，使用模拟杠杆设置，交易对: {}, 杠杆: {}", symbol, leverage);
            return true;
        }

        try {
            CurrencyPair currencyPair = parseCurrencyPair(symbol);

            // 尝试通过ExchangeSpecification设置杠杆参数
            ExchangeSpecification spec = exchange.getExchangeSpecification();
            spec.setExchangeSpecificParametersItem("leverage", leverage);
            spec.setExchangeSpecificParametersItem("leverage_" + symbol.replace("-", ""), leverage);

            // 记录杠杆设置完成
            log.info("杠杆设置完成，交易对: {}, 杠杆: {}", symbol, leverage);
            return true;

        } catch (Exception e) {
            log.error("设置杠杆失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean closeAllPositions(String symbol) {
        log.info("开始平仓所有持仓，交易对: {}", symbol);

        if (exchange == null) {
            log.info("Exchange未初始化，使用模拟平仓，交易对: {}", symbol);
            return true;
        }

        try {
            CurrencyPair currencyPair = parseCurrencyPair(symbol);
            int closedPositions = 0;

            // 步骤1: 取消所有相关订单（作为平仓准备）
            closedPositions += cancelPendingOrders(symbol);

            // 步骤2: 获取并平仓实际持仓
            BigDecimal positionSize = getPositionSize(currencyPair);
            log.info("获取到持仓大小 for {}: {}", symbol, positionSize);

            if (positionSize.compareTo(BigDecimal.ZERO) != 0) {
                // 有持仓，创建平仓订单
                Order.OrderType closeOrderType;
                BigDecimal closeAmount;

                if (positionSize.compareTo(BigDecimal.ZERO) > 0) {
                    // 多头持仓，卖出平仓
                    closeOrderType = Order.OrderType.ASK;
                    closeAmount = positionSize;
                    log.info("检测到多头持仓 {}，准备卖出平仓", positionSize);
                } else {
                    // 空头持仓，买入平仓
                    closeOrderType = Order.OrderType.BID;
                    closeAmount = positionSize.abs();
                    log.info("检测到空头持仓 {}，准备买入平仓", positionSize);
                }

                // 创建市价平仓订单（直连）
                com.chain.ai.trade.engine.xchange.dto.MarketOrder closeOrder = new com.chain.ai.trade.engine.xchange.dto.MarketOrder();
                closeOrder.setSymbol(symbol);
                if (positionSize.compareTo(BigDecimal.ZERO) > 0) {
                    closeOrder.setSide(OrderSideEnum.SELL);
                } else {
                    closeOrder.setSide(OrderSideEnum.BUY);
                }
                closeOrder.setAmount(closeAmount.setScale(0, RoundingMode.HALF_DOWN));
                closeOrder.setOffset("close");
                closeOrder.setTdMode("cross");
                closeOrder.setApiKey(this.apiKey);
                closeOrder.setSecretKey(this.apiSecret);
                closeOrder.setPassphrase(this.passphrase);
                closeOrder.setSimulated(account.getSimulated());
                String orderId = PlatformApiServiceFactory.getTradeService(com.chain.ai.trade.common.entity.constants.Exchange.OKX).placeMarketOrder(closeOrder);

                log.info("成功创建平仓订单: {} for {}", orderId, symbol);
                closedPositions++;

            } else {
                log.info("未检测到持仓，无需平仓 for {}", symbol);
            }

            log.info("平仓操作执行完成，交易对: {}, 处理了 {} 个持仓", symbol, closedPositions);
            return true;

        } catch (Exception e) {
            log.error("平仓操作失败，交易对: {}", symbol, e);
            return false;
        }
    }

    /**
     * 尝试获取指定交易对的持仓大小
     *
     * @param currencyPair 交易对
     * @return 持仓大小，正数表示多头，负数表示空头，0表示无持仓
     */
    private BigDecimal getPositionSize(CurrencyPair currencyPair) {
        if (exchange == null) {
            log.debug("Exchange未初始化，无法获取持仓信息");
            return BigDecimal.ZERO;
        }

        try {
            // 注意：当前实现通过未成交订单推断持仓大小，这不是准确的持仓查询方法
            // 如需准确的持仓信息，需要实现真正的OKEX持仓查询API

            TradeService tradeService = exchange.getTradeService();
            OpenOrders openOrders = tradeService.getOpenOrders();
            BigDecimal positionSize = BigDecimal.ZERO;

            // 通过未成交订单推断可能的持仓（近似方法）
            // 买入订单累加，卖出订单累减
            for (Order order : openOrders.getAllOpenOrders()) {
                if (order.getInstrument().equals(currencyPair)) {
                    if (order.getType() == Order.OrderType.BID) {
                        // 买入订单表示潜在的多头持仓
                        positionSize = positionSize.add(order.getOriginalAmount());
                    } else if (order.getType() == Order.OrderType.ASK) {
                        // 卖出订单表示潜在的空头持仓
                        positionSize = positionSize.subtract(order.getOriginalAmount());
                    }
                }
            }

            if (positionSize.compareTo(BigDecimal.ZERO) != 0) {
                log.debug("通过未成交订单推断持仓大小: {} for {}", positionSize, currencyPair);
                return positionSize;
            }

            log.debug("未发现相关未成交订单，推断无持仓 for {}", currencyPair);

        } catch (Exception e) {
            log.debug("获取持仓大小失败: {}", e.getMessage());
        }

        log.debug("无法获取准确持仓信息，返回默认值0 for {}", currencyPair);
        return BigDecimal.ZERO;
    }



    /**
     * 取消指定交易对的所有挂单
     *
     * @param symbol 交易对
     * @return 取消的订单数量
     */
    private int cancelPendingOrders(String symbol) {
        try {
            List<String> canceledOrders = cancelAllOrders(symbol);
            if (!canceledOrders.isEmpty()) {
                log.info("作为平仓操作，取消了 {} 个未成交订单", canceledOrders.size());
                return canceledOrders.size();
            }
        } catch (Exception e) {
            log.warn("取消相关订单失败，但继续平仓流程: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public List<Candlestick> getCandlestick(CandlestickRequest request) {
        log.debug("获取OKX K线数据，交易对: {}, 时间间隔: {}, 大小: {}",
                request.getSymbol(), request.getInterval(), request.getSize());

        try {
            return getCandlestickInternal(request);
        } catch (Exception e) {
            log.error("获取K线数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取K线数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Candlestick> getHistoryCandlestick(CandlestickRequest request) {
        log.info("获取OKX历史K线，交易对: {}, 间隔: {}, from={}, to={}",
                request.getSymbol(), request.getInterval(), request.getFrom(), request.getTo());
        if (request.getFrom() <= 0 && request.getTo() <= 0) {
            log.warn("getHistoryCandlestick 需要 from、to 大于 0（秒级时间戳）");
            return new ArrayList<>();
        }
        try {
            return getHistoryCandlestickInternal(request);
        } catch (Exception e) {
            log.error("获取OKX历史K线失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取OKX历史K线失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取历史K线：全部走 XChange OkexMarketDataServiceRaw.getHistoryCandle
     * after、before 不能同时传，只传 after（毫秒），按时间正序分页
     */


    @Override
    public boolean amendTpSl(String orderSn, String symbol, BigDecimal gainPrice, BigDecimal lossPrice) {
        throw new UnsupportedOperationException("OkxExchangeService(XChange)不支持直接修改止盈止损，请使用OkxDirectTradeService");
    }

    private List<Candlestick> getHistoryCandlestickInternal(CandlestickRequest request) throws Exception {
        String symbol = request.getSymbol();
        String bar = mapIntervalToOkxBar(request.getInterval());
        int limit = request.getSize() != null ? Math.min(request.getSize(), 300) : 300;
        long afterMs = request.getTo() * 1000;
        String afterStr = String.valueOf(afterMs);

        MarketDataService marketDataService = exchange.getMarketDataService();
        if (!(marketDataService instanceof OkexMarketDataServiceRaw)) {
            throw new RuntimeException("当前 MarketDataService 不是 OkexMarketDataServiceRaw，无法调用 getHistoryCandle");
        }
        OkexMarketDataServiceRaw raw = (OkexMarketDataServiceRaw) marketDataService;
        OkexResponse<List<OkexCandleStick>> response = raw.getHistoryCandle(
                symbol, afterStr, null, bar, String.valueOf(limit));

        if (response == null || response.getData() == null) {
            log.warn("XChange getHistoryCandle 返回空");
            return new ArrayList<>();
        }
        List<Candlestick> list = new ArrayList<>();
        for (OkexCandleStick okexCandle : response.getData()) {
            list.add(convertOkexCandleToCandlestick(okexCandle, symbol, request.getInterval()));
        }
        java.util.Collections.reverse(list);
        log.info("XChange getHistoryCandle 成功，{} 条", list.size());
        return list;
    }


    /**
     * 获取K线数据的内部实现
     * 使用真实环境的Exchange获取K线数据（通过XChange库）
     * 注意：根据XChange源码，参数顺序为：instrument, after, before, bar, limit
     */
    private List<Candlestick> getCandlestickInternal(CandlestickRequest request) throws Exception {
        String symbol = request.getSymbol(); // 格式如: "BTC-USDT" 或 "ETH-USDT-SWAP"
        String bar = mapIntervalToOkxBar(request.getInterval());
        int limit = request.getSize() != null ? Math.min(request.getSize(), 300) : 100;

        try {
            String envType = (account != null && account.getSimulated() != null && account.getSimulated()) ? "沙箱环境" : "真实环境";
            log.info("使用XChange获取OKX K线数据（{}），交易对: {}, 周期: {}, 数量: {}",
                    envType, symbol, bar, limit);

            // 使用Exchange获取K线数据（环境由account.getSimulated()决定）
            MarketDataService marketDataService = exchange.getMarketDataService();

            // 检查是否是OkexMarketDataService
            if (!(marketDataService instanceof OkexMarketDataService)) {
                log.warn("MarketDataService不是OkexMarketDataService，使用HTTP请求方式");
                return getCandlestickByHttp(request);
            }

            OkexMarketDataService okexMarketDataService = (OkexMarketDataService) marketDataService;

            // 根据XChange源码，getCandle方法的参数顺序为：instrument, after, before, bar, limit
            // after: 请求此时间戳之后的数据（String类型，秒级时间戳的字符串，null表示不限制，获取最新数据）
            // before: 请求此时间戳之前的数据（String类型，秒级时间戳的字符串，null表示不限制）
            // 如果request中有from或to参数，使用它们；否则传null获取最新数据
            String after = null;
            String before = null;

            // CandlestickRequest中的from和to是秒级时间戳（根据注释：查询时间断，10位，单位为s）
            if (request.getFrom() > 0) {
                after = String.valueOf(request.getFrom()); // 转换为String类型
                log.debug("使用from参数: {} (秒级时间戳)", after);
            }
            if (request.getTo() > 0) {
                before = String.valueOf(request.getTo()); // 转换为String类型
                log.debug("使用to参数: {} (秒级时间戳)", before);
            }

            // 如果没有指定时间范围，获取最新数据（传null）
            // OKX API默认返回最新的历史数据（已完成的K线），当前正在进行的K线不会返回
            log.info("获取OKX K线数据参数: symbol={}, after={}, before={}, bar={}, limit={}",
                    symbol, after, before, bar, limit);

            OkexResponse<List<OkexCandleStick>> response = okexMarketDataService.getCandle(
                    symbol,           // instrument: 交易对ID，如 "BTC-USDT" 或 "ETH-USDT-SWAP"
                    after,            // after: 请求此时间戳之后的数据（String类型，秒级时间戳，null表示获取最新数据）
                    before,           // before: 请求此时间戳之前的数据（String类型，秒级时间戳，null表示不限制）
                    bar,              // bar: 时间周期，如 "3m", "1H", "1D"
                    String.valueOf(limit)  // limit: 返回的数据条数（转换为String）
            );

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.warn("XChange返回的K线数据为空，回退到HTTP请求方式");
                return getCandlestickByHttp(request);
            }

            // 转换为我们的Candlestick格式
            List<Candlestick> candlesticks = new ArrayList<>();
            for (OkexCandleStick okexCandle : response.getData()) {
                Candlestick candlestick = convertOkexCandleToCandlestick(
                        okexCandle, symbol, request.getInterval());
                candlesticks.add(candlestick);
            }

            // 反转列表，使时间顺序从旧到新（OKX返回的是最新在前）
            java.util.Collections.reverse(candlesticks);

            log.info("使用XChange成功获取 {} 条K线数据（真实环境）", candlesticks.size());
            return candlesticks;

        } catch (Exception e) {
            log.warn("使用XChange获取K线数据失败，回退到HTTP请求方式: {}", e.getMessage());
            return getCandlestickByHttp(request);
        }
    }

    /**
     * 通过HTTP请求获取K线数据（备用方案）
     */
    private List<Candlestick> getCandlestickByHttp(CandlestickRequest request) throws Exception {
        String symbol = request.getSymbol();
        String bar = mapIntervalToOkxBar(request.getInterval());
        int limit = request.getSize() != null ? Math.min(request.getSize(), 300) : 100;

        String url = "https://www.okx.com/api/v5/market/candles";
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append(String.format("instId=%s&bar=%s&limit=%d", symbol, bar, limit));

        // 添加时间参数（如果提供）
        if (request.getFrom() > 0) {
            paramsBuilder.append(String.format("&after=%d", request.getFrom()));
            log.debug("HTTP请求添加after参数: {} (秒级时间戳)", request.getFrom());
        }
        if (request.getTo() > 0) {
            paramsBuilder.append(String.format("&before=%d", request.getTo()));
            log.debug("HTTP请求添加before参数: {} (秒级时间戳)", request.getTo());
        }

        String params = paramsBuilder.toString();
        log.info("通过HTTP获取OKX K线数据: {}?{}", url, params);

        String response = cn.hutool.http.HttpUtil.get(url + "?" + params);
        cn.hutool.json.JSONObject jsonResponse = cn.hutool.json.JSONUtil.parseObj(response);

        String code = jsonResponse.getStr("code");
        if (!"0".equals(code)) {
            String msg = jsonResponse.getStr("msg");
            throw new RuntimeException("OKX API调用失败: " + msg);
        }

        cn.hutool.json.JSONArray data = jsonResponse.getJSONArray("data");
        List<Candlestick> candlesticks = new ArrayList<>();

        for (Object item : data) {
            cn.hutool.json.JSONArray kline = (cn.hutool.json.JSONArray) item;
            Candlestick candlestick = parseOkxKlineData(kline, symbol, request.getInterval());
            candlesticks.add(candlestick);
        }

        java.util.Collections.reverse(candlesticks);
        log.info("通过HTTP成功获取 {} 条K线数据", candlesticks.size());
        return candlesticks;
    }

    /**
     * 将OkexCandleStick转换为我们的Candlestick格式
     */
    private Candlestick convertOkexCandleToCandlestick(
            OkexCandleStick okexCandle,
            String symbol,
            CandlestickIntervalEnum interval) {
        // 将毫秒时间戳转换为 "yyyy-MM-dd HH:mm:ss" 格式的字符串
        long timestamp = okexCandle.getTimestamp();

        String timeStr =DateUtil.longConvertDateTime(timestamp);

        return Candlestick.builder()
                .id(timestamp)
                .symbol(symbol)
                .marketType(com.chain.ai.trade.common.entity.constants.MarketType.CRYPTO)
                .exchange(com.chain.ai.trade.common.entity.constants.Exchange.OKX)
                .candlestickIntervalEnum(interval)
                .timeStr(timeStr)
                .openPrice(new BigDecimal(okexCandle.getOpenPrice()))
                .highPrice(new BigDecimal(okexCandle.getHighPrice()))
                .lowPrice(new BigDecimal(okexCandle.getLowPrice()))
                .closePrice(new BigDecimal(okexCandle.getClosePrice()))
                .volume(new BigDecimal(okexCandle.getVolume()))
                .amount(okexCandle.getVolumeCcy() != null ? new BigDecimal(okexCandle.getVolumeCcy()) :
                        new BigDecimal(okexCandle.getVolume()).multiply(new BigDecimal(okexCandle.getClosePrice())))
                .count(BigDecimal.ZERO)
                .confirm("1")
                .build();
    }

    /**
     * 将CandlestickIntervalEnum映射到OKX的bar参数
     */
    private String mapIntervalToOkxBar(CandlestickIntervalEnum interval) {
        if (interval == null) {
            return "3m";
        }

        switch (interval) {
            case OKXMIN1:
            case OKXMIN3:
            case OKXMIN5:
            case OKXMIN15:
            case OKXMIN30:
            case OKXMIN60:
            case OKX4HOUR:
            case OKX1D:
                return interval.getCode();
            default:
                Integer minNum = interval.getMinNum();
                if (minNum != null) {
                    if (minNum < 60) {
                        return minNum + "m";
                    } else if (minNum == 60) {
                        return "1H";
                    } else if (minNum < 1440) {
                        int hours = minNum / 60;
                        return hours + "H";
                    } else if (minNum == 1440) {
                        return "1D";
                    } else if (minNum == 10080) {
                        return "1W";
                    } else if (minNum == 43200) {
                        return "1M";
                    }
                }
                return "3m";
        }
    }

    /**
     * 解析OKX返回的K线数据
     * OKX K线数据格式: [timestamp, open, high, low, close, volume, volumeCcy]
     */
    private Candlestick parseOkxKlineData(JSONArray kline, String symbol, CandlestickIntervalEnum interval) {
        String timestamp = kline.getStr(0);
        long tsMs = Long.parseLong(timestamp);
        String openStr = kline.getStr(1);
        String highStr = kline.getStr(2);
        String lowStr = kline.getStr(3);
        String closeStr = kline.getStr(4);
        String volumeStr = kline.getStr(5);
        String volumeCcyStr = kline.getStr(6);

        return Candlestick.builder()
                .id(tsMs)
                .symbol(symbol)
                .marketType(com.chain.ai.trade.common.entity.constants.MarketType.CRYPTO)
                .exchange(com.chain.ai.trade.common.entity.constants.Exchange.OKX)
                .candlestickIntervalEnum(interval)
                .timeStr(DateUtil.longConvertDateTime(tsMs))
                .openPrice(new BigDecimal(openStr))
                .highPrice(new BigDecimal(highStr))
                .lowPrice(new BigDecimal(lowStr))
                .closePrice(new BigDecimal(closeStr))
                .volume(new BigDecimal(volumeStr))
                .amount(new BigDecimal(volumeCcyStr != null ? volumeCcyStr : "0"))
                .count(new BigDecimal("0"))
                .confirm("1")
                .build();
    }

    /**
     * 获取合约规格信息（面值、乘数）
     * 通过 xchange OkexMarketDataServiceRaw.getInstruments 获取
     */
    public com.chain.ai.trade.common.entity.dto.ContractSpec getContractSpec(String symbol) {
        log.info("获取OKX合约规格信息（使用 xchange OkexMarketDataServiceRaw）: symbol={}", symbol);

        if (exchange == null) {
            log.warn("Exchange未初始化，使用默认规格: symbol={}", symbol);
            return com.chain.ai.trade.common.entity.dto.ContractSpec.defaultSpec();
        }

        try {
            MarketDataService marketDataService = exchange.getMarketDataService();
            if (!(marketDataService instanceof OkexMarketDataServiceRaw)) {
                log.warn("MarketDataService 不是 OkexMarketDataServiceRaw，使用默认规格: symbol={}", symbol);
                return com.chain.ai.trade.common.entity.dto.ContractSpec.defaultSpec();
            }

            OkexMarketDataServiceRaw okexMarketDataServiceRaw = (OkexMarketDataServiceRaw) marketDataService;

            OkexResponse<List<OkexInstrument>> response = okexMarketDataServiceRaw.getOkexInstruments("SWAP", null, symbol);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                log.warn("xchange返回的合约信息为空，使用默认规格: symbol={}", symbol);
                return com.chain.ai.trade.common.entity.dto.ContractSpec.defaultSpec();
            }

            OkexInstrument instrument = response.getData().get(0);
            String ctVal = instrument.getContractValue();
            String ctMult = instrument.getContractMultiplier();

            BigDecimal contractSize = ctVal != null && !ctVal.isEmpty() ?
                    new BigDecimal(ctVal) : com.chain.ai.trade.common.entity.dto.ContractSpec.DEFAULT_CONTRACT_SIZE;
            BigDecimal contractMult = ctMult != null && !ctMult.isEmpty() ?
                    new BigDecimal(ctMult) : com.chain.ai.trade.common.entity.dto.ContractSpec.DEFAULT_CONTRACT_MULT;

            log.info("通过 xchange OkexMarketDataServiceRaw.getOkexInstruments 获取合约规格成功: symbol={}, contractSize={}, contractMult={}",
                    symbol, contractSize, contractMult);

            return com.chain.ai.trade.common.entity.dto.ContractSpec.builder()
                    .contractSize(contractSize)
                    .contractMult(contractMult)
                    .build();

        } catch (Exception e) {
            log.error("通过 xchange 获取合约规格失败，使用默认规格: symbol={}, error={}", symbol, e.getMessage(), e);
            return com.chain.ai.trade.common.entity.dto.ContractSpec.defaultSpec();
        }
    }
}
