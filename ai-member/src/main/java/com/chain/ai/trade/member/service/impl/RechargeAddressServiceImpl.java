package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.RechargeAddress;
import com.chain.ai.trade.member.mapper.RechargeAddressMapper;
import com.chain.ai.trade.member.service.IRechargeAddressService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.tron.trident.core.key.KeyPair;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RechargeAddressServiceImpl implements IRechargeAddressService {

    @Autowired
    private RechargeAddressMapper mapper;

    @Autowired
    private Environment environment;

    @Override
    public RechargeAddress getOrCreateAddress(String userId, String businessType) {
        RechargeAddress existing = mapper.selectOne(
                new LambdaQueryWrapper<RechargeAddress>()
                        .eq(RechargeAddress::getUserId, userId)
                        .eq(RechargeAddress::getBusinessType, businessType)
        );
        if (existing != null) {
            return existing;
        }

        KeyPair keyPair = KeyPair.generate();
        String address = keyPair.toBase58CheckAddress();
        String privateKey = keyPair.toPrivateKey();

        AesGcmEncryptor encryptor = new AesGcmEncryptor(resolveEncryptKey());
        String privateKeyEnc = encryptor.encrypt(privateKey);

        RechargeAddress entity = RechargeAddress.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .rechargeAddress(address)
                .privateKeyEnc(privateKeyEnc)
                .businessType(businessType)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        mapper.insert(entity);
        return entity;
    }

    private String resolveEncryptKey() {
        String key = environment.getProperty("wallet.encrypt.key");
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }
        String env = System.getenv("WALLET_ENCRYPT_KEY");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        throw new IllegalStateException("WALLET_ENCRYPT_KEY 未配置");
    }
}
