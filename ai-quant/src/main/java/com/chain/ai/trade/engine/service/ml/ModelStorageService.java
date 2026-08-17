package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.entity.MlModel;
import com.chain.ai.trade.engine.mapper.MlModelMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smile.classification.RandomForest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelStorageService {

    private final MlModelMapper mlModelMapper;
    private final ObjectMapper objectMapper;
    private final MlProperties mlProperties;

    @Transactional
    public MlModel saveModel(RandomForest forest, String symbol, String modelType, DirectionModelTrainer.TrainingResult result, Map<String, Object> trainingDataRange) {
        try {
            Files.createDirectories(Paths.get(mlProperties.getModel().getStorageDir()));
        } catch (IOException e) {
            throw new RuntimeException("无法创建模型存储目录: " + mlProperties.getModel().getStorageDir(), e);
        }

        int nextVersion = mlModelMapper.getNextVersion(symbol, modelType);
        if (nextVersion == 0) nextVersion = 1;

        String storageDir = mlProperties.getModel().getStorageDir();
        String fileName = String.format("%s_%s_v%d.ser", symbol, modelType, nextVersion);
        Path filePath = Paths.get(storageDir, fileName);
        String absolutePath = filePath.toAbsolutePath().toString();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(absolutePath))) {
            oos.writeObject(forest);
        } catch (IOException e) {
            throw new RuntimeException("保存模型文件失败: " + absolutePath, e);
        }

        long fileSize = 0;
        String md5 = "";
        try {
            fileSize = Files.size(filePath);
            md5 = computeMd5(filePath);
        } catch (IOException e) {
            log.warn("计算文件大小/MD5失败: {}", e.getMessage());
        }

        try {
            String rangeJson = objectMapper.writeValueAsString(trainingDataRange);
            String hyperparamsJson = objectMapper.writeValueAsString(Map.of(
                    "numTrees", mlProperties.getModel().getNumTrees(),
                    "maxDepth", mlProperties.getModel().getMaxDepth(),
                    "minSamples", mlProperties.getModel().getMinSamples()
            ));
            String importanceJson = result.getMetrics().getFeatureImportance() != null
                    ? objectMapper.writeValueAsString(result.getMetrics().getFeatureImportance())
                    : "{}";
            String confusionMatrixJson = result.getMetrics().getConfusionMatrix() != null
                    ? objectMapper.writeValueAsString(result.getMetrics().getConfusionMatrix())
                    : "{}";

            String accuracyTrendJson = buildAccuracyTrend(symbol, modelType, result.getMetrics().getAccuracy().doubleValue());

            mlModelMapper.deactivateAll(symbol, modelType);

            MlModel entity = MlModel.builder()
                    .symbol(symbol)
                    .modelType(modelType)
                    .version(nextVersion)
                    .filePath(absolutePath)
                    .fileSize(fileSize)
                    .md5Checksum(md5)
                    .accuracy(result.getMetrics().getAccuracy())
                    .recall(result.getMetrics().getRecall())
                    .precision(result.getMetrics().getPrecision())
                    .f1Score(result.getMetrics().getF1Score())
                    .featureImportance(importanceJson)
                    .confusionMatrix(confusionMatrixJson)
                    .accuracyTrend(accuracyTrendJson)
                    .hyperparams(hyperparamsJson)
                    .trainingDataRange(rangeJson)
                    .trainingDurationMs(result.getTrainingDurationMs())
                    .isActive(true)
                    .build();

            mlModelMapper.insert(entity);
            cleanupOldVersions(symbol, modelType);
            log.info("模型已保存: symbol={}, type={}, version={}, path={}", symbol, modelType, nextVersion, absolutePath);
            return entity;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化JSON失败", e);
        }
    }

    public RandomForest loadModel(String symbol, String modelType) {
        MlModel meta = mlModelMapper.findActive(symbol, modelType);
        if (meta == null) {
            log.warn("未找到活跃模型: symbol={}, type={}", symbol, modelType);
            return null;
        }
        return loadFromFile(meta.getFilePath());
    }

    public RandomForest loadById(String modelId) {
        MlModel meta = mlModelMapper.selectById(modelId);
        if (meta == null) return null;
        return loadFromFile(meta.getFilePath());
    }

    public RandomForest loadFromFile(String filePath) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return (RandomForest) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("加载模型文件失败: {}", filePath, e);
            return null;
        }
    }

    public MlModel getActiveModelMeta(String symbol, String modelType) {
        return mlModelMapper.findActive(symbol, modelType);
    }

    public List<MlModel> listModels(String symbol, String modelType) {
        return mlModelMapper.findAllBySymbolAndType(symbol, modelType);
    }

    private String buildAccuracyTrend(String symbol, String modelType, double currentAccuracy) {
        try {
            MlModel active = mlModelMapper.findActive(symbol, modelType);
            if (active != null && active.getAccuracyTrend() != null && !active.getAccuracyTrend().isEmpty()) {
                List<Map<String, Object>> trend = objectMapper.readValue(active.getAccuracyTrend(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                trend.add(Map.of(
                        "date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        "accuracy", currentAccuracy
                ));
                int maxEntries = 30;
                if (trend.size() > maxEntries) {
                    trend = trend.subList(trend.size() - maxEntries, trend.size());
                }
                return objectMapper.writeValueAsString(trend);
            }
        } catch (Exception e) {
            log.warn("读取历史准确率趋势失败，重新创建: {}", e.getMessage());
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        trend.add(Map.of(
                "date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "accuracy", currentAccuracy
        ));
        try {
            return objectMapper.writeValueAsString(trend);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private void cleanupOldVersions(String symbol, String modelType) {
        int maxVersions = mlProperties.getModel().getMaxVersionsPerSymbol();
        List<MlModel> allModels = mlModelMapper.findAllBySymbolAndType(symbol, modelType);
        if (allModels.size() <= maxVersions) return;

        allModels.sort((a, b) -> Integer.compare(b.getVersion(), a.getVersion()));
        for (int i = maxVersions; i < allModels.size(); i++) {
            MlModel old = allModels.get(i);
            try {
                Path filePath = Paths.get(old.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("删除旧模型文件失败: {}", old.getFilePath(), e);
            }
            mlModelMapper.deleteById(old.getId());
            log.info("已清理旧模型: symbol={}, type={}, version={}", symbol, modelType, old.getVersion());
        }
    }

    private String computeMd5(Path filePath) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
        byte[] data = Files.readAllBytes(filePath);
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
