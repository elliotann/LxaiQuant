package com.chain.ai.trade.engine.task.jobhandler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.engine.xchange.factory.ExchangeWrapFactory;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.engine.xchange.okx.OkxExchangeService;
import com.chain.ai.trade.order.service.impl.GateioDirectTradeService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 合约信息同步任务
 * 获取交易所合约规格（面值、乘数），目前支持：OKX（通过 xchange）、GATEIO（通过直连API）
 * 合约张数=价值*杠杆/(面值*开仓均价)
 */
@Slf4j
@Component
public class ContractInfoGetTaskExecute {

    private static final String CONTRACT_INFO_KEY_PREFIX = "contract:info:";

    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private ITradingAccountService tradingAccountService;

    @XxlJob("contractInfoGetTaskExecute")
    public void execute() {
        log.info("同步合约基础信息（使用 xchange 方式）.....");
        String param = XxlJobHelper.getJobParam();
        JSONObject params = null;
        if (StringUtils.isNotEmpty(param)) {
            log.info("获取合约信息参数: {}", param);
            params = JSONUtil.parseObj(param);
        }
        
        if (params != null && StringUtils.isNotEmpty(params.getStr("symbols"))) {
            String accountId = params.getStr("accountId");
            TradingAccount account = null;

            // 获取账户信息（用于创建 ExchangeService）
            if (StringUtils.isNotEmpty(accountId)) {
                account = tradingAccountService.getByAccountId(accountId);
                if (account == null) {
                    log.warn("指定的账户不存在: accountId={}", accountId);
                }
            }

            // 如果没有指定账户，尝试使用默认OKX账户（向后兼容）
            if (account == null) {
                account = tradingAccountService.getByAccountId("TA000000000000001");
            }

            if (account == null) {
                log.error("无法找到有效账户，无法获取合约信息");
                return;
            }

            Exchange exchange = account.getMemberPlatform();
            log.info("使用账户获取合约信息: accountId={}, exchange={}", account.getId(), exchange);

            for (String symbol : params.getStr("symbols").split(",")) {
                symbol = symbol.trim();
                if (StringUtils.isEmpty(symbol)) {
                    continue;
                }

                // 确保 symbol 格式正确（添加 -SWAP 后缀）
                String swapSymbol = symbol.contains("-SWAP") ? symbol : symbol + "-SWAP";
                String redisKey = CONTRACT_INFO_KEY_PREFIX + exchange.name() + ":" + swapSymbol;

                // 检查缓存
                ContractSpec cachedSpec = (ContractSpec) redisCache.get(redisKey);
                if (cachedSpec != null) {
                    log.debug("从缓存获取合约信息: symbol={}, contractSize={}, contractMult={}",
                            swapSymbol, cachedSpec.getContractSize(), cachedSpec.getContractMult());
                    continue;
                }

                try {
                    ContractSpec contractSpec = null;

                    if (exchange == Exchange.OKX) {
                        // 使用 xchange 方式获取 OKX 合约信息
                        ExchangeTradeService exchangeService = ExchangeWrapFactory.createExchangeService(account);
                        if (!(exchangeService instanceof OkxExchangeService)) {
                            log.error("账户不是 OKX 类型，无法获取合约信息: accountId={}", account.getId());
                            continue;
                        }
                        contractSpec = ((OkxExchangeService) exchangeService).getContractSpec(swapSymbol);

                    } else if (exchange == Exchange.GATEIO) {
                        // 使用直连 API 获取 GATEIO 合约信息
                        GateioDirectTradeService gateioService = new GateioDirectTradeService(account);
                        contractSpec = gateioService.getContractSpec(swapSymbol);

                    } else {
                        log.error("不支持的交易所类型: {}, accountId={}", exchange, account.getId());
                        continue;
                    }

                    // 存入缓存，保留一个月（30天）
                    long expireSeconds = 60L * 60 * 24 * 30;
                    redisCache.put(redisKey, contractSpec, expireSeconds);

                    log.info("成功获取并缓存合约信息: exchange={}, symbol={}, contractSize={}, contractMult={}",
                            exchange, swapSymbol, contractSpec.getContractSize(), contractSpec.getContractMult());

                } catch (Exception e) {
                    log.error("获取合约信息失败: exchange={}, symbol={}, error={}", exchange, swapSymbol, e.getMessage(), e);
                }
            }
        }
        log.info("同步合约基础信息完成.....");
    }
}
