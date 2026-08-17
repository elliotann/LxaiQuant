package com.chain.ai.trade.engine.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import com.chain.ai.trade.engine.data.entity.dos.UserFavorite;
import com.chain.ai.trade.engine.data.service.ISymbolsService;
import com.chain.ai.trade.engine.data.service.IUserFavoriteService;
import com.chain.ai.trade.engine.mapper.UserFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户自选股服务实现
 */
@Service
@RequiredArgsConstructor
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite> implements IUserFavoriteService {

    private final ISymbolsService symbolsService;

    @Override
    public List<UserFavorite> getByUserId(String userId) {
        List<UserFavorite> list = list(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByDesc(UserFavorite::getCreatedAt));

        // 填充标的详情
        List<Integer> symbolIds = list.stream().map(UserFavorite::getSymbolId).collect(Collectors.toList());
        if (!symbolIds.isEmpty()) {
            Map<Integer, Symbol> symbolMap = symbolsService.listByIds(symbolIds)
                    .stream().collect(Collectors.toMap(Symbol::getId, s -> s));
            list.forEach(f -> f.setSymbol(symbolMap.get(f.getSymbolId())));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addFavorite(String userId, Integer symbolId) {
        if (isFavorited(userId, symbolId)) {
            return true;
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setSymbolId(symbolId);
        return save(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeFavorite(String userId, Integer symbolId) {
        return remove(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getSymbolId, symbolId));
    }

    @Override
    public boolean isFavorited(String userId, Integer symbolId) {
        return count(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getSymbolId, symbolId)) > 0;
    }
}
