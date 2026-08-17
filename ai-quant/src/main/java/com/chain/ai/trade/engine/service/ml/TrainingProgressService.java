package com.chain.ai.trade.engine.service.ml;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TrainingProgressService {

    private final Map<String, TrainingProgress> progressMap = new ConcurrentHashMap<>();

    public void initProgress(String jobId, int totalTrees) {
        TrainingProgress p = new TrainingProgress();
        p.setJobId(jobId);
        p.setTotalTrees(totalTrees);
        p.setCompletedTrees(0);
        p.setStatus("RUNNING");
        progressMap.put(jobId, p);
        log.info("训练进度初始化: jobId={}, totalTrees={}", jobId, totalTrees);
    }

    public void updateProgress(String jobId, int completedTrees, double currentAccuracy) {
        TrainingProgress p = progressMap.get(jobId);
        if (p != null) {
            p.setCompletedTrees(completedTrees);
            p.setCurrentAccuracy(currentAccuracy);
        }
    }

    public void markSuccess(String jobId, double finalAccuracy) {
        TrainingProgress p = progressMap.get(jobId);
        if (p != null) {
            p.setCompletedTrees(p.getTotalTrees());
            p.setCurrentAccuracy(finalAccuracy);
            p.setStatus("SUCCESS");
        }
    }

    public void markFailed(String jobId, String errorMsg) {
        TrainingProgress p = progressMap.get(jobId);
        if (p != null) {
            p.setStatus("FAILED");
            p.setErrorMsg(errorMsg);
        }
    }

    public TrainingProgress getProgress(String jobId) {
        return progressMap.get(jobId);
    }

    public void removeProgress(String jobId) {
        progressMap.remove(jobId);
    }

    @Data
    public static class TrainingProgress {
        private String jobId;
        private int totalTrees;
        private int completedTrees;
        private double currentAccuracy;
        private String status;
        private String errorMsg;

        public double getProgressPct() {
            return totalTrees > 0 ? (double) completedTrees / totalTrees : 0;
        }
    }
}
