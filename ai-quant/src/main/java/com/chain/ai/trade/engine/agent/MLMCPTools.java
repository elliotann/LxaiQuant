package com.chain.ai.trade.engine.agent;

import com.chain.ai.trade.engine.service.ml.MLInferenceService;
import com.chain.ai.trade.engine.service.ml.MLTrainingService;
import com.chain.ai.trade.engine.entity.MlTrainingJob;
import com.chain.ai.trade.engine.model.ml.PredictionResult;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

@Slf4j
@Component
@RequiredArgsConstructor
public class MLMCPTools {

    private final MLInferenceService mlInferenceService;
    private final MLTrainingService mlTrainingService;
    private final KLineV1Service kLineV1Service;

    @Tool(name = "mlPredict", description = "使用机器学习模型预测某个交易对后续方向（涨/跌）")
    public String mlPredict(
            @ToolParam(description = "交易对符号，如 BTCUSDT") String symbol) {
        try {
            KLineHistoryRequest request = new KLineHistoryRequest();
            request.setSymbol(symbol);
            request.setInterval("1h");
            request.setLimit(100);
            KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);

            if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
                return "无法获取 " + symbol + " 的K线数据，请检查交易对是否正确";
            }

            BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
            PredictionResult result = mlInferenceService.predictDirection(series, symbol);

            if (!result.isSuccess()) {
                return "预测失败: " + result.getMessage();
            }

            return String.format("%s ML预测结果: 方向=%s, 置信度=%.1f%%, 上涨概率=%.1f%%, 下跌概率=%.1f%%",
                    symbol, result.getDirection(), result.getConfidence() * 100,
                    result.getProbabilityUp() * 100, result.getProbabilityDown() * 100);
        } catch (Exception e) {
            log.error("ML预测异常: symbol={}", symbol, e);
            return "预测异常: " + e.getMessage();
        }
    }

    @Tool(name = "mlTrain", description = "触发某个交易对的方向预测模型训练")
    public String mlTrain(
            @ToolParam(description = "交易对符号，如 BTCUSDT") String symbol) {
        try {
            MlTrainingJob job = mlTrainingService.trainDirectionModel(symbol);
            if ("SUCCESS".equals(job.getStatus())) {
                return String.format("模型训练完成: symbol=%s, 准确率=%.2f%%",
                        symbol, job.getAccuracy().multiply(java.math.BigDecimal.valueOf(100)));
            } else {
                return "模型训练失败: " + job.getErrorMsg();
            }
        } catch (Exception e) {
            return "训练异常: " + e.getMessage();
        }
    }
}
