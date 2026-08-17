package com.chain.ai.trade.engine.data.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ExchangeKlineFetcher 工厂
 * 根据交易所标识分发到对应的 fetcher 实现
 */
@Slf4j
@Component
public class ExchangeKlineFetcherFactory {

    private final List<ExchangeKlineFetcher> fetchers;

    public ExchangeKlineFetcherFactory(List<ExchangeKlineFetcher> fetchers) {
        this.fetchers = fetchers;
    }

    /**
     * 获取指定交易所对应的 fetcher
     *
     * @param exchange 交易所标识，如 "OKX"、"GATEIO"
     * @return 匹配的 fetcher，未找到时返回 empty
     */
    public Optional<ExchangeKlineFetcher> getFetcher(String exchange) {
        return fetchers.stream()
                .filter(f -> f.supports(exchange))
                .findFirst();
    }
}
