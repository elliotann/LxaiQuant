package com.chain.ai.trade.engine.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class OptimizationTaskManager {
    private final Map<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();

    public void submit(String taskId, Runnable runnable) {
        CompletableFuture<?> future = CompletableFuture.runAsync(runnable);
        futures.put(taskId, future);
        future.whenComplete((r, ex) -> futures.remove(taskId));
    }

    public boolean cancel(String taskId) {
        CompletableFuture<?> future = futures.get(taskId);
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        futures.remove(taskId);
        return cancelled;
    }

    public boolean isRunning(String taskId) {
        CompletableFuture<?> future = futures.get(taskId);
        return future != null && !future.isDone();
    }
}
