package com.chain.ai.trade.extension.ta4j.indicator.chanlun.algorithm;

import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.Bi;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.FenXing;
import com.chain.ai.trade.extension.ta4j.indicator.chanlun.model.ChanLunConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔识别器（单指针 + 延伸算法）
 *
 * 核心逻辑：
 * - last：最后一个分型指针
 * - 同类型更强时替换 last（延伸）
 * - 反向分型满足距离条件则成笔
 * - 反向分型距离不足时，last 延伸到当前分型（代替跳过），让距离继续积累
 * - 不校验价格方向，方向由分型类型自然决定
 */
public class BiRecognizer {

    private BiRecognizer() {}

    public static List<Bi> recognize(List<FenXing> fenXings, ChanLunConfig config) {
        List<Bi> result = new ArrayList<>();
        if (fenXings == null || fenXings.size() < 2) return result;

        // 强度过滤
        List<FenXing> filtered = fenXings;
        if (config.getMinFenXingStrength() > 0) {
            filtered = new ArrayList<>();
            for (FenXing fx : fenXings) {
                if (fx.getPowerScore() >= config.getMinFenXingStrength()) {
                    filtered.add(fx);
                }
            }
            if (filtered.size() < 2) return result;
        }

        FenXing last = null;

        for (FenXing fx : filtered) {
            if (last == null) {
                last = fx;
                continue;
            }

            // 同类型：更强则替换 last（延伸），同时更新前一笔的终点使笔连续
            if (last.getType().equals(fx.getType())) {
                if (isBetter(last, fx)) {
                    last = fx;
                    if (!result.isEmpty()) {
                        Bi prevBi = result.get(result.size() - 1);
                        prevBi.setEnd(fx);
                        prevBi.setHigh(Math.max(prevBi.getStart().getHigh(), fx.getHigh()));
                        prevBi.setLow(Math.min(prevBi.getStart().getLow(), fx.getLow()));
                        prevBi.setKlineCount(fx.getIndex() - prevBi.getStart().getIndex());
                    }
                }
                continue;
            }

            // 反向：满足距离条件则成笔，否则跳过当前分型，保持 last 不变
            if (!isValidBi(last, fx, config)) {
                // 距离不足不成笔，保持 last 不变，等待后续更远的分型
                continue;
            }

            result.add(buildBi(last, fx));
            last = fx;
        }

        return result;
    }

    private static Bi buildBi(FenXing start, FenXing end) {
        Bi bi = new Bi();
        bi.setStart(start);
        bi.setEnd(end);
        bi.setDirection("TOP".equals(end.getType()) ? "UP" : "DOWN");
        bi.setHigh(Math.max(start.getHigh(), end.getHigh()));
        bi.setLow(Math.min(start.getLow(), end.getLow()));
        bi.setKlineCount(end.getIndex() - start.getIndex());
        return bi;
    }

    private static boolean isBetter(FenXing a, FenXing b) {
        if ("TOP".equals(a.getType())) {
            return b.getHigh() > a.getHigh();
        } else {
            return b.getLow() < a.getLow();
        }
    }

    private static boolean isValidBi(FenXing start, FenXing end, ChanLunConfig config) {
        int gap = end.getIndex() - start.getIndex();
        int required = config.isUseNewBiRule()
                ? config.getMinKlineBetween() + 1   // 新笔规则：gap >= 2
                : config.getMinKlineBetween() + 2;  // 老笔规则：gap >= 3
        return gap >= required;
    }
}
