package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import java.util.List;

/**
 * 缠论计算结果
 */
@Data
public class ChanLunResult {
    private String period;
    private List<StdKLine> klines;
    private List<FenXing> fenXings;
    private List<Bi> bis;
    private List<Duan> duans;
    private List<ZhongShu> zhongShus;
    private List<Signal> signals;
}
