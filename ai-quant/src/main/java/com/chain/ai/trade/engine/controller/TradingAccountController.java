package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.engine.controller.dto.TradingAccountDTO;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.mapper.TradingAccountMapper;
import com.chain.ai.trade.engine.xchange.factory.ExchangeWrapFactory;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.member.service.AccountSecretsService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 交易账户管理控制器
 * 提供交易账户的查询和管理功能
 */
@RestController
@RequestMapping("/api/trading/accounts")
@RequiredArgsConstructor
@Slf4j
public class TradingAccountController {

    private final ITradingAccountService tradingAccountService;
    private final TradingAccountMapper tradingAccountMapper;
    private final AccountSecretsService accountSecretsService;
    private final ICandlestickService candlestickService;

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "OK");
        result.put("service", "TradingAccountController");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取交易账户（可按交易所过滤）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAccounts(
            @RequestParam(required = false) String platform) {
        try {
            log.info("获取交易账户 - platform={}", platform);

            if (tradingAccountService == null) {
                log.error("TradingAccountService 为空！");
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "服务未初始化");
                return ResponseEntity.internalServerError().body(result);
            }

            List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
            log.info("查询到 {} 个账户", accounts != null ? accounts.size() : 0);

            // 如果指定了交易所，按平台过滤
            if (platform != null && !platform.trim().isEmpty()) {
                accounts = accounts.stream()
                        .filter(a -> a.getMemberPlatform() != null
                                && a.getMemberPlatform().name().equalsIgnoreCase(platform.trim()))
                        .collect(Collectors.toList());
                log.info("按平台 {} 过滤后剩余 {} 个账户", platform, accounts.size());
            }

            // 转换为DTO
            List<TradingAccountDTO> accountDTOs = accounts.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", accountDTOs);
            result.put("total", accountDTOs.size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取交易账户失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取交易账户失败: " + e.getMessage());
            result.put("data", List.of());
            result.put("total", 0);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 根据ID获取交易账户详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAccountById(@PathVariable String id) {
        try {
            log.info("获取交易账户详情: id={}", id);

            TradingAccount account = tradingAccountService.getById(id);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            TradingAccountDTO accountDTO = convertToDTO(account);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", accountDTO);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取交易账户详情失败: id={}", id, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取账户详情失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 创建交易账户
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody Map<String, Object> accountData) {
        try {
            log.info("创建交易账户");

            // 验证必要参数
            String name = (String) accountData.get("name");
            String exchange = (String) accountData.get("exchange");
            String apiKey = (String) accountData.get("apiKey");
            String apiSecret = (String) accountData.get("apiSecret");
            String passphrase = (String) accountData.get("passphrase");
            Boolean simulated = (Boolean) accountData.get("simulated");

            if (name == null || name.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户名称不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (exchange == null || exchange.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "交易所不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (apiKey == null || apiKey.trim().isEmpty() || apiSecret == null || apiSecret.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "API密钥和密钥不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            // 获取当前登录用户ID
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "未登录或Token无效");
                return ResponseEntity.status(401).body(result);
            }
            String memberId = authentication.getPrincipal().toString();

            // 处理账户类型
            String accountTypeStr = (String) accountData.get("type");
            TradingAccount.AccountType accountType = null;
            if (accountTypeStr != null && !accountTypeStr.trim().isEmpty()) {
                try {
                    accountType = TradingAccount.AccountType.valueOf(accountTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的账户类型: {}", accountTypeStr);
                }
            }

            // 创建账户实体（同时设置明文兼容字段，确保 INSERT 包含 api_key 等列）
            TradingAccount account = TradingAccount.builder()
                    .memberId(memberId)
                    .accountName(name)
                    .accountType(accountType)
                    .memberPlatform(Enum.valueOf(com.chain.ai.trade.common.entity.constants.Exchange.class, exchange.toUpperCase()))
                    .simulated(simulated != null ? simulated : false)
                    .bindStatus(TradingAccount.BindStatus.BIND)
                    .apiEnabled(true)
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .passphrase(passphrase)
                    .createTime(new java.util.Date())
                    .updateTime(new java.util.Date())
                    .build();

            // 保存账户到数据库
            int result = tradingAccountMapper.insert(account);
            if (result > 0) {
                accountSecretsService.saveEncryptedSecrets(
                        account.getId(),
                        apiKey,
                        apiSecret,
                        passphrase
                );
            }

            if (result > 0) {
                TradingAccountDTO accountDTO = convertToDTO(account);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "账户创建成功");
                response.put("data", accountDTO);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "账户创建失败");
                return ResponseEntity.internalServerError().body(response);
            }

        } catch (Exception e) {
            log.error("创建交易账户失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "创建账户失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 更新账户信息
     */
    @PutMapping("/{accountId}")
    public ResponseEntity<Map<String, Object>> updateAccount(
            @PathVariable String accountId,
            @RequestBody Map<String, Object> updateData) {

        try {
            log.info("更新账户信息: accountId={}", accountId);

            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 更新字段
            if (updateData.containsKey("name") && updateData.get("name") != null) {
                account.setAccountName(updateData.get("name").toString());
            }

            String apiKey = updateData.containsKey("apiKey") && updateData.get("apiKey") != null
                    ? updateData.get("apiKey").toString() : null;
            String apiSecret = updateData.containsKey("apiSecret") && updateData.get("apiSecret") != null
                    ? updateData.get("apiSecret").toString() : null;
            String passphrase = updateData.containsKey("passphrase") && updateData.get("passphrase") != null
                    ? updateData.get("passphrase").toString() : null;
            if (apiKey != null || apiSecret != null || passphrase != null) {
                accountSecretsService.saveEncryptedSecrets(
                        accountId,
                        apiKey,
                        apiSecret,
                        passphrase
                );
            }

            if (updateData.containsKey("simulated")) {
                Object simulatedObj = updateData.get("simulated");
                if (simulatedObj instanceof Boolean) {
                    account.setSimulated((Boolean) simulatedObj);
                } else if (simulatedObj != null) {
                    account.setSimulated(Boolean.parseBoolean(simulatedObj.toString()));
                }
            }

            // 处理账户类型
            if (updateData.containsKey("type") && updateData.get("type") != null) {
                String accountTypeStr = updateData.get("type").toString().toUpperCase();
                try {
                    TradingAccount.AccountType accountType = TradingAccount.AccountType.valueOf(accountTypeStr);
                    account.setAccountType(accountType);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的账户类型: {}", accountTypeStr);
                }
            }

            // 更新时间
            account.setUpdateTime(new java.util.Date());

            // 保存到数据库
            int result = tradingAccountMapper.updateById(account);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result > 0);
            response.put("message", result > 0 ? "账户更新成功" : "账户更新失败");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新账户信息失败: accountId={}", accountId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "更新账户信息失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 将TradingAccount实体转换为DTO
     */
    private TradingAccountDTO convertToDTO(TradingAccount account) {
        TradingAccountDTO dto = new TradingAccountDTO();

        // 基础字段映射
        dto.setId(account.getId());
        dto.setAccountName(account.getAccountName());
        dto.setMemberId(account.getMemberId());
        dto.setMemberPlatform(account.getMemberPlatform() != null ? account.getMemberPlatform().name() : null);
        dto.setApiKey(null);
        dto.setApiSecret(null);
        dto.setBindStatus(account.getBindStatus() != null ? account.getBindStatus().name().toLowerCase() : null);
        dto.setCreateTime(convertToLocalDateTime(account.getCreateTime()));
        dto.setUpdateTime(convertToLocalDateTime(account.getUpdateTime()));

        // 前端需要的字段映射
        dto.setName(account.getAccountName() != null ? account.getAccountName() : "账户 " + account.getId());
        dto.setPlatform(account.getMemberPlatform() != null ? account.getMemberPlatform().name() : null);
        dto.setPlatformName(account.getMemberPlatform() != null ? account.getMemberPlatform().name() : null);
        dto.setType(account.getAccountType() != null ? account.getAccountType().name().toLowerCase() : null);
        dto.setSimulated(account.getSimulated() != null ? account.getSimulated() : false);

        // 设置绑定状态和活跃状态
        boolean isActive = account.getBindStatus() == TradingAccount.BindStatus.BIND;
        dto.setIsActive(isActive);
        dto.setStatus(isActive ? "active" : "inactive");

        // 时间字段映射
        dto.setCreatedAt(convertToLocalDateTime(account.getCreateTime()));
        dto.setUpdatedAt(convertToLocalDateTime(account.getUpdateTime()));

        // 从账户实体读取余额（usdtBalance 为数据库虚拟列，由 balances JSON 自动计算）
        dto.setBalance(account.getUsdtBalance() != null ? account.getUsdtBalance().doubleValue() : 0.0);
        // PnL 暂不提供
        dto.setPnl(0.0);

        return dto;
    }

    /**
     * 将 Date 转换为 LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 同步账户余额信息
     */
    @PostMapping("/{accountId}/sync-balance")
    public ResponseEntity<Map<String, Object>> syncAccountBalance(@PathVariable String accountId) {
        try {
            log.info("开始同步账户余额: accountId={}", accountId);

            // 1. 获取账户信息
            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 2. 检查账户是否已绑定
            if (account.getBindStatus() != TradingAccount.BindStatus.BIND) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户未绑定，无法同步余额");
                return ResponseEntity.badRequest().body(result);
            }

            // 3. 创建交易所服务
            ExchangeTradeService exchangeService = ExchangeWrapFactory.createExchangeService(account);

            // 4. 获取账户余额
            Map<String, BigDecimal> balances = new HashMap<>();

            // 获取主要货币的余额（可以根据需要扩展）
            try {
                BigDecimal btcBalance = exchangeService.getAccountBalance(null);
                if (btcBalance != null && btcBalance.compareTo(BigDecimal.ZERO) > 0) {
                    balances.put("BTC", btcBalance);
                }
            } catch (Exception e) {
                log.warn("获取BTC余额失败: {}", e.getMessage());
            }

            try {
                BigDecimal usdtBalance = exchangeService.getAccountBalance("USDT");
                if (usdtBalance != null && usdtBalance.compareTo(BigDecimal.ZERO) > 0) {
                    balances.put("USDT", usdtBalance);
                }
            } catch (Exception e) {
                log.warn("获取USDT余额失败: {}", e.getMessage());
            }

            // 5. 更新数据库中的余额信息
            boolean updateResult = tradingAccountService.updateAccountBalances(accountId, balances);
            if (updateResult) {
                log.info("账户 {} 余额同步并更新数据库成功: {}", accountId, balances);
            } else {
                log.warn("账户 {} 余额同步成功但数据库更新失败: {}", accountId, balances);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "余额同步成功");
            result.put("balances", balances);
            result.put("syncTime", java.time.LocalDateTime.now());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("同步账户余额失败: accountId={}", accountId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "同步余额失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试账户连接
     */
    @PostMapping("/{accountId}/test-connection")
    public ResponseEntity<Map<String, Object>> testAccountConnection(@PathVariable String accountId) {
        try {
            log.info("测试账户连接: accountId={}", accountId);

            // 1. 获取账户信息
            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 2. 检查账户是否已绑定
            if (account.getBindStatus() != TradingAccount.BindStatus.BIND) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户未绑定，无法测试连接");
                return ResponseEntity.badRequest().body(result);
            }

            // 3. 创建交易所服务并测试连接
            ExchangeTradeService exchangeService;
            try {
                exchangeService = ExchangeWrapFactory.createExchangeService(account);
            } catch (Exception initException) {
                // 交易所服务初始化失败，直接返回失败
                log.warn("账户 {} 交易所服务初始化失败: {}", accountId, initException.getMessage());

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "连接测试失败: API配置无效");
                result.put("details", Map.of(
                    "error", "交易所服务初始化失败，请检查账户的API配置",
                    "connectionStatus", "FAILED",
                    "exceptionType", initException.getClass().getSimpleName(),
                    "initError", initException.getMessage()
                ));
                result.put("accountId", accountId);
                result.put("exchange", account.getMemberPlatform() != null ? account.getMemberPlatform().name() : "UNKNOWN");

                return ResponseEntity.ok(result);
            }

            boolean connectionTested = false;
            String testResult = "连接测试失败";
            Map<String, Object> testDetails = new HashMap<>();

            try {
                // 尝试获取账户余额来测试连接
                BigDecimal testBalance = exchangeService.getAccountBalance("BTC");
                connectionTested = true;
                testResult = "连接测试成功";
                testDetails.put("btcBalance", testBalance);
                testDetails.put("connectionStatus", "SUCCESS");

                log.info("账户 {} 连接测试成功", accountId);
            } catch (Exception e) {
                connectionTested = true;
                testResult = "连接测试失败: " + e.getMessage();
                testDetails.put("error", e.getMessage());
                testDetails.put("connectionStatus", "FAILED");

                log.warn("账户 {} 连接测试失败: {}", accountId, e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", connectionTested && "连接测试成功".equals(testResult));
            result.put("message", testResult);
            result.put("details", testDetails);
            result.put("accountId", accountId);
            result.put("exchange", account.getMemberPlatform() != null ? account.getMemberPlatform().name() : "UNKNOWN");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试账户连接失败: accountId={}", accountId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "连接测试异常: " + e.getMessage());
            result.put("accountId", accountId);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试API配置连接（用于添加账户时的测试）
     */
    @PostMapping("/test-connection-config")
    public ResponseEntity<Map<String, Object>> testConnectionConfig(@RequestBody Map<String, Object> config) {
        try {
            log.info("测试API配置连接");

            // 从请求中提取配置信息
            String apiKey = (String) config.get("apiKey");
            String apiSecret = (String) config.get("apiSecret");
            String passphrase = (String) config.get("passphrase");
            String exchangeStr = (String) config.get("exchange");

            if (apiKey == null || apiSecret == null || exchangeStr == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "缺少必要的配置参数");
                return ResponseEntity.badRequest().body(result);
            }

            String base64Key = System.getenv("ACCOUNT_SECRET_KEY");
            if (base64Key == null || base64Key.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "服务未配置密钥: ACCOUNT_SECRET_KEY");
                return ResponseEntity.internalServerError().body(result);
            }
            AesGcmEncryptor encryptor = new AesGcmEncryptor(base64Key);
            String apiKeyEnc = encryptor.encrypt(apiKey);
            String apiSecretEnc = encryptor.encrypt(apiSecret);
            String passphraseEnc = passphrase != null ? encryptor.encrypt(passphrase) : null;

            // 创建临时账户对象进行测试
            TradingAccount testAccount = TradingAccount.builder()
                    .id("TEST-CONNECTION-" + System.currentTimeMillis()) // 设置测试ID
                    .apiKeyEnc(apiKeyEnc)
                    .apiSecretEnc(apiSecretEnc)
                    .passphraseEnc(passphraseEnc)
                    .memberPlatform(Enum.valueOf(com.chain.ai.trade.common.entity.constants.Exchange.class, exchangeStr.toUpperCase()))
                    .bindStatus(TradingAccount.BindStatus.BIND)
                    .build();

            // 创建交易所服务并测试连接
            ExchangeTradeService exchangeService;
            try {
                exchangeService = ExchangeWrapFactory.createExchangeService(testAccount);
            } catch (Exception initException) {
                // 交易所服务初始化失败，直接返回失败
                log.warn("交易所服务初始化失败: exchange={}, error={}", exchangeStr, initException.getMessage());

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "连接测试失败: API密钥无效或格式错误");
                result.put("details", Map.of(
                    "error", "交易所服务初始化失败，请检查API密钥、密钥和密码是否正确",
                    "connectionStatus", "FAILED",
                    "exceptionType", initException.getClass().getSimpleName(),
                    "initError", initException.getMessage()
                ));
                result.put("exchange", exchangeStr);

                return ResponseEntity.ok(result);
            }

            boolean connectionTested = false;
            String testResult = "连接测试失败";
            Map<String, Object> testDetails = new HashMap<>();

            try {
                // 尝试获取账户余额来测试连接
                BigDecimal testBalance = exchangeService.getAccountBalance("BTC");
                connectionTested = true;
                testResult = "连接测试成功";
                testDetails.put("btcBalance", testBalance != null ? testBalance.toString() : "0");
                testDetails.put("connectionStatus", "SUCCESS");
                testDetails.put("message", "API密钥验证成功，可以正常连接到交易所");

                log.info("API配置连接测试成功: exchange={}, btcBalance={}", exchangeStr, testBalance);
            } catch (Exception e) {
                connectionTested = true;
                String errorMsg = e.getMessage();

                // 提供更友好的错误信息
                if (errorMsg.contains("50101") || errorMsg.contains("APIKey does not match current environment")) {
                    testResult = "连接测试失败: API密钥环境不匹配";
                    testDetails.put("error", "API密钥可能是在测试环境生成的，但应用运行在生产环境（或相反）。请检查OKX账户设置中的环境配置。");
                    testDetails.put("solution", "1. 登录OKX官网账户设置 2. 检查API密钥的环境设置 3. 确保与当前应用环境一致");
                } else if (errorMsg.contains("ClassNotFoundException") || errorMsg.contains("OKX")) {
                    testResult = "连接测试失败: OKX服务不可用，请检查系统配置";
                    testDetails.put("error", "OKX交易所服务初始化失败，可能缺少相关依赖或配置");
                } else if (errorMsg.contains("API") || errorMsg.contains("key")) {
                    testResult = "连接测试失败: API密钥无效或格式错误";
                    testDetails.put("error", "请检查API密钥、密钥和密码是否正确");
                } else if (errorMsg.contains("network") || errorMsg.contains("timeout") || errorMsg.contains("Connection timed out")) {
                    testResult = "连接测试失败: 网络连接超时";
                    testDetails.put("error", "请检查网络连接或代理设置");
                } else {
                    testResult = "连接测试失败: " + errorMsg;
                    testDetails.put("error", errorMsg);
                }

                testDetails.put("connectionStatus", "FAILED");
                testDetails.put("exceptionType", e.getClass().getSimpleName());

                log.warn("API配置连接测试失败: exchange={}, error={}", exchangeStr, e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", connectionTested && "连接测试成功".equals(testResult));
            result.put("message", testResult);
            result.put("details", testDetails);
            result.put("exchange", exchangeStr);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试API配置连接异常", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "连接测试异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 删除交易账户
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable String accountId) {
        try {
            log.info("删除交易账户: accountId={}", accountId);

            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 清除密钥缓存
            accountSecretsService.clearCache(accountId);

            int result = tradingAccountMapper.deleteById(accountId);
            if (result > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "账户删除成功");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "账户删除失败");
                return ResponseEntity.internalServerError().body(response);
            }
        } catch (Exception e) {
            log.error("删除交易账户失败: accountId={}", accountId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "删除账户失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 批量同步所有账户余额
     */
    @PostMapping("/sync-all-balances")
    public ResponseEntity<Map<String, Object>> syncAllAccountBalances() {
        try {
            log.info("开始批量同步所有账户余额");

            List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
            Map<String, Object> results = new HashMap<>();
            int successCount = 0;
            int failCount = 0;

            for (TradingAccount account : accounts) {
                try {
                    // 复用单个账户同步逻辑
                    ResponseEntity<Map<String, Object>> response = syncAccountBalance(account.getId());
                    if (response.getBody() != null && (Boolean) response.getBody().get("success")) {
                        successCount++;
                        results.put(account.getId(), Map.of("success", true, "balances", response.getBody().get("balances")));
                    } else {
                        failCount++;
                        results.put(account.getId(), Map.of("success", false, "message", response.getBody().get("message")));
                    }
                } catch (Exception e) {
                    failCount++;
                    results.put(account.getId(), Map.of("success", false, "message", e.getMessage()));
                    log.error("同步账户 {} 余额失败", account.getId(), e);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.format("批量同步完成，成功: %d, 失败: %d", successCount, failCount));
            result.put("total", accounts.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("results", results);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量同步账户余额失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "批量同步失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/{accountId}/ticker/{symbol}")
    public ResponseEntity<Map<String, Object>> getTicker(
            @PathVariable String accountId,
            @PathVariable String symbol) {
        try {
            String sym = normalizeSymbol(symbol);
            if (!isValidSymbol(sym)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "symbol 格式不正确: " + symbol);
                return ResponseEntity.badRequest().body(result);
            }

            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            Exchange ex = ExchangeWrapFactory.createNoAuthExchangeService(account);
            Instrument instrument = toInstrument(sym);
            Ticker t;
            try {
                t = ex.getMarketDataService().getTicker(instrument);
            } catch (IndexOutOfBoundsException ioobe) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "交易所返回空Ticker数据，请检查标的: " + sym);
                return ResponseEntity.status(502).body(result);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("symbol", sym);
            data.put("last", t != null && t.getLast() != null ? t.getLast() : 0);
            data.put("bid", t != null && t.getBid() != null ? t.getBid() : 0);
            data.put("ask", t != null && t.getAsk() != null ? t.getAsk() : 0);
            data.put("volume", t != null && t.getVolume() != null ? t.getVolume() : 0);
            data.put("changePercent", 0);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取Ticker失败: accountId={}, symbol={}", accountId, symbol, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取Ticker失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/{accountId}/latest-price/{symbol}")
    public ResponseEntity<Map<String, Object>> getLatestPrice(
            @PathVariable String accountId,
            @PathVariable String symbol,
            @RequestParam(value = "interval", required = false) String interval) {
        try {
            String sym = normalizeSymbol(symbol);
            if (!isValidSymbol(sym)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "symbol 格式不正确: " + symbol);
                return ResponseEntity.badRequest().body(result);
            }

            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            CandlestickIntervalEnum intervalEnum = CandlestickIntervalEnum.OKXMIN15;
            if (interval != null && !interval.isBlank()) {
                try {
                    intervalEnum = CandlestickIntervalEnum.valueOf(interval.trim());
                } catch (Exception ignored) {
                }
            }

            KlineParam p = KlineParam.builder()
                    .symbol(sym)
                    .klineInterval(intervalEnum)
                    .size(1)
                    .build();
            List<Candlestick> list = candlestickService.getLastKlines(p);
            if (list == null || list.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "无本地K线数据，请先导入或开启行情采集: " + sym);
                return ResponseEntity.status(404).body(result);
            }
            Candlestick latest = list.get(list.size() - 1);

            Map<String, Object> data = new HashMap<>();
            data.put("symbol", sym);
            data.put("interval", intervalEnum.name());
            data.put("time", latest.getTimeStr());
            data.put("price", latest.getClosePrice());
            data.put("source", "db_kline");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取最新价格失败: accountId={}, symbol={}", accountId, symbol, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取最新价格失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/{accountId}/orderbook/{symbol}")
    public ResponseEntity<Map<String, Object>> getOrderBook(
            @PathVariable String accountId,
            @PathVariable String symbol,
            @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            String sym = normalizeSymbol(symbol);
            if (!isValidSymbol(sym)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "symbol 格式不正确: " + symbol);
                return ResponseEntity.badRequest().body(result);
            }

            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            Exchange ex = ExchangeWrapFactory.createNoAuthExchangeService(account);
            Instrument instrument = toInstrument(sym);
            OrderBook ob = (limit != null && limit > 0)
                    ? ex.getMarketDataService().getOrderBook(instrument, limit)
                    : ex.getMarketDataService().getOrderBook(instrument);

            List<List<Object>> bids = new ArrayList<>();
            List<List<Object>> asks = new ArrayList<>();
            if (ob != null) {
                if (ob.getBids() != null) {
                    for (var o : ob.getBids()) {
                        List<Object> row = new ArrayList<>();
                        row.add(o.getLimitPrice());
                        row.add(o.getOriginalAmount());
                        bids.add(row);
                    }
                }
                if (ob.getAsks() != null) {
                    for (var o : ob.getAsks()) {
                        List<Object> row = new ArrayList<>();
                        row.add(o.getLimitPrice());
                        row.add(o.getOriginalAmount());
                        asks.add(row);
                    }
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("symbol", sym);
            data.put("bids", bids);
            data.put("asks", asks);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (java.io.IOException e) {
            log.error("获取OrderBook失败(上游IO异常): accountId={}, symbol={}", accountId, symbol, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取OrderBook失败: 上游交易所连接异常");
            return ResponseEntity.status(502).body(result);
        } catch (Exception e) {
            log.error("获取OrderBook失败: accountId={}, symbol={}", accountId, symbol, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取OrderBook失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/{accountId}/trades")
    public ResponseEntity<Map<String, Object>> getTrades(
            @PathVariable String accountId,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            TradingAccount account = tradingAccountService.getById(accountId);
            if (account == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "账户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            String raw = symbol != null ? symbol : "";
            if (raw.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "symbol 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            String sym = normalizeSymbol(raw);
            if (!isValidSymbol(sym)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "symbol 格式不正确: " + raw);
                return ResponseEntity.badRequest().body(result);
            }

            Exchange ex = ExchangeWrapFactory.createNoAuthExchangeService(account);
            Instrument instrument = toInstrument(sym);
            Trades trades = (limit != null && limit > 0)
                    ? ex.getMarketDataService().getTrades(instrument, limit)
                    : ex.getMarketDataService().getTrades(instrument);

            List<Map<String, Object>> list = new ArrayList<>();
            if (trades != null && trades.getTrades() != null) {
                for (Trade t : trades.getTrades()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("price", t.getPrice());
                    row.put("amount", t.getOriginalAmount());
                    row.put("side", t.getType() != null ? t.getType().toString() : "");
                    row.put("timestamp", t.getTimestamp() != null ? t.getTimestamp().getTime() : null);
                    list.add(row);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", list);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取Trades失败: accountId={}, symbol={}", accountId, symbol, e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取Trades失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Instrument toInstrument(String symbol) {
        String s = normalizeSymbol(symbol);
        if (s.endsWith("-SWAP")) {
            String[] parts = s.split("-");
            if (parts.length >= 2) {
                CurrencyPair cp = new CurrencyPair(parts[0], parts[1]);
                return new FuturesContract(cp, "SWAP");
            }
        }
        String[] parts = s.split("-");
        if (parts.length >= 2) {
            return new CurrencyPair(parts[0], parts[1]);
        }
        return new CurrencyPair(s, "USDT");
    }

    private boolean isValidSymbol(String sym) {
        if (sym == null) return false;
        return sym.matches("^[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?$");
    }

    private String normalizeSymbol(String symbol) {
        String s = String.valueOf(symbol == null ? "" : symbol).trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("^实时建议\\s*[:：]\\s*", "");
        s = s.replaceAll("^标的\\s*[:：]\\s*", "");
        s = s.replaceAll("^symbol\\s*[:：]\\s*", "");
        s = s.trim().replaceAll("\\s+", "").replace("/", "-").toUpperCase();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?")
                .matcher(s);
        if (m.find()) return m.group();
        if (s.matches("^[A-Z0-9]{2,12}$")) return s + "-USDT-SWAP";
        return s;
    }
}
