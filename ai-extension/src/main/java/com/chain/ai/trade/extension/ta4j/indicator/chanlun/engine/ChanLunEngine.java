package com.chain.ai.trade.extension.ta4j.indicator.chanlun.engine;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm.*;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.*;
import java.util.List;

/**
 * 单周期缠论引擎
 * 管理一个完整周期的缠论计算生命周期
 */
public class ChanLunEngine {

    private final Period period;
    private final ChanLunConfig config;
    private List<StdKLine> rawKlines;
    private ChanLunResult lastResult;
    private final IncrementalComputer incrementalComputer;

    public ChanLunEngine(Period period, ChanLunConfig config) {
        this.period = period;
        this.config = config;
        this.incrementalComputer = new IncrementalComputer();
    }

    /**
     * 全量计算（从头到尾处理所有K线）
     */
    public ChanLunResult compute(List<StdKLine> klines) {
        this.rawKlines = klines;

        // 1. 包含关系处理
        List<StdKLine> processedKlines = ContainmentProcessor.process(klines);

        // 2. 分型识别
        List<FenXing> fenXings = FenXingRecognizer.recognize(processedKlines, config);

        // 3. 笔识别
        List<Bi> bis = BiRecognizer.recognize(fenXings, config);

        // 4. 线段识别
        List<Duan> duans = DuanRecognizer.recognize(bis, config);

        // 5. 中枢识别
        List<ZhongShu> zhongShus = ZhongShuRecognizer.recognize(bis, config);

        // 6. 背驰检测 & 买卖点生成
        List<Signal> signals = SignalGenerator.generate(bis, zhongShus, processedKlines, config);

        // 组装结果
        ChanLunResult result = new ChanLunResult();
        result.setPeriod(period.name());
        result.setKlines(klines);
        result.setFenXings(fenXings);
        result.setBis(bis);
        result.setDuans(duans);
        result.setZhongShus(zhongShus);
        result.setSignals(signals);

        lastResult = result;
        return result;
    }

    /**
     * 增量计算
     */
    public ChanLunResult incrementalCompute(List<StdKLine> newKlines) {
        if (newKlines == null || newKlines.isEmpty()) return lastResult;

        if (rawKlines == null) {
            return compute(newKlines);
        }

        // 追加新数据
        rawKlines.addAll(newKlines);
        return incrementalComputer.recompute(rawKlines, this);
    }

    public Period getPeriod() {
        return period;
    }

    public ChanLunResult getLastResult() {
        return lastResult;
    }

    public void reset() {
        rawKlines = null;
        lastResult = null;
        incrementalComputer.reset();
    }
}
