package com.chain.ai.trade.engine.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import ta4jexamples.charting.workflow.ChartWorkflow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 图表可视化服务
 * 使用 Ta4j ChartWorkflow 生成交易策略的可视化图表
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChartService {

    private static final String CHART_OUTPUT_DIR = "target/charts";

    /**
     * 生成策略回测图表
     */
    public ChartResult generateStrategyChart(BarSeries series, String strategyName, TradingRecord record) {
        try {
            log.info("开始生成策略图表: {}", strategyName);
            log.info("图表参数: seriesBars={}, tradingRecordPositions={}",
                    series.getBarCount(), record.getPositionCount());

            // 确保输出目录存在
            Path outputDir = Paths.get(CHART_OUTPUT_DIR);
            Files.createDirectories(outputDir);
            log.info("输出目录准备完成: {}", outputDir);

            // 创建 ChartWorkflow
            ChartWorkflow chartWorkflow = new ChartWorkflow(CHART_OUTPUT_DIR);
            log.info("ChartWorkflow创建完成");

            // 根据数据量调整图表尺寸和数据采样
            int originalBarCount = series.getBarCount();
            BarSeries displaySeries = series;
            TradingRecord displayRecord = record;

            // 如果数据点太多，进行采样以提高可读性
            int sampleRate = 1;
            if (originalBarCount > 1000) {
                sampleRate = Math.max(1, originalBarCount / 1000); // 最多显示1000个数据点
                displaySeries = createSampledSeries(series, sampleRate);
                displayRecord = filterTradingRecordForSampledData(record, originalBarCount, sampleRate);
                log.info("数据采样: 原始数据点={}, 采样率={}, 显示数据点={}",
                        originalBarCount, sampleRate, displaySeries.getBarCount());
            }

            int barCount = displaySeries.getBarCount();
            log.info("图表配置: 原始数据点={}, 显示数据点={}",
                    originalBarCount, barCount);

            // 添加价格指标（基于采样数据）
            ClosePriceIndicator closePrice = new ClosePriceIndicator(displaySeries);
            log.info("价格指标创建完成");

            // 添加技术指标（调整周期以适应采样数据）
            int smaPeriod = Math.min(20, Math.max(5, barCount / 50)); // 根据数据点数量调整周期
            int emaPeriod = Math.min(50, Math.max(10, barCount / 20));

            SMAIndicator sma = new SMAIndicator(closePrice, smaPeriod);
            EMAIndicator ema = new EMAIndicator(closePrice, emaPeriod);
            log.info("技术指标创建完成: SMA{}, EMA{}", smaPeriod, emaPeriod);

            // 生成图表文件名
            String fileName = String.format("%s_%s",
                    strategyName.replaceAll("[^a-zA-Z0-9]", "_"),
                    System.currentTimeMillis());
            log.info("图表文件名生成: {}", fileName);

            // 使用 ChartWorkflow 生成图表
            log.info("开始构建图表...");
            var chart = chartWorkflow.builder()
                    .withTitle(strategyName + " - Backtest Results (" + originalBarCount + "→" + barCount + " bars)")
                    .withSeries(displaySeries)
                    .withIndicatorOverlay(sma)
                    .withIndicatorOverlay(ema)
                    .withTradingRecordOverlay(displayRecord)
                    .toChart();
            log.info("图表构建完成");

            // 保存图表
            log.info("开始保存图表图片...");
            var savedPath = chartWorkflow.saveChartImage(chart, displaySeries, fileName);
            log.info("图表保存方法调用完成, savedPath present: {}", savedPath.isPresent());

            if (savedPath.isPresent()) {
                String imagePath = savedPath.get().toString();
                log.info("图表文件保存路径: {}", imagePath);

                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    long fileSize = imageFile.length();
                    log.info("图表文件存在，大小: {} bytes", fileSize);

                    byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    log.info("Base64编码完成，长度: {} 字符", base64Image.length());

                    log.info("图表生成成功完成");
                    return ChartResult.builder()
                            .success(true)
                            .imageData(base64Image)
                            .imagePath(imagePath)
                            .fileName(fileName + ".png")
                            .build();
                } else {
                    log.error("图表文件不存在: {}", imagePath);
                }
            } else {
                log.error("saveChartImage 返回空路径");
            }

            return ChartResult.builder()
                    .success(false)
                    .errorMessage("图表文件生成失败")
                    .build();

        } catch (Exception e) {
            log.error("生成策略图表失败", e);
            return ChartResult.builder()
                    .success(false)
                    .errorMessage("图表生成失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 创建采样后的BarSeries，用于大量数据显示优化
     */
    private BarSeries createSampledSeries(BarSeries originalSeries, int sampleRate) {
        List<Bar> sampledBars = new ArrayList<>();

        for (int i = 0; i < originalSeries.getBarCount(); i += sampleRate) {
            sampledBars.add(originalSeries.getBar(i));
        }

        // 创建新的BarSeries
        BarSeries sampledSeries = new BaseBarSeriesBuilder()
                .withName(originalSeries.getName() + "_sampled")
                .withBars(sampledBars)
                .build();

        return sampledSeries;
    }

    /**
     * 过滤交易记录以适应采样后的数据
     */
    private TradingRecord filterTradingRecordForSampledData(TradingRecord originalRecord, int originalBarCount, int sampleRate) {
        if (sampleRate == 1) {
            return originalRecord; // 不需要过滤
        }

        TradingRecord filteredRecord = new BaseTradingRecord();

        // 遍历原始交易记录
        for (Position position : originalRecord.getPositions()) {
            int entryIndex = position.getEntry().getIndex();
            int exitIndex = position.getExit() != null ? position.getExit().getIndex() : -1;

            // 检查交易位置是否在采样数据范围内
            if (entryIndex < originalBarCount) {
                int sampledEntryIndex = entryIndex / sampleRate;

                if (exitIndex >= 0 && exitIndex < originalBarCount) {
                    // 完整的交易（有入场和出场）
                    int sampledExitIndex = exitIndex / sampleRate;
                    if (sampledEntryIndex < sampledExitIndex) {
                        filteredRecord.enter(sampledEntryIndex, position.getEntry().getNetPrice(), position.getEntry().getAmount());
                        filteredRecord.exit(sampledExitIndex, position.getExit().getNetPrice(), position.getExit().getAmount());
                    }
                } else {
                    // 只有入场的交易
                    filteredRecord.enter(sampledEntryIndex, position.getEntry().getNetPrice(), position.getEntry().getAmount());
                }
            }
        }

        return filteredRecord;
    }

    /**
     * 图表结果
     */
    @Data
    @Builder
    public static class ChartResult {
        private boolean success;
        private String imageData; // Base64 编码的图片数据
        private String imagePath; // 图片文件路径
        private String fileName;  // 图片文件名
        private String errorMessage;
    }
}
