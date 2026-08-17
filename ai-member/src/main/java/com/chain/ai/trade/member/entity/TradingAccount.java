package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_member_third_account")
public class TradingAccount extends BaseEntity {

    public enum BindStatus{
        BIND,
        UNBIND
    }
    
    public enum AccountType {
        SPOT("现货"),
        FUTURES("合约"),
        MARGIN("保证金");
        
        private final String description;
        
        AccountType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private static final long serialVersionUID = 1L;

    
    private String memberId;

    private Exchange memberPlatform;

    private String accountName;

    private String uid;

    
    private String apiKeyEnc;

    private String apiSecretEnc;

    private String passphraseEnc;

    private boolean apiEnabled;

    /**
     * 明文API密钥（旧版字段，用于插入时兼容）
     */
    private String apiKey;

    private String apiSecret;

    private String passphrase;

    /**
     * 交易所余额（JSON格式）
     */
    private String balances;

    // 虚拟列映射（注意：该字段由数据库自动计算，Java 中仅用于读取）
    private BigDecimal usdtBalance;  // 对应 usdt_balance 虚拟列
    private BigDecimal btcBalance;   // 可选：对应 btc_balance 虚拟列

    /**
     * 已分配额度（JSON格式）
     */
    private String allocations;

    /**
     * 是否模拟账户（沙箱环境）
     * true: 模拟账户，使用沙箱环境进行交易操作
     * false: 真实账户，使用真实环境进行交易操作
     */
    private Boolean simulated;

    private AccountType accountType;

    private BindStatus bindStatus;

    /**
     * 合作商 id，仅合作商推送 api 有
     */
    private String partnerId;
    private LocalDateTime lastSyncTime;
}
