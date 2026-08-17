package com.chain.ai.trade.order.service.impl;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.engine.xchange.dto.MarketDepth;
import com.chain.ai.trade.member.dto.AccountSecrets;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GateioDirectTradeService implements ExchangeTradeService {

    // 实盘URL
    private static final String LIVE_BASE_URL = "https://api.gateio.ws/api/v4";
    private static final String LIVE_FUTURES_BASE_URL = "https://fx-api.gateio.ws/api/v4";
    // 模拟盘URL（官方文档：测试网统一使用 api-testnet.gateapi.io）
    private static final String TESTNET_BASE_URL = "https://api-testnet.gateapi.io/api/v4";
    private static final String TESTNET_FUTURES_BASE_URL = "https://api-testnet.gateapi.io/api/v4";

    private static final String FUTURES_SETTLE = "usdt";

    /** TradFi(外汇/CFD) 下单 API 路径 */
    private static final String TRADFI_ORDERS_PATH = "/tradfi/orders";
    /** TradFi 品种正则：纯字母交易对，如 EURUSD、XAGUSD */
    private static final String TRADFI_SYMBOL_PATTERN = "^[A-Za-z]{4,12}$";

    /** 合约价格最小变动单位缓存 */
    private final Map<String, BigDecimal> contractTickSizeCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final TradingAccount account;
    private final boolean simulated;
    private final String baseUrl;
    private final String futuresBaseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final OkHttpClient httpClient;

    public GateioDirectTradeService(TradingAccount account) {
        this.account = account;
        this.simulated = account.getSimulated() != null && account.getSimulated();
        this.baseUrl = this.simulated ? TESTNET_BASE_URL : LIVE_BASE_URL;
        this.futuresBaseUrl = this.simulated ? TESTNET_FUTURES_BASE_URL : LIVE_FUTURES_BASE_URL;

        String k = null;
        String s = null;
        try {
            AccountSecretsService secretsService = SpringContextUtil.getBean(AccountSecretsService.class);
            AccountSecrets secrets = secretsService.getAccountSecrets(account.getId());
            k = secrets.getApiKey() != null ? new String(secrets.getApiKey()) : null;
            s = secrets.getApiSecret() != null ? new String(secrets.getApiSecret()) : null;
        } catch (Exception e) {
            log.warn("获取GATEIO账户密钥异常", e);
        }
        this.apiKey = k;
        this.apiSecret = s;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("API Secret 不能为空");
        }

        // 诊断日志：打印KEY的前4位和后4位，确认加解密是否正确
        log.info("GATEIO API Key 诊断: {}...{} (len={}, simulated={}, baseUrl={})",
                apiKey.length() > 4 ? apiKey.substring(0, 4) : apiKey,
                apiKey.length() > 4 ? apiKey.substring(apiKey.length() - 4) : apiKey,
                apiKey.length(),
                this.simulated, this.baseUrl);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        log.info("初始化GATEIO直连交易服务成功，账户ID: {}, simulated: {}, baseUrl: {}", account.getId(), this.simulated, this.baseUrl);
    }

    // ==================== 签名工具 ====================

    /**
     * 生成GATEIO API v4签名
     * 签名字符串: METHOD\nURL\nQUERY_STRING\nSHA512(PAYLOAD)\nTIMESTAMP
     */
    private Map<String, String> buildHeaders(String method, String url, String queryString, String payload) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String bodyHash = sha512Hex(payload == null ? "" : payload);

        String signStr = method.toUpperCase() + "\n"
                + url + "\n"
                + (queryString == null ? "" : queryString) + "\n"
                + bodyHash + "\n"
                + timestamp;

        String sign = hmacSha512Hex(apiSecret, signStr);

        Map<String, String> headers = new HashMap<>();
        headers.put("KEY", apiKey);
        headers.put("Timestamp", timestamp);
        headers.put("SIGN", sign);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static String sha512Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-512计算失败", e);
        }
    }

    private static String hmacSha512Hex(String secret, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA512计算失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== HTTP请求 ====================

    /** 从baseUrl中提取URL路径前缀，如 https://xxx/api/v4 → /api/v4 */
    private String getUrlPrefix(String baseUrl) {
        try {
            return new java.net.URL(baseUrl).getPath();
        } catch (Exception e) {
            return "";
        }
    }

    /** 构造签名用的完整路径（包含/api/v4前缀） */
    private String signPath(String baseUrl, String path) {
        return getUrlPrefix(baseUrl) + path;
    }

    /** GET请求（无认证） */
    private String get(String baseUrl, String path, String queryString) throws IOException {
        String url = baseUrl + path + (queryString != null ? "?" + queryString : "");
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    /** GET请求（带签名认证） */
    private String authGet(String baseUrl, String path, String queryString) throws IOException {
        String fullPath = path + (queryString != null ? "?" + queryString : "");
        Map<String, String> headers = buildHeaders("GET", signPath(baseUrl, path), queryString, "");

        String url = baseUrl + fullPath;
        Request.Builder builder = new Request.Builder().url(url).get();
        headers.forEach(builder::addHeader);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    /** POST请求（带签名认证） */
    private String authPost(String baseUrl, String path, String jsonPayload) throws IOException {
        Map<String, String> headers = buildHeaders("POST", signPath(baseUrl, path), "", jsonPayload);

        String url = baseUrl + path;
        RequestBody requestBody = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        headers.forEach(builder::addHeader);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    /** PUT请求（带签名认证） */
    private String authPut(String baseUrl, String path, String jsonPayload) throws IOException {
        Map<String, String> headers = buildHeaders("PUT", signPath(baseUrl, path), "", jsonPayload);

        String url = baseUrl + path;
        RequestBody requestBody = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder().url(url).put(requestBody);
        headers.forEach(builder::addHeader);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    /** DELETE请求（带签名认证） */
    private String authDelete(String baseUrl, String path, String queryString) throws IOException {
        String fullPath = path + (queryString != null ? "?" + queryString : "");
        Map<String, String> headers = buildHeaders("DELETE", signPath(baseUrl, path), queryString, "");

        String url = baseUrl + fullPath;
        Request.Builder builder = new Request.Builder().url(url).delete();
        headers.forEach(builder::addHeader);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    // ==================== 工具方法 ====================

    /** 转换交易对符号为GATEIO格式: ETH-USDT-SWAP -> ETH_USDT */
    private String convertSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.toUpperCase().replace("/", "_").replace("-", "_");
        // 去掉OKX风格的_SWAP后缀，GATEIO合约名如 ETH_USDT
        if (s.endsWith("_SWAP")) {
            s = s.substring(0, s.length() - 5);
        }
        return s;
    }

    // ==================== 接口实现 ====================

    @Override
    public String createOrder(TradingStrategyParams params) {
        log.info("GATEIO直连下单: symbol={}, side={}, amount={}",
                params.getSymbol(), params.getSide(), params.getAmount());

        try {
            // TradFi 品种走 TradFi 下单接口（外汇/CFD）
            if (isTradfiSymbol(params.getSymbol())) {
                return createTradfiOrder(params);
            }
            // GATEIO USDT永续合约下单
            String gateSymbol = convertSymbol(params.getSymbol());
            return createFuturesOrder(params, gateSymbol);
        } catch (Exception e) {
            log.error("GATEIO直连下单失败: {}", e.getMessage(), e);
            throw new RuntimeException("GATEIO直连下单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 TradFi 品种当前价格（通过 klines 接口获取最近一根K线的收盘价）
     */
    private BigDecimal getTradfiCurrentPrice(String symbol) {
        try {
            String path = "/tradfi/symbols/" + symbol + "/klines";
            String query = "kline_type=1m&limit=1";
            String respBody = get(this.baseUrl, path, query);
            com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
            com.alibaba.fastjson.JSONObject data = resp.getJSONObject("data");
            if (data != null) {
                com.alibaba.fastjson.JSONArray list = data.getJSONArray("list");
                if (list != null && !list.isEmpty()) {
                    com.alibaba.fastjson.JSONObject item = list.getJSONObject(0);
                    String close = item.getString("c");
                    if (close != null) {
                        return new BigDecimal(close);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取TradFi品种价格失败: symbol={}, error={}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 判断是否为 TradFi 品种（纯字母交易对，如 EURUSD、XAGUSD）
     */
    private boolean isTradfiSymbol(String symbol) {
        return symbol != null && symbol.matches(TRADFI_SYMBOL_PATTERN);
    }

    /**
     * 创建 Gate.io TradFi 订单（外汇/CFD）
     * POST /tradfi/orders
     */
    private String createTradfiOrder(TradingStrategyParams params) throws IOException {
        String symbol = params.getSymbol();
        boolean isBuy = "BUY".equalsIgnoreCase(params.getSide());
        // TradFi side: 1=买入, 2=卖出
        int tradfiSide = isBuy ? 1 : 2;

        BigDecimal amount = params.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("TradFi下单数量不能为空或零");
        }

        // 判断订单类型
        boolean useLimit = params.getPrice() != null && params.getPrice().compareTo(BigDecimal.ZERO) > 0;
        if (params.getEntryType() == com.chain.ai.trade.common.entity.constants.OrderPriceType.MARKET) {
            useLimit = false;
        } else if (params.getEntryType() == com.chain.ai.trade.common.entity.constants.OrderPriceType.LIMIT) {
            useLimit = true;
        }

        com.alibaba.fastjson.JSONObject order = new com.alibaba.fastjson.JSONObject();
        order.put("symbol", symbol);
        order.put("side", tradfiSide);
        order.put("volume", amount.toPlainString());

        if (useLimit) {
            order.put("price", params.getPrice().toPlainString());
            order.put("price_type", "trigger");
        } else {
            order.put("price", "0");
            order.put("price_type", "market");
        }

        // 止盈止损直接在订单中设置
        if (params.getTakeProfitPrice() != null && params.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0) {
            order.put("price_tp", params.getTakeProfitPrice().toPlainString());
        }
        if (params.getStopLossPrice() != null && params.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0) {
            order.put("price_sl", params.getStopLossPrice().toPlainString());
        }

        String jsonPayload = order.toJSONString();
        log.info("GATEIO TradFi下单: symbol={}, side={}, volume={}, price_type={}",
                symbol, tradfiSide, amount.toPlainString(), order.getString("price_type"));

        String respBody = authPost(this.baseUrl, TRADFI_ORDERS_PATH, jsonPayload);
        com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);

        // TradFi 订单响应格式: { "code":0, "message":"success", "data":{ "log_id":xxx }, "timestamp":xxx }
        Integer code = resp.getInteger("code");
        if (code == null || code != 0) {
            String message = resp.getString("message");
            String label = resp.getString("label");
            throw new RuntimeException("GATEIO TradFi下单失败: " + label + " - " + message);
        }
        com.alibaba.fastjson.JSONObject data = resp.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("GATEIO TradFi下单响应无data字段");
        }
        Long logId = data.getLong("log_id");
        if (logId == null) {
            throw new RuntimeException("GATEIO TradFi下单失败: 未获取到log_id");
        }
        String orderId = String.valueOf(logId);
        log.info("GATEIO TradFi下单成功: log_id={}, symbol={}, side={}, volume={}",
                orderId, symbol, tradfiSide, amount.toPlainString());
        return orderId;
    }

    /**
     * 创建GATEIO永续合约订单
     */
    private String createFuturesOrder(TradingStrategyParams params, String contract) throws IOException {
        log.info("创建GATEIO合约订单: contract={}", contract);

        // 设置杠杆（失败不阻塞下单，使用账户默认杠杆）
        if (params.getLeverage() != null && params.getLeverage() > 0) {
            try {
                setLeverageInternal(contract, params.getLeverage());
            } catch (Exception e) {
                log.warn("设置GATEIO杠杆失败，继续使用当前杠杆: {}", e.getMessage());
            }
        }

        // 下单
        com.alibaba.fastjson.JSONObject order = new com.alibaba.fastjson.JSONObject();
        order.put("contract", contract);

        // GATEIO: size > 0 开多/平空, size < 0 开空/平多
        boolean isBuy = "BUY".equalsIgnoreCase(params.getSide());
        boolean isExit = params.getOrderType() != null
                && (params.getOrderType().name().contains("EXIT") || params.getOrderType().name().contains("CLOSE"));

        BigDecimal amount = params.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("下单数量不能为空或零");
        }

        long size;
        if (isExit) {
            // 平仓: 方向取反
            size = isBuy ? -amount.longValue() : amount.longValue();
        } else {
            // 开仓: 正向
            size = isBuy ? amount.longValue() : -amount.longValue();
        }
        order.put("size", size);

        // 价格策略：优先根据信号入场类型判断，兼容无entryType的旧流程
        boolean useLimit;
        if (params.getEntryType() == OrderPriceType.MARKET) {
            useLimit = false;
        } else if (params.getEntryType() == OrderPriceType.LIMIT || params.getEntryType() == OrderPriceType.CONDITION) {
            useLimit = true;
        } else {
            // 兼容旧逻辑：通过price字段判断
            useLimit = params.getPrice() != null && params.getPrice().compareTo(BigDecimal.ZERO) > 0;
        }
        if (useLimit) {
            order.put("price", params.getPrice().toPlainString());
            order.put("tif", "gtc");
        } else {
            order.put("price", "0"); // 市价单
            order.put("tif", "ioc");
        }

        // 下单文本（自定义订单标识，GATEIO限制最长30字符，以 t- 开头）
        if (StringUtils.isNotBlank(params.getPositionId())) {
            String text = params.getPositionId().startsWith("t-") ? params.getPositionId() : "t-" + params.getPositionId();
            if (text.length() > 30) {
                text = text.substring(0, 30);
            }
            order.put("text", text);
        }

        String jsonPayload = order.toJSONString();
        String path = "/futures/" + FUTURES_SETTLE + "/orders";
        String respBody = authPost(this.futuresBaseUrl, path, jsonPayload);

        com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
        String orderId = resp.getString("id");
        if (StringUtils.isBlank(orderId)) {
            String label = resp.getString("label");
            String errMsg = resp.getString("message");
            throw new RuntimeException("GATEIO下单失败: label=" + label + ", message=" + errMsg);
        }

        log.info("GATEIO合约下单成功: orderId={}, contract={}, size={}", orderId, contract, size);

        // 设置止盈止损：先查询实际持仓，确保存在后再设置
        if (params.getTakeProfitPrice() != null || params.getStopLossPrice() != null) {
            try {
                String symbol = params.getSymbol();
                // 查询当前持仓
                String posPath = "/futures/" + FUTURES_SETTLE + "/positions/" + contract;
                String posBody = authGet(this.futuresBaseUrl, posPath, null);
                com.alibaba.fastjson.JSONObject pos = com.alibaba.fastjson.JSONObject.parseObject(posBody);
                long actualPositionSize = pos.getLongValue("size");
                if (actualPositionSize == 0) {
                    log.warn("GATEIO下单后无持仓，跳过设置止盈止损: contract={}", contract);
                    return orderId;
                }
                // 校验持仓方向与下单方向一致（size正负与下单size同号）
                if (Long.signum(actualPositionSize) != Long.signum(size)) {
                    log.warn("GATEIO下单后持仓方向与下单方向不一致，跳过设置止盈止损: contract={}, actualSize={}, orderSize={}",
                            contract, actualPositionSize, size);
                    return orderId;
                }
                BigDecimal gainPrice = params.getTakeProfitPrice();
                BigDecimal lossPrice = params.getStopLossPrice();
                log.info("GATEIO下单后设置止盈止损: symbol={}, gainPrice={}, lossPrice={}, actualPositionSize={}",
                        symbol, gainPrice, lossPrice, actualPositionSize);
                createTpSlOrders(contract, gainPrice, lossPrice, actualPositionSize);
            } catch (Exception e) {
                // 止盈止损失败不阻塞下单
                log.warn("GATEIO下单后设置止盈止损失败，不影响下单: {}", e.getMessage());
            }
        }

        return orderId;
    }

    @Override
    public boolean cancelOrder(String orderId, String symbol) {
        log.info("GATEIO直连撤单: orderId={}", orderId);
        // TradFi 不支持单独撤单（MT5基于平仓），此处仅做记录
        if (isTradfiSymbol(symbol)) {
            log.warn("GATEIO TradFi不支持撤单操作: symbol={}, orderId={}", symbol, orderId);
            return false;
        }
        try {
            String path = "/futures/" + FUTURES_SETTLE + "/orders/" + orderId;
            authDelete(this.futuresBaseUrl, path, null);
            log.info("GATEIO撤单成功: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.warn("GATEIO撤单失败: orderId={}, error={}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> cancelAllOrders(String symbol) {
        log.info("GATEIO直连撤销所有挂单: symbol={}", symbol);
        return Collections.emptyList();
    }

    @Override
    public String getOrderStatus(String orderId) {
        log.info("GATEIO直连查询订单状态: orderId={}", orderId);
        try {
            String path = "/futures/" + FUTURES_SETTLE + "/orders/" + orderId;
            String respBody = authGet(this.futuresBaseUrl, path, null);
            com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
            return resp.getString("status");
        } catch (Exception e) {
            log.error("查询订单状态异常: orderId={}, error={}", orderId, e.getMessage());
            return "error";
        }
    }

    @Override
    public BigDecimal getAccountBalance(String currency) {
        try {
            String path = "/futures/" + FUTURES_SETTLE + "/accounts";
            String respBody = authGet(this.futuresBaseUrl, path, null);
            com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
            if (currency != null) {
                String avail = resp.getString("available");
                if (avail != null) {
                    return new BigDecimal(avail);
                }
            } else {
                // 返回总权益（折合USDT）
                String total = resp.getString("total");
                if (total != null) {
                    return new BigDecimal(total);
                }
            }
        } catch (Exception e) {
            log.warn("查询账户余额异常: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        // TradFi 品种走 klines 获取最新价格
        if (isTradfiSymbol(symbol)) {
            return getTradfiCurrentPrice(symbol);
        }
        try {
            String gateSymbol = convertSymbol(symbol);
            // 先尝试从合约ticker获取
            String query = "contract=" + gateSymbol;
            String path = "/futures/" + FUTURES_SETTLE + "/tickers";
            String respBody = get(this.futuresBaseUrl, path, query);
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(respBody);
            if (arr != null && !arr.isEmpty()) {
                String last = arr.getJSONObject(0).getString("last");
                if (last != null) {
                    return new BigDecimal(last);
                }
            }
        } catch (Exception e) {
            log.warn("查询合约价格异常: symbol={}, error={}", symbol, e.getMessage());
        }
        // 备用: 从现货获取
        try {
            String gateSymbol = convertSymbol(symbol);
            String query = "currency_pair=" + gateSymbol;
            String respBody = get(this.baseUrl, "/spot/tickers", query);
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(respBody);
            if (arr != null && !arr.isEmpty()) {
                String last = arr.getJSONObject(0).getString("last");
                if (last != null) {
                    return new BigDecimal(last);
                }
            }
        } catch (Exception e) {
            log.warn("查询现货价格异常: symbol={}, error={}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public MarketDepth getMarketDepth(String symbol, int depth) {
        try {
            String gateSymbol = convertSymbol(symbol);
            String query = "currency_pair=" + gateSymbol + "&limit=" + Math.min(depth, 50);
            String respBody = get(this.baseUrl, "/spot/order_book", query);
            com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
            List<MarketDepth.DepthLevel> bids = new ArrayList<>();
            List<MarketDepth.DepthLevel> asks = new ArrayList<>();
            com.alibaba.fastjson.JSONArray bidsArr = resp.getJSONArray("bids");
            com.alibaba.fastjson.JSONArray asksArr = resp.getJSONArray("asks");
            if (bidsArr != null) {
                for (int i = 0; i < bidsArr.size(); i++) {
                    com.alibaba.fastjson.JSONArray lvl = bidsArr.getJSONArray(i);
                    bids.add(MarketDepth.DepthLevel.builder()
                            .price(new BigDecimal(lvl.getString(0)))
                            .quantity(new BigDecimal(lvl.getString(1)))
                            .build());
                }
            }
            if (asksArr != null) {
                for (int i = 0; i < asksArr.size(); i++) {
                    com.alibaba.fastjson.JSONArray lvl = asksArr.getJSONArray(i);
                    asks.add(MarketDepth.DepthLevel.builder()
                            .price(new BigDecimal(lvl.getString(0)))
                            .quantity(new BigDecimal(lvl.getString(1)))
                            .build());
                }
            }
            return MarketDepth.builder()
                    .symbol(symbol)
                    .bids(bids)
                    .asks(asks)
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.warn("查询市场深度异常: symbol={}", symbol, e);
            return MarketDepth.builder()
                    .symbol(symbol)
                    .bids(Collections.emptyList())
                    .asks(Collections.emptyList())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    @Override
    public boolean setLeverage(String symbol, int leverage) {
        try {
            String contract = convertSymbol(symbol);
            setLeverageInternal(contract, leverage);
            return true;
        } catch (Exception e) {
            log.warn("设置杠杆异常: {}", e.getMessage());
            return false;
        }
    }

    private void setLeverageInternal(String contract, int leverage) throws IOException {
        log.info("设置GATEIO杠杆: contract={}, leverage={}", contract, leverage);
        com.alibaba.fastjson.JSONObject body = new com.alibaba.fastjson.JSONObject();
        body.put("leverage", String.valueOf(leverage));
        String path = "/futures/" + FUTURES_SETTLE + "/positions/" + contract + "/leverage";
        authPost(this.futuresBaseUrl, path, body.toJSONString());
    }

    @Override
    public boolean closeAllPositions(String symbol) {
        return false;
    }

    @Override
    public List<Candlestick> getCandlestick(CandlestickRequest request) {
        try {
            String gateSymbol = convertSymbol(request.getSymbol());
            String interval = convertInterval(request.getInterval());
            String query = "currency_pair=" + gateSymbol
                    + "&interval=" + interval
                    + "&limit=" + request.getSize();
            String respBody = get(this.baseUrl, "/spot/candlesticks", query);
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(respBody);
            return parseCandlestickArray(arr, request.getSymbol(), request.getInterval());
        } catch (Exception e) {
            log.warn("获取K线异常: symbol={}", request.getSymbol(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Candlestick> getHistoryCandlestick(CandlestickRequest request) {
        // GATEIO的 candlesticks 接口本身支持历史查询（带 from/to）
        try {
            String gateSymbol = convertSymbol(request.getSymbol());
            String interval = convertInterval(request.getInterval());
            StringBuilder query = new StringBuilder("currency_pair=" + gateSymbol
                    + "&interval=" + interval
                    + "&limit=" + request.getSize());
            if (request.getFrom() != 0) {
                query.append("&from=").append(request.getFrom());
            }
            if (request.getTo() != 0) {
                query.append("&to=").append(request.getTo());
            }
            String respBody = get(this.baseUrl, "/spot/candlesticks", query.toString());
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(respBody);
            return parseCandlestickArray(arr, request.getSymbol(), request.getInterval());
        } catch (Exception e) {
            log.warn("获取历史K线异常: symbol={}", request.getSymbol(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 直接创建止盈止损触发订单（已知持仓方向，不依赖持仓查询）
     * @param contract 合约名
     * @param gainPrice 止盈价
     * @param lossPrice 止损价
     * @param positionSize 持仓数量（正=多仓，负=空仓）
     */
    private boolean createTpSlOrders(String contract, BigDecimal gainPrice, BigDecimal lossPrice, long positionSize) {
        try {
            // 取消已有的价格触发订单（止盈/止损）
            cancelPriceTriggerOrders(contract);

            // 创建止盈触发订单
            if (gainPrice != null && gainPrice.compareTo(BigDecimal.ZERO) > 0) {
                int rule = positionSize > 0 ? 1 : 2;
                String orderType = positionSize > 0 ? "close-long-position" : "close-short-position";
                String jsonBody = buildPriceTriggerOrder(contract, gainPrice, rule, orderType);
                authPost(this.futuresBaseUrl, "/futures/" + FUTURES_SETTLE + "/price_orders", jsonBody);
                log.info("GATEIO创建止盈触发订单成功: contract={}, gainPrice={}", contract, gainPrice);
            }

            // 创建止损触发订单
            if (lossPrice != null && lossPrice.compareTo(BigDecimal.ZERO) > 0) {
                int rule = positionSize > 0 ? 2 : 1;
                String orderType = positionSize > 0 ? "close-long-position" : "close-short-position";
                String jsonBody = buildPriceTriggerOrder(contract, lossPrice, rule, orderType);
                authPost(this.futuresBaseUrl, "/futures/" + FUTURES_SETTLE + "/price_orders", jsonBody);
                log.info("GATEIO创建止损触发订单成功: contract={}, lossPrice={}", contract, lossPrice);
            }

            return true;
        } catch (Exception e) {
            log.error("GATEIO创建止盈止损触发订单失败: contract={}", contract, e);
            return false;
        }
    }

    @Override
    public boolean amendTpSl(String orderSn, String symbol, BigDecimal gainPrice, BigDecimal lossPrice) {
        log.info("GATEIO修改止盈止损: orderSn={}, symbol={}, gainPrice={}, lossPrice={}",
                orderSn, symbol, gainPrice, lossPrice);
        try {
            String contract = convertSymbol(symbol);

            // 获取当前持仓大小
            String posPath = "/futures/" + FUTURES_SETTLE + "/positions/" + contract;
            String posBody = authGet(this.futuresBaseUrl, posPath, null);
            com.alibaba.fastjson.JSONObject pos = com.alibaba.fastjson.JSONObject.parseObject(posBody);
            long positionSize = pos.getLongValue("size");
            if (positionSize == 0) {
                log.warn("GATEIO无持仓, 跳过设置止盈止损: contract={}", contract);
                return false;
            }
            log.info("GATEIO当前持仓: contract={}, size={}", contract, positionSize);

            return createTpSlOrders(contract, gainPrice, lossPrice, positionSize);
        } catch (Exception e) {
            log.error("GATEIO修改止盈止损失败: orderSn={}", orderSn, e);
            return false;
        }
    }

    // ==================== 价格触发订单辅助方法 ====================

    /** 取消指定合约的所有OPEN状态价格触发订单 */
    private void cancelPriceTriggerOrders(String contract) throws IOException {
        String path = "/futures/" + FUTURES_SETTLE + "/price_orders";
        String query = "contract=" + contract + "&status=open";
        try {
            String respBody = authGet(this.futuresBaseUrl, path, query);
            com.alibaba.fastjson.JSONArray arr = com.alibaba.fastjson.JSONArray.parseArray(respBody);
            if (arr != null && !arr.isEmpty()) {
                for (int i = 0; i < arr.size(); i++) {
                    com.alibaba.fastjson.JSONObject order = arr.getJSONObject(i);
                    String id = order.getString("id");
                    if (id != null) {
                        try {
                            authDelete(this.futuresBaseUrl, path + "/" + id, null);
                            log.info("取消已有价格触发订单: id={}, type={}", id, order.getString("order_type"));
                        } catch (Exception e) {
                            log.warn("取消价格触发订单失败: id={}, error={}", id, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 查询历史触发订单异常时，忽略（可能没有历史订单）
            log.warn("查询历史价格触发订单异常: contract={}, error={}", contract, e.getMessage());
        }
    }

    /** 获取合约价格最小变动单位（tick size），带缓存 */
    private BigDecimal getContractTickSize(String contract) throws IOException {
        BigDecimal cached = contractTickSizeCache.get(contract);
        if (cached != null) {
            return cached;
        }
        String path = "/futures/" + FUTURES_SETTLE + "/contracts/" + contract;
        String respBody = authGet(this.futuresBaseUrl, path, null);
        com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);
        // order_price_round 是 tick size，如 "0.001"、"0.01"、"0.1"、"1"
        String round = resp.getString("order_price_round");
        BigDecimal tickSize = new BigDecimal(round);
        contractTickSizeCache.put(contract, tickSize);
        log.debug("GATEIO合约精度: contract={}, order_price_round={}", contract, round);
        return tickSize;
    }

    /**
     * 获取合约规格信息（面值、乘数）
     * 通过 Gateio 永续合约 API /futures/{settle}/contracts/{contract} 获取
     */
    public ContractSpec getContractSpec(String symbol) {
        log.info("获取GATEIO合约规格信息: symbol={}", symbol);

        try {
            String contract = convertSymbol(symbol);
            String path = "/futures/" + FUTURES_SETTLE + "/contracts/" + contract;
            String respBody = authGet(this.futuresBaseUrl, path, null);

            com.alibaba.fastjson.JSONObject resp = com.alibaba.fastjson.JSONObject.parseObject(respBody);

            // Gateio USDT永续合约的面值通过 quanto_multiplier 字段获取
            String quantoMult = resp.getString("quanto_multiplier");

            log.info("GATEIO合约信息: name={}, quanto_multiplier={}", resp.getString("name"), quantoMult);

            BigDecimal contractSize = quantoMult != null && !quantoMult.isEmpty()
                    ? new BigDecimal(quantoMult)
                    : ContractSpec.DEFAULT_CONTRACT_SIZE;
            BigDecimal contractMult = ContractSpec.DEFAULT_CONTRACT_MULT;

            log.info("获取GATEIO合约规格成功: symbol={}, contractSize={}, contractMult={}",
                    symbol, contractSize, contractMult);

            return ContractSpec.builder()
                    .contractSize(contractSize)
                    .contractMult(contractMult)
                    .build();

        } catch (Exception e) {
            log.error("获取GATEIO合约规格失败，使用默认规格: symbol={}, error={}", symbol, e.getMessage(), e);
            return ContractSpec.defaultSpec();
        }
    }

    /** 将价格舍入到最小变动单位的整数倍（向下取整） */
    private BigDecimal roundToTickSize(BigDecimal price, BigDecimal tickSize) {
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }
        // 用除法取整实现向下舍入到 tickSize 的整数倍
        BigDecimal[] divAndRem = price.divideAndRemainder(tickSize);
        return divAndRem[0].multiply(tickSize);
    }

    /** 构建价格触发订单JSON */
    private String buildPriceTriggerOrder(String contract, BigDecimal triggerPrice, int rule, String orderType) {
        com.alibaba.fastjson.JSONObject initial = new com.alibaba.fastjson.JSONObject();
        initial.put("contract", contract);
        initial.put("price", "0");
        initial.put("tif", "ioc");
        // 持仓级止盈止损(close-long/short-position): 设置close=true和size=0表示平全部仓位
        initial.put("close", true);
        initial.put("size", 0);

        // 将触发价格舍入到tick size的整数倍
        BigDecimal roundedPrice;
        try {
            BigDecimal tickSize = getContractTickSize(contract);
            roundedPrice = roundToTickSize(triggerPrice, tickSize);
            if (roundedPrice.compareTo(triggerPrice) != 0) {
                log.info("GATEIO触发价格精度调整: {} → {} (tickSize={})", triggerPrice, roundedPrice, tickSize);
            }
        } catch (Exception e) {
            log.warn("获取合约精度失败，使用原始价格: {}", triggerPrice, e);
            roundedPrice = triggerPrice;
        }

        com.alibaba.fastjson.JSONObject trigger = new com.alibaba.fastjson.JSONObject();
        trigger.put("price", roundedPrice.toPlainString());
        trigger.put("rule", rule);

        com.alibaba.fastjson.JSONObject order = new com.alibaba.fastjson.JSONObject();
        order.put("initial", initial);
        order.put("trigger", trigger);
        order.put("order_type", orderType);

        return order.toJSONString();
    }

    // ==================== 辅助方法 ====================

    private String convertInterval(CandlestickIntervalEnum interval) {
        if (interval == null) return "5m";
        String code = interval.getCode();
        // GATEIO: 5m, 15m, 30m, 1h, 4h, 1d, 1w
        return code.replace("m", "m")
                .replace("H", "h")
                .replace("D", "d")
                .replace("W", "w");
    }

    private List<Candlestick> parseCandlestickArray(com.alibaba.fastjson.JSONArray arr, String symbol,
                                                     CandlestickIntervalEnum interval) {
        if (arr == null || arr.isEmpty()) {
            return Collections.emptyList();
        }
        List<Candlestick> list = new ArrayList<>(arr.size());
        // GATEIO返回: [timestamp, open, high, low, close, volume, ...]
        for (int i = 0; i < arr.size(); i++) {
            com.alibaba.fastjson.JSONArray row = arr.getJSONArray(i);
            Candlestick c = new Candlestick();
            c.setId(row.getLongValue(0));
            c.setOpenPrice(new BigDecimal(row.getString(1)));
            c.setHighPrice(new BigDecimal(row.getString(2)));
            c.setLowPrice(new BigDecimal(row.getString(3)));
            c.setClosePrice(new BigDecimal(row.getString(4)));
            c.setVolume(new BigDecimal(row.getString(5)));
            c.setCandlestickIntervalEnum(interval);
            c.setSymbol(symbol);
            c.setMarketType(MarketType.CRYPTO);
            c.setExchange(Exchange.GATEIO);
            c.setTimeStr(com.chain.ai.trade.common.utils.DateUtil.longConvertDateTime(c.getId()));
            list.add(c);
        }
        return list;
    }
}
