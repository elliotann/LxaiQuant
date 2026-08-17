package com.chain.ai.trade.engine.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;

import java.util.List;

/**
 * 统一标的服务接口
 */
public interface ISymbolsService extends IService<Symbol> {

    /**
     * 按市场获取热门标的
     * @param market 市场（Crypto/USStock/CNStock/HKStock/Forex），为空则查询所有市场
     * @return 热门标的列表
     */
    List<Symbol> getHotSymbols(String market);

    /**
     * 按市场获取标的列表
     * @param market 市场，为空则查询所有
     * @param keyword 关键字模糊搜索（symbol 或 name）
     * @return 标的列表
     */
    List<Symbol> searchSymbols(String market, String keyword);

    /**
     * 按 symbol 代码批量查询
     * @param symbols symbol 代码列表
     * @return 匹配的标的列表
     */
    List<Symbol> listBySymbols(List<String> symbols);
}
