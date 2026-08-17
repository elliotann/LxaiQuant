package com.chain.ai.trade.engine.strategy.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.strategy.entity.dos.BotParameter;
import com.chain.ai.trade.engine.strategy.mapper.BotParameterMapper;
import com.chain.ai.trade.engine.strategy.service.IBotParameterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 机器人参数服务实现
 */
@Slf4j
@Service
public class BotParameterServiceImpl extends ServiceImpl<BotParameterMapper, BotParameter> implements IBotParameterService {

    @Override
    public List<BotParameter> listByBotIdAndGroup(String botId, String groupName) {
        return baseMapper.selectByBotIdAndGroup(botId, groupName);
    }

    @Override
    public String getParameterValue(String botId, String groupName, String name) {
        BotParameter param = baseMapper.selectByBotIdGroupAndName(botId, groupName, name);
        return param != null ? param.getValue() : null;
    }

    @Override
    @Transactional
    public void saveParameters(String botId, String groupName, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        // 先删除该分组下所有参数，再批量新增（全量替换）
        baseMapper.deleteByBotIdAndGroup(botId, groupName);

        List<BotParameter> list = params.entrySet().stream()
                .map(entry -> {
                    BotParameter p = new BotParameter();
                    p.setBotId(botId);
                    p.setGroupName(groupName);
                    p.setName(entry.getKey());
                    p.setValue(entry.getValue());
                    return p;
                })
                .collect(Collectors.toList());

        saveBatch(list);
        log.info("已保存机器人 {} 分组 {} 的参数，共 {} 项", botId, groupName, list.size());
    }

    @Override
    @Transactional
    public void deleteByBotIdAndGroup(String botId, String groupName) {
        baseMapper.deleteByBotIdAndGroup(botId, groupName);
    }

    @Override
    @Transactional
    public void deleteByBotId(String botId) {
        baseMapper.deleteByBotId(botId);
    }
}
