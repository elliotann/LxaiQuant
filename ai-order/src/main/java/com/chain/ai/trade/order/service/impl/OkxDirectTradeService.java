package com.chain.ai.trade.order.service.impl;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.MarketType;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.xchange.dto.MarketDepth;
import com.chain.ai.trade.engine.xchange.utils.*;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import com.chain.ai.trade.member.dto.AccountSecrets;
import com.chain.ai.trade.member.service.AccountSecretsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class OkxDirectTradeService implements ExchangeTradeService {

    private final TradingAccount account;
    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private final OkxRestConnection restConnection;

    public OkxDirectTradeService(TradingAccount account) {
        this.account = account;

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

        // 创建OKHTTP连接
        OkxOptions options = OkxOptions.builder()
                .apiKey(this.apiKey)
                .secretKey(this.apiSecret)
                .passphrase(this.passphrase)
                .simulated(account.getSimulated())
                .build();
        
        this.restConnection = new OkxRestConnection(options);
        log.info("初始化OKX直连交易服务成功，账户ID: {}", account.getId());
    }

    @Override
    public String createOrder(TradingStrategyParams params) {
        log.info("OKX直连下单: symbol={}, side={}, amount={}",
                params.getSymbol(), params.getSide(), params.getAmount());

        try {
            boolean hasOverrideCreds = StringUtils.isNotBlank(params.getApiKey()) && StringUtils.isNotBlank(params.getSecretKey());
            OkxRestConnection conn;
            if (hasOverrideCreds) {
                OkxOptions options = OkxOptions.builder()
                        .apiKey(params.getApiKey())
                        .secretKey(params.getSecretKey())
                        .passphrase(params.getPassphrase())
                        .simulated(params.getSimulated() != null ? params.getSimulated() : account.getSimulated())
                        .build();
                conn = new OkxRestConnection(options);
            } else {
                conn = this.restConnection;
            }
            String okexSymbol = convertToOkexSymbol(params.getSymbol());
            boolean isContract = okexSymbol.contains("-SWAP") || okexSymbol.matches(".*-\\d{6}$");

            if (isContract) {
                return createContractOrder(params, okexSymbol, conn);
            } else {
                return createSpotOrder(params, okexSymbol, conn);
            }
        } catch (Exception e) {
            log.error("OKX直连下单失败: {}", e.getMessage(), e);
            throw new RuntimeException("OKX直连下单失败: " + e.getMessage(), e);
        }
    }

    private String createContractOrder(TradingStrategyParams params, String symbol, OkxRestConnection conn) throws IOException {
        log.info("创建合约订单: symbol={}", symbol);

        // 设置杠杆（如果需要）
        if (params.getLeverage() != null && params.getLeverage() > 0) {
            setLeverageInternal(symbol, params.getLeverage(), conn);
        }

        // 构建下单参数
        UrlParamsBuilder builder = buildOrderParams(params, symbol);
        
        // 调用OKX API下单
        com.alibaba.fastjson.JSONObject response = conn.executePostWithSignature("/api/v5/trade/order", builder);
        
        // 解析响应
        return parseOrderResponse(response);
    }

    private String createSpotOrder(TradingStrategyParams params, String symbol, OkxRestConnection conn) throws IOException {
        log.info("创建现货订单: symbol={}", symbol);

        // 构建下单参数
        UrlParamsBuilder builder = buildOrderParams(params, symbol);
        
        // 调用OKX API下单
        com.alibaba.fastjson.JSONObject response = conn.executePostWithSignature("/api/v5/trade/order", builder);
        
        // 解析响应
        return parseOrderResponse(response);
    }

    private UrlParamsBuilder buildOrderParams(TradingStrategyParams params, String symbol) {
        String side = "BUY".equals(params.getSide()) ? "buy" : "sell";
        boolean isContract = symbol.contains("-SWAP") || symbol.matches(".*-\\d{6}$");
        String tdMode = isContract ? "cross" : "cash";
        
        UrlParamsBuilder builder = UrlParamsBuilder.build()
                .putToPost("instId", symbol)
                .putToPost("tdMode", tdMode)
                .putToPost("clOrdId", params.getPositionId())
                .putToPost("sz", params.getAmount().setScale(8, RoundingMode.HALF_DOWN).toPlainString());
        boolean useLimit = params.getPrice() != null && params.getPrice().compareTo(BigDecimal.ZERO) > 0;
        if (useLimit) {
            builder.putToPost("ordType", "limit");
            builder.putToPost("px", params.getPrice().toPlainString());
        } else {
            builder.putToPost("ordType", "market");
        }

        // 设置仓位方向（合约）
        if (isContract) {
            boolean isExit = false;
            if (params.getOrderType() != null) {
                String typeName = params.getOrderType().name();
                isExit = typeName.contains("EXIT") || typeName.contains("CLOSE");
            }
            String posSide;
            String finalSide = side;
            if (isExit) {
                if ("buy".equals(side)) {
                    finalSide = "sell";
                    posSide = "long";
                } else {
                    finalSide = "buy";
                    posSide = "short";
                }
            } else {
                posSide = "buy".equals(side) ? "long" : "short";
            }
            builder.putToPost("side", finalSide);
            builder.putToPost("posSide", posSide);
        } else {
            builder.putToPost("side", side);
        }

        // 添加止盈止损（开仓时）
        boolean isExitForTpSl = false;
        if (params.getOrderType() != null) {
            String typeName = params.getOrderType().name();
            isExitForTpSl = typeName.contains("EXIT") || typeName.contains("CLOSE");
        }
        if (!isExitForTpSl) {
            boolean hasTp = params.getTakeProfitPrice() != null && params.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0;
            boolean hasSl = params.getStopLossPrice() != null && params.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0;
            if (hasTp || hasSl) {
                JSONArray attachAlgoOrds = new JSONArray();
                if (hasTp) {
                    JSONObject tp = new JSONObject();
                    tp.put("tpTriggerPx", params.getTakeProfitPrice().toPlainString());
                    tp.put("tpOrdPx", "-1");
                    attachAlgoOrds.add(tp);
                }
                if (hasSl) {
                    JSONObject sl = new JSONObject();
                    sl.put("slTriggerPx", params.getStopLossPrice().toPlainString());
                    sl.put("slOrdPx", "-1");
                    attachAlgoOrds.add(sl);
                }
                builder.putToPost("attachAlgoOrds", attachAlgoOrds);
            }
        }

        return builder;
    }

    private void setLeverageInternal(String symbol, int leverage, OkxRestConnection conn) {
        try {
            UrlParamsBuilder builder = UrlParamsBuilder.build()
                    .putToPost("instId", symbol)
                    .putToPost("mgnMode", "cross")
                    .putToPost("lever", leverage);

            com.alibaba.fastjson.JSONObject response = conn.executePostWithSignature("/api/v5/account/set-leverage", builder);
            String code = response.getString("code");
            if ("0".equals(code)) {
                log.info("设置杠杆成功: symbol={}, leverage={}", symbol, leverage);
            } else {
                log.warn("设置杠杆失败: symbol={}, leverage={}, 响应: {}", symbol, leverage, response.toJSONString());
            }
        } catch (Exception e) {
            log.warn("设置杠杆异常: symbol={}, leverage={}, 错误: {}", symbol, leverage, e.getMessage());
        }
    }

    private String parseOrderResponse(com.alibaba.fastjson.JSONObject response) {
        String code = response.getString("code");
        if (!"0".equals(code)) {
            String msg = response.getString("msg");
            throw new RuntimeException("OKX API返回错误: code=" + code + ", msg=" + msg);
        }

        // 解析订单ID
        com.alibaba.fastjson.JSONArray data = response.getJSONArray("data");
        if (data != null && !data.isEmpty()) {
            com.alibaba.fastjson.JSONObject orderData = data.getJSONObject(0);
            String ordId = orderData.getString("ordId");
            if (StringUtils.isNotEmpty(ordId)) {
                log.info("下单成功，订单ID: {}", ordId);
                return ordId;
            }
        }

        throw new RuntimeException("无法从响应中解析订单ID: " + response.toJSONString());
    }

    private String convertToOkexSymbol(String symbol) {
        if (symbol == null) return null;
        return symbol.toUpperCase().replace("/", "-").replace("_", "-");
    }

    @Override
    public boolean cancelOrder(String orderId, String symbol) {
        log.info("OKX直连撤单: orderId={}", orderId);

        try {
            String okxSymbol = convertToOkexSymbol(symbol);
            UrlParamsBuilder builder = UrlParamsBuilder.build()
                    .putToPost("instId", okxSymbol)
                    .putToPost("ordId", orderId);

            JSONObject response = restConnection.executePostWithSignature("/api/v5/trade/cancel-order", builder);
            String code = response.getString("code");
            
            if ("0".equals(code)) {
                log.info("撤单成功: orderId={}", orderId);
                return true;
            } else {
                log.warn("撤单失败: orderId={}, 响应: {}", orderId, response.toJSONString());
                return false;
            }
        } catch (Exception e) {
            log.error("撤单异常: orderId={}, 错误: {}", orderId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<String> cancelAllOrders(String symbol) {
        log.info("OKX直连撤销所有挂单: symbol={}", symbol);
        return Collections.emptyList();
    }

    @Override
    public String getOrderStatus(String orderId) {
        log.info("OKX直连查询订单状态: orderId={}", orderId);

        try {
            UrlParamsBuilder builder = UrlParamsBuilder.build()
                    .putToUrl("ordId", orderId);
            JSONObject response = restConnection.executeGetWithSignature("/api/v5/trade/order", builder);
            if ("0".equals(response.getString("code"))) {
                JSONArray data = response.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    JSONObject obj = data.getJSONObject(0);
                    return obj.getString("state");
                }
            }
            return "unknown";
        } catch (Exception e) {
            log.error("查询订单状态异常: orderId={}, 错误: {}", orderId, e.getMessage(), e);
            return "error";
        }
    }

    @Override
    public BigDecimal getAccountBalance(String currency) {
        try {
            JSONObject response = restConnection.executeGetWithSignature("/api/v5/account/balance", UrlParamsBuilder.build());
            if ("0".equals(response.getString("code"))) {
                JSONArray data = response.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    JSONArray details = data.getJSONObject(0).getJSONArray("details");
                    for (int i = 0; i < details.size(); i++) {
                        JSONObject d = details.getJSONObject(i);
                        if (currency == null || currency.equalsIgnoreCase(d.getString("ccy"))) {
                            String avail = d.getString("availBal");
                            if (avail != null) {
                                return new BigDecimal(avail);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询账户余额异常: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        try {
            String okxSymbol = convertToOkexSymbol(symbol);
            UrlParamsBuilder builder = UrlParamsBuilder.build().putToUrl("instId", okxSymbol);
            JSONObject response = restConnection.executeGet("/api/v5/market/ticker", builder);
            JSONArray data = response.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                String last = data.getJSONObject(0).getString("last");
                if (last != null) {
                    return new BigDecimal(last);
                }
            }
        } catch (Exception e) {
            log.warn("查询当前价格异常: symbol={}, 错误: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public MarketDepth getMarketDepth(String symbol, int depth) {
        try {
            String okxSymbol = convertToOkexSymbol(symbol);
            UrlParamsBuilder builder = UrlParamsBuilder.build()
                    .putToUrl("instId", okxSymbol)
                    .putToUrl("sz", depth);
            JSONObject response = restConnection.executeGet("/api/v5/market/books", builder);
            JSONArray data = response.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject book = data.getJSONObject(0);
                List<MarketDepth.DepthLevel> bids = new ArrayList<>();
                List<MarketDepth.DepthLevel> asks = new ArrayList<>();
                JSONArray bidsArr = book.getJSONArray("bids");
                JSONArray asksArr = book.getJSONArray("asks");
                if (bidsArr != null) {
                    for (int i = 0; i < bidsArr.size(); i++) {
                        JSONArray lvl = bidsArr.getJSONArray(i);
                        bids.add(MarketDepth.DepthLevel.builder()
                                .price(new BigDecimal(lvl.getString(0)))
                                .quantity(new BigDecimal(lvl.getString(1)))
                                .build());
                    }
                }
                if (asksArr != null) {
                    for (int i = 0; i < asksArr.size(); i++) {
                        JSONArray lvl = asksArr.getJSONArray(i);
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
            }
        } catch (Exception e) {
            log.warn("查询市场深度异常: symbol={}, 错误: {}", symbol, e.getMessage());
        }
        return MarketDepth.builder()
                .symbol(symbol)
                .bids(Collections.emptyList())
                .asks(Collections.emptyList())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public boolean setLeverage(String symbol, int leverage) {
        try {
            UrlParamsBuilder builder = UrlParamsBuilder.build()
                    .putToPost("instId", convertToOkexSymbol(symbol))
                    .putToPost("mgnMode", "cross")
                    .putToPost("lever", leverage);
            JSONObject response = restConnection.executePostWithSignature("/api/v5/account/set-leverage", builder);
            return "0".equals(response.getString("code"));
        } catch (Exception e) {
            log.warn("设置杠杆异常: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean closeAllPositions(String symbol) {
        return false;
    }

    @Override
    public List<Candlestick> getCandlestick(CandlestickRequest request) {
        String okxSymbol = convertToOkexSymbol(request.getSymbol());
        UrlParamsBuilder builder = UrlParamsBuilder.build()
                .putToUrl("instId", okxSymbol)
                .putToUrl("bar", request.getInterval().getCode())
                .putToUrl("limit", request.getSize());
        JSONObject json = restConnection.executeGet("/api/v5/market/candles", builder);
        JSONArray data = json.getJSONArray("data");
        return parseCandlestickArray(data, request.getSymbol(), request.getInterval());
    }

    @Override
    public List<Candlestick> getHistoryCandlestick(CandlestickRequest request) {
        String okxSymbol = convertToOkexSymbol(request.getSymbol());
        UrlParamsBuilder paramBuilder = UrlParamsBuilder.build()
                .putToUrl("instId", okxSymbol)
                .putToUrl("bar", request.getInterval().getCode());
        if (request.getFrom() != 0 && request.getTo() != 0) {
            paramBuilder.putToUrl("after", request.getTo() * 1000);
        }
        paramBuilder.putToUrl("limit", request.getSize());
        JSONObject json = restConnection.executeGet("/api/v5/market/history-candles", paramBuilder);
        JSONArray data = json.getJSONArray("data");
        return parseCandlestickArray(data, request.getSymbol(), request.getInterval());
    }



    @Override
    public boolean amendTpSl(String orderSn, String symbol, BigDecimal gainPrice, BigDecimal lossPrice) {
        log.info("OKX直连修改止盈止损: orderSn={}, symbol={}, gainPrice={}, lossPrice={}", orderSn, symbol, gainPrice, lossPrice);
        try {
            String okxSymbol = convertToOkexSymbol(symbol);
            // 1. 查询待触发条件单，获取 algoId
            UrlParamsBuilder queryBuilder = UrlParamsBuilder.build()
                    .putToUrl("algoClOrdId", orderSn)
                    .putToUrl("instId", okxSymbol)
                    .putToUrl("state", "live");
            JSONObject queryResponse = restConnection.executeGetWithSignature("/api/v5/trade/orders-algo-pending", queryBuilder);
            String queryCode = queryResponse.getString("code");
            if (!"0".equals(queryCode)) {
                log.warn("查询条件单失败: orderSn={}, code={}, msg={}", orderSn, queryCode, queryResponse.getString("msg"));
                return false;
            }
            JSONArray data = queryResponse.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                log.warn("未找到待触发的条件单: orderSn={}", orderSn);
                return false;
            }
            String algoId = data.getJSONObject(0).getString("algoId");
            if (StringUtils.isBlank(algoId)) {
                log.warn("条件单algoId为空: orderSn={}", orderSn);
                return false;
            }
            // 2. 构建修改参数
            UrlParamsBuilder amendBuilder = UrlParamsBuilder.build()
                    .putToPost("instId", okxSymbol)
                    .putToPost("algoId", algoId);
            if (gainPrice != null) {
                amendBuilder.putToPost("newTpOrdPx", gainPrice.toPlainString());
            }
            if (lossPrice != null) {
                amendBuilder.putToPost("newSlOrdPx", lossPrice.toPlainString());
            }
            // 3. 调用修改接口
            JSONObject amendResponse = restConnection.executePostWithSignature("/api/v5/trade/amend-algos", amendBuilder);
            String amendCode = amendResponse.getString("code");
            if ("0".equals(amendCode)) {
                log.info("修改止盈止损成功: orderSn={}, algoId={}, gainPrice={}, lossPrice={}", orderSn, algoId, gainPrice, lossPrice);
                return true;
            } else {
                log.warn("修改止盈止损失败: orderSn={}, algoId={}, code={}, msg={}", orderSn, algoId, amendCode, amendResponse.getString("msg"));
                return false;
            }
        } catch (Exception e) {
            log.error("修改止盈止损异常: orderSn={}, 错误: {}", orderSn, e.getMessage(), e);
            return false;
        }
    }

    private List<Candlestick> parseCandlestickArray(JSONArray jsonArray, String symbol, CandlestickIntervalEnum interval) {
        List<Candlestick> list = new ArrayList<>(jsonArray == null ? 0 : jsonArray.size());
        if (jsonArray == null) {
            return list;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONArray arr = jsonArray.getJSONArray(i);
            list.add(parseCandlestick(arr, symbol, interval));
        }
        return list;
    }

    private Candlestick parseCandlestick(JSONArray json, String symbol, CandlestickIntervalEnum interval) {
        int pricePrecision = 2;
        Candlestick c = new Candlestick();
        c.setId(json.getLong(0));
        c.setOpenPrice(json.getBigDecimal(1).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        c.setHighPrice(json.getBigDecimal(2).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        c.setLowPrice(json.getBigDecimal(3).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        c.setClosePrice(json.getBigDecimal(4).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        c.setVolume(json.getBigDecimal(7).setScale(pricePrecision, BigDecimal.ROUND_DOWN));
        c.setConfirm(json.getString(8));
        c.setTimeStr(com.chain.ai.trade.common.utils.DateUtil.longConvertDateTime(c.getId()));
        c.setCandlestickIntervalEnum(interval);
        c.setSymbol(symbol);
        c.setMarketType(MarketType.CRYPTO);
        c.setExchange(Exchange.OKX);
        return c;
    }
}
