package com.chain.ai.trade.engine.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import com.chain.ai.trade.engine.data.service.ISymbolsService;
import com.chain.ai.trade.engine.mapper.SymbolsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一标的服务实现
 */
@Service
public class SymbolsServiceImpl extends ServiceImpl<SymbolsMapper, Symbol> implements ISymbolsService {

    @Override
    public List<Symbol> getHotSymbols(String market) {
        LambdaQueryWrapper<Symbol> wrapper = new LambdaQueryWrapper<Symbol>()
                .eq(Symbol::getIsHot, true)
                .eq(Symbol::getActive, true)
                .orderByDesc(Symbol::getSortOrder);
        if (market != null && !market.isEmpty()) {
            wrapper.eq(Symbol::getMarket, market);
        }
        return list(wrapper);
    }

    @Override
    public List<Symbol> searchSymbols(String market, String keyword) {
        LambdaQueryWrapper<Symbol> wrapper = new LambdaQueryWrapper<Symbol>()
                .eq(Symbol::getActive, true)
                .orderByDesc(Symbol::getSortOrder);
        if (market != null && !market.isEmpty()) {
            wrapper.eq(Symbol::getMarket, market);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Symbol::getSymbol, keyword)
                    .or().like(Symbol::getName, keyword));
        }
        return list(wrapper);
    }

    @Override
    public List<Symbol> listBySymbols(List<String> symbols) {
        LambdaQueryWrapper<Symbol> wrapper = new LambdaQueryWrapper<Symbol>()
                .in(Symbol::getSymbol, symbols)
                .eq(Symbol::getActive, true);
        return list(wrapper);
    }
}
