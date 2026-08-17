package com.chain.ai.trade.engine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.impl.AiStrategyService;
import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyEntryRule;
import com.chain.ai.trade.engine.strategy.entity.dos.StrategyParameter;
import com.chain.ai.trade.engine.strategy.entity.dto.AiConfirmRequest;
import com.chain.ai.trade.engine.strategy.entity.dto.AiGenerateRequest;
import com.chain.ai.trade.engine.strategy.entity.dto.AiStrategyRecommendation;
import com.chain.ai.trade.engine.strategy.ExitRulesConfigDTO;
import com.chain.ai.trade.engine.strategy.enums.StrategyType;
import com.chain.ai.trade.engine.strategy.service.IStrategyService;
import com.chain.ai.trade.engine.strategy.service.IStrategyParameterService;
import com.chain.ai.trade.engine.strategy.service.IStrategyEntryRuleService;
import com.chain.ai.trade.engine.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 策略管理控制器
 * 提供策略的分页查询和管理功能
 * 提供 /strategies 端点以匹配前端策略列表的API调用
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StrategyManageController {

    private final IStrategyService strategyService;
    private final IStrategyParameterService strategyParameterService;
    private final IStrategyEntryRuleService strategyEntryRuleService;
    private final AiStrategyService aiStrategyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分页查询策略列表（用于前端策略列表展示）
     * 提供 /strategies 端点以匹配前端API调用
     * 
     * @param page 页码（从1开始，前端使用 page 参数）
     * @param limit 每页大小（前端使用 limit 参数）
     * @param search 搜索关键词（策略名称）
     * @param status 状态（可选）
     * @param type 策略类型（可选）
     * @return 分页结果
     */
    @GetMapping("/strategies")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStrategiesPaged(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        log.info("收到策略分页查询请求: page={}, limit={}, search={}, status={}, type={}", 
                page, limit, search, status, type);

        try {
            // 参数校验
            if (page < 1) {
                page = 1;
            }
            if (limit < 1 || limit > 100) {
                limit = 15;
            }

            // 执行分页查询
            Page<Strategy> pageParam = new Page<>(page, limit);
            IPage<Strategy> resultPage = strategyService.pageStrategies(
                    pageParam, search, status, type, null);

            // 转换为前端需要的格式
            List<Map<String, Object>> strategies = new ArrayList<>();
            for (Strategy strategy : resultPage.getRecords()) {
                Map<String, Object> strategyMap = new HashMap<>();
                strategyMap.put("id", strategy.getStrategyId() != null ? strategy.getStrategyId() : strategy.getId());
                strategyMap.put("name", strategy.getName() != null ? strategy.getName() : "未命名策略");
                strategyMap.put("type", strategy.getStrategyType() != null ? strategy.getStrategyType().toLowerCase() : "");
                strategyMap.put("symbol", ""); // 暂时为空
                strategyMap.put("timeframe", strategy.getTimeFrame() != null ? strategy.getTimeFrame() : "");
                
                // 收益率（可以从统计信息中获取，暂时使用默认值）
                if (strategy.getAvgAnnualReturn() != null) {
                    strategyMap.put("profitRate", strategy.getAvgAnnualReturn().multiply(
                        java.math.BigDecimal.valueOf(100)).doubleValue());
                } else {
                    strategyMap.put("profitRate", 0.0);
                }
                
                // 创建时间
                if (strategy.getCreatedAt() != null) {
                    strategyMap.put("createdAt", strategy.getCreatedAt().toString());
                } else {
                    strategyMap.put("createdAt", "");
                }
                
                // 状态
                strategyMap.put("status", strategy.getStatus() != null ? strategy.getStatus().toLowerCase() : "draft");
                
                // 添加其他有用的字段
                strategyMap.put("description", strategy.getDescription());
                strategyMap.put("displayName", strategy.getDisplayName());
                strategyMap.put("backtestCount", strategy.getBacktestCount());
                strategyMap.put("avgSharpeRatio", strategy.getAvgSharpeRatio());
                
                strategies.add(strategyMap);
            }

            // 构建分页信息
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("total", resultPage.getTotal());
            pagination.put("page", resultPage.getCurrent());
            pagination.put("limit", resultPage.getSize());
            pagination.put("pages", resultPage.getPages());

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("strategies", strategies);
            data.put("pagination", pagination);

            log.info("分页查询完成: 总记录数={}, 当前页={}, 每页大小={}, 总页数={}",
                    resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize(), resultPage.getPages());

            return ResponseEntity.ok(ApiResponse.success("查询成功", data));

        } catch (Exception e) {
            log.error("分页查询策略失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取策略详情
     * 
     * @param id 策略ID（strategy_id或id）
     * @return 策略详情
     */
    @GetMapping("/strategies/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStrategyDetail(@PathVariable String id) {
        log.info("收到获取策略详情请求: id={}", id);

        try {
            Strategy strategy;
            
            // 尝试通过strategyId查找
            strategy = strategyService.getByStrategyId(id);
            if (strategy == null) {
                // 如果strategyId找不到，尝试通过id查找
                try {
                    Long longId = Long.parseLong(id);
                    strategy = strategyService.getById(longId);
                } catch (NumberFormatException e) {
                    // id不是数字，继续
                }
            }
            
            if (strategy == null) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "策略不存在"));
            }

            // 转换为前端需要的格式
            Map<String, Object> strategyMap = convertStrategyToMap(strategy);
            String strategyId = strategy.getStrategyId() != null ? strategy.getStrategyId() : String.valueOf(strategy.getId());
            Map<String, Object> positionRisk = buildPositionRiskResponse(strategyId);
            if (!positionRisk.isEmpty()) {
                strategyMap.put("positionRisk", positionRisk);
            }
            Map<String, Object> exitRules = loadParameterGroupMap(strategyId, "exit_rules_config", "config");
            if (!exitRules.isEmpty()) {
                strategyMap.put("exitRules", exitRules);
            }
            Map<String, Object> dynamicRiskEngine = loadParameterGroupMap(strategyId, "dynamic_risk_engine", "config");
            if (!dynamicRiskEngine.isEmpty()) {
                strategyMap.put("dynamicRiskEngine", dynamicRiskEngine);
            }
            Map<String, Object> riskControl = buildRiskControlResponse(strategyId);
            if (!riskControl.isEmpty()) {
                strategyMap.put("riskControl", riskControl);
            }
            strategyMap.put("entryRules", strategyEntryRuleService.loadEntryRulesResponse(strategyId));

            return ResponseEntity.ok(ApiResponse.success("查询成功", strategyMap));

        } catch (Exception e) {
            log.error("获取策略详情失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 创建策略
     * 
     * @param requestBody 策略数据
     * @return 创建后的策略
     */
    @PostMapping("/strategies")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStrategy(
            @RequestBody Map<String, Object> requestBody) {
        
        log.info("收到创建策略请求: data={}", requestBody);

        try {
            // 创建策略对象
            Strategy strategy = new Strategy();
            
            // 生成策略ID
            String strategyId = strategyService.generateStrategyId();
            strategy.setStrategyId(strategyId);
            
            // 设置基本字段
            updateStrategyFields(strategy, requestBody);
            
            // 设置默认值
            if (strategy.getOwnerId() == null) {
                strategy.setOwnerId(parseUserId(SecurityUtils.getCurrentUserId()));
            }
            if (strategy.getCreatedBy() == null) {
                strategy.setCreatedBy(parseUserId(SecurityUtils.getCurrentUserId()));
            }
            if (strategy.getStatus() == null) {
                strategy.setStatus("draft");
            }
            if (strategy.getVisibility() == null) {
                strategy.setVisibility("private");
            }
            
            // 保存策略
            boolean success = strategyService.save(strategy);
            
            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("创建失败"));
            }

            // 保存策略参数到 StrategyParameter 表
            saveStrategyParameters(strategyId, requestBody);
            if (requestBody.containsKey("positionRisk") || requestBody.containsKey("exitRules")) {
                savePositionRiskParameters(strategyId, requestBody);
                saveExitRulesParameters(strategyId, requestBody);
            } else {
                saveRiskControlParameters(strategyId, requestBody);
            }
            // 动态风控引擎（移动止损/移动止盈）-> dynamic_risk_engine 组
            saveDynamicRiskEngineParameters(strategyId, requestBody);
            // 保存入场规则到专用表
            if (requestBody.containsKey("entryRules")) {
                Object entryRulesObj = requestBody.get("entryRules");
                if (entryRulesObj instanceof String) {
                    strategyEntryRuleService.saveEntryRules(strategyId, (String) entryRulesObj);
                } else if (entryRulesObj != null) {
                    strategyEntryRuleService.saveEntryRules(strategyId, entryRulesObj.toString());
                }
            }

            // 返回创建后的策略
            Map<String, Object> strategyMap = convertStrategyToMap(strategy);
            strategyMap.put("entryRules", strategyEntryRuleService.loadEntryRulesResponse(strategyId));

            return ResponseEntity.ok(ApiResponse.success("创建成功", strategyMap));

        } catch (Exception e) {
            log.error("创建策略失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新策略
     * 
     * @param id 策略ID（strategy_id或id）
     * @param requestBody 更新的策略数据
     * @return 更新后的策略
     */
    @PutMapping("/strategies/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStrategy(
            @PathVariable String id,
            @RequestBody Map<String, Object> requestBody) {
        
        log.info("收到更新策略请求: id={}, data={}", id, requestBody);

        try {
            Strategy strategy;
            
            // 尝试通过strategyId查找
            strategy = strategyService.getByStrategyId(id);
            if (strategy == null) {
                // 如果strategyId找不到，尝试通过id查找
                try {
                    Long longId = Long.parseLong(id);
                    strategy = strategyService.getById(longId);
                } catch (NumberFormatException e) {
                    // id不是数字，继续
                }
            }
            
            if (strategy == null) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "策略不存在"));
            }

            // 如果 strategyId 变更，需先迁移关联表的数据，再更新策略本身的 strategyId
            String newStrategyId = requestBody.containsKey("strategyId") ? (String) requestBody.get("strategyId") : null;
            String oldStrategyId = strategy.getStrategyId();
            if (newStrategyId != null && oldStrategyId != null && !newStrategyId.equals(oldStrategyId)) {
                log.info("strategyId 变更，迁移关联表: {} -> {}", oldStrategyId, newStrategyId);
                migrateRelatedStrategyId(oldStrategyId, newStrategyId);
                strategy.setStrategyId(newStrategyId);
            }

            // 更新策略字段（strategyId 已在上面单独处理）
            updateStrategyFields(strategy, requestBody);

            // 保存更新
            boolean success = strategyService.updateById(strategy);
            
            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("更新失败"));
            }

            // 保存策略参数到 StrategyParameter 表
            String strategyId = strategy.getStrategyId() != null ? strategy.getStrategyId() : String.valueOf(strategy.getId());
            saveStrategyParameters(strategyId, requestBody);
            // 仓位风险管理 -> position_risk 组；出场规则（止损/止盈/分批止盈）-> exit_rules 组；无则回退为原 risk_control
            if (requestBody.containsKey("positionRisk") || requestBody.containsKey("exitRules")) {
                savePositionRiskParameters(strategyId, requestBody);
                saveExitRulesParameters(strategyId, requestBody);
            } else {
                saveRiskControlParameters(strategyId, requestBody);
            }
            // 动态风控引擎（移动止损/移动止盈）-> dynamic_risk_engine 组
            saveDynamicRiskEngineParameters(strategyId, requestBody);
            // 保存入场规则到专用表
            if (requestBody.containsKey("entryRules")) {
                Object entryRulesObj = requestBody.get("entryRules");
                if (entryRulesObj instanceof String) {
                    strategyEntryRuleService.saveEntryRules(strategyId, (String) entryRulesObj);
                } else if (entryRulesObj != null) {
                    strategyEntryRuleService.saveEntryRules(strategyId, entryRulesObj.toString());
                }
            }

            // 返回更新后的策略
            Map<String, Object> strategyMap = convertStrategyToMap(strategy);
            strategyMap.put("entryRules", strategyEntryRuleService.loadEntryRulesResponse(strategyId));

            return ResponseEntity.ok(ApiResponse.success("更新成功", strategyMap));

        } catch (Exception e) {
            log.error("更新策略失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 删除策略
     *
     * @param id 策略ID（strategy_id或id）
     * @return 删除结果
     */
    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteStrategy(@PathVariable String id) {
        log.info("收到删除策略请求: id={}", id);

        try {
            Strategy strategy;

            strategy = strategyService.getByStrategyId(id);
            if (strategy == null) {
                try {
                    Long longId = Long.parseLong(id);
                    strategy = strategyService.getById(longId);
                } catch (NumberFormatException e) {
                    // id不是数字，继续
                }
            }

            if (strategy == null) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "策略不存在"));
            }

            String strategyId = strategy.getStrategyId() != null ? strategy.getStrategyId() : String.valueOf(strategy.getId());

            try {
                LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(StrategyParameter::getStrategyId, strategyId);
                strategyParameterService.remove(wrapper);
            } catch (Exception e) {
                log.warn("删除策略参数失败: strategyId={}, error={}", strategyId, e.getMessage());
            }

            boolean success;
            if (strategy.getId() != null) {
                success = strategyService.removeById(strategy.getId());
            } else {
                success = strategyService.removeById(strategyId);
            }

            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("删除失败"));
            }

            return ResponseEntity.ok(ApiResponse.success("删除成功", true));

        } catch (Exception e) {
            log.error("删除策略失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("删除失败: " + e.getMessage()));
        }
    }

    /**
     * 更新策略状态（启用/停用）
     *
     * @param id 策略ID（strategy_id或id）
     * @param requestBody { "status": "active" | "inactive" }
     * @return 更新后的策略
     */
    @PutMapping("/strategies/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStrategyStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> requestBody) {
        log.info("收到更新策略状态请求: id={}, body={}", id, requestBody);

        try {
            Object statusObj = requestBody != null ? requestBody.get("status") : null;
            String status = statusObj != null ? String.valueOf(statusObj).trim() : null;
            if (StringUtils.isBlank(status)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少 status 参数"));
            }
            String statusLower = status.toLowerCase(Locale.ROOT);
            if (!"active".equals(statusLower) && !"inactive".equals(statusLower)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "status 仅支持 active / inactive"));
            }

            Strategy strategy = strategyService.getByStrategyId(id);
            if (strategy == null) {
                try {
                    Long longId = Long.parseLong(id);
                    strategy = strategyService.getById(longId);
                } catch (NumberFormatException ignored) {
                }
            }
            if (strategy == null) {
                return ResponseEntity.status(404).body(ApiResponse.error(404, "策略不存在"));
            }

            strategy.setStatus(statusLower);
            boolean success = strategyService.updateById(strategy);
            if (!success) {
                return ResponseEntity.internalServerError().body(ApiResponse.error("更新策略状态失败"));
            }

            Map<String, Object> strategyMap = convertStrategyToMap(strategy);
            return ResponseEntity.ok(ApiResponse.success("更新成功", strategyMap));
        } catch (Exception e) {
            log.error("更新策略状态失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 将Strategy实体转换为Map
     */
    private Map<String, Object> convertStrategyToMap(Strategy strategy) {
        Map<String, Object> strategyMap = new HashMap<>();
        strategyMap.put("id", strategy.getStrategyId() != null ? strategy.getStrategyId() : strategy.getId());
        strategyMap.put("strategyId", strategy.getStrategyId());
        strategyMap.put("name", strategy.getName());
        strategyMap.put("displayName", strategy.getDisplayName());
        strategyMap.put("description", strategy.getDescription());
        strategyMap.put("briefDescription", strategy.getBriefDescription());
        strategyMap.put("strategyType", strategy.getStrategyType());
        strategyMap.put("categoryId", strategy.getCategoryId());
        strategyMap.put("subCategory", strategy.getSubCategory());
        strategyMap.put("tags", strategy.getTags());
        strategyMap.put("codeContent", strategy.getCodeContent());
        strategyMap.put("className", strategy.getClassName());
        strategyMap.put("filePath", strategy.getFilePath());
        strategyMap.put("entryPoint", strategy.getEntryPoint());
        strategyMap.put("defaultParameters", strategy.getDefaultParameters());
        strategyMap.put("parameterSchema", strategy.getParameterSchema());
        strategyMap.put("dependencies", strategy.getDependencies());
        strategyMap.put("autoSignal", strategy.getAutoSignal());
        strategyMap.put("status", strategy.getStatus());
        strategyMap.put("visibility", strategy.getVisibility());
        strategyMap.put("isSystem", strategy.getIsSystem());
        strategyMap.put("isTemplate", strategy.getIsTemplate());
        strategyMap.put("frequency", strategy.getFrequency());
        strategyMap.put("marketType", strategy.getMarketType());
        strategyMap.put("timeFrame", strategy.getTimeFrame());
        strategyMap.put("supportsLong", strategy.getSupportsLong());
        strategyMap.put("supportsShort", strategy.getSupportsShort());
        strategyMap.put("supportsLeverage", strategy.getSupportsLeverage());
        strategyMap.put("minCapital", strategy.getMinCapital());
        strategyMap.put("currentVersion", strategy.getCurrentVersion());
        strategyMap.put("latestVersionId", strategy.getLatestVersionId());
        strategyMap.put("backtestCount", strategy.getBacktestCount());
        strategyMap.put("avgSharpeRatio", strategy.getAvgSharpeRatio());
        strategyMap.put("avgAnnualReturn", strategy.getAvgAnnualReturn());
        strategyMap.put("avgMaxDrawdown", strategy.getAvgMaxDrawdown());
        strategyMap.put("successRate", strategy.getSuccessRate());
        strategyMap.put("ownerId", strategy.getOwnerId());
        strategyMap.put("ownerName", strategy.getOwnerName());
        strategyMap.put("teamId", strategy.getTeamId());
        strategyMap.put("createdBy", strategy.getCreatedBy());
        strategyMap.put("createdByName", strategy.getCreatedByName());
        strategyMap.put("approvedBy", strategy.getApprovedBy());
        strategyMap.put("approvedAt", strategy.getApprovedAt());
        strategyMap.put("createdAt", strategy.getCreatedAt());
        strategyMap.put("updatedAt", strategy.getUpdatedAt());
        strategyMap.put("lastExecutedAt", strategy.getLastExecutedAt());
        strategyMap.put("lastModifiedAt", strategy.getLastModifiedAt());
        return strategyMap;
    }

    /**
     * 查询所有策略信息
     * 返回数据库中所有策略的完整信息
     * 
     * @return 策略列表
     */
    @GetMapping("/listStrategys")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllStrategies() {
        log.info("收到查询所有策略信息请求");

        try {
            // 查询所有策略
            List<Strategy> strategies = strategyService.list();
            
            // 转换为前端需要的格式
            List<Map<String, Object>> strategyList = new ArrayList<>();
            for (Strategy strategy : strategies) {
                Map<String, Object> strategyMap = convertStrategyToMap(strategy);
                strategyList.add(strategyMap);
            }
            
            log.info("查询到 {} 个策略", strategyList.size());
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", strategyList));

        } catch (Exception e) {
            log.error("查询所有策略信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 更新策略字段
     */
    private void updateStrategyFields(Strategy strategy, Map<String, Object> data) {
        if (data.containsKey("name")) {
            strategy.setName((String) data.get("name"));
        }
        if (data.containsKey("displayName")) {
            strategy.setDisplayName((String) data.get("displayName"));
        }
        if (data.containsKey("description")) {
            strategy.setDescription((String) data.get("description"));
        }
        if (data.containsKey("briefDescription")) {
            strategy.setBriefDescription((String) data.get("briefDescription"));
        }
        if (data.containsKey("strategyType")) {
            strategy.setStrategyType((String) data.get("strategyType"));
        }
        if (data.containsKey("categoryId")) {
            Object categoryId = data.get("categoryId");
            if (categoryId != null) {
                strategy.setCategoryId(categoryId instanceof Number 
                    ? ((Number) categoryId).longValue() 
                    : Long.parseLong(categoryId.toString()));
            }
        }
        if (data.containsKey("subCategory")) {
            strategy.setSubCategory((String) data.get("subCategory"));
        }
        if (data.containsKey("status")) {
            strategy.setStatus((String) data.get("status"));
        }
        if (data.containsKey("visibility")) {
            strategy.setVisibility((String) data.get("visibility"));
        }
        if (data.containsKey("frequency")) {
            strategy.setFrequency((String) data.get("frequency"));
        }
        if (data.containsKey("marketType")) {
            strategy.setMarketType((String) data.get("marketType"));
        }
        if (data.containsKey("timeFrame")) {
            strategy.setTimeFrame((String) data.get("timeFrame"));
        }
        if (data.containsKey("codeContent")) {
            strategy.setCodeContent((String) data.get("codeContent"));
        }
        if (data.containsKey("className")) {
            strategy.setClassName((String) data.get("className"));
        }
        if (data.containsKey("filePath")) {
            strategy.setFilePath((String) data.get("filePath"));
        }
        if (data.containsKey("entryPoint")) {
            strategy.setEntryPoint((String) data.get("entryPoint"));
        }
        // 注意：JSON字段（tags, defaultParameters等）需要特殊处理，这里简化处理
        if (data.containsKey("tags")) {
            strategy.setTags(data.get("tags") != null ? data.get("tags").toString() : null);
        }
        if (data.containsKey("defaultParameters")) {
            strategy.setDefaultParameters(data.get("defaultParameters") != null 
                ? data.get("defaultParameters").toString() : null);
        }
        if (data.containsKey("parameterSchema")) {
            strategy.setParameterSchema(data.get("parameterSchema") != null 
                ? data.get("parameterSchema").toString() : null);
        }
        if (data.containsKey("dependencies")) {
            strategy.setDependencies(data.get("dependencies") != null 
                ? data.get("dependencies").toString() : null);
        }
        if (data.containsKey("autoSignal")) {
            Object autoSignalValue = data.get("autoSignal");
            if (autoSignalValue != null) {
                if (autoSignalValue instanceof String) {
                    strategy.setAutoSignal((String) autoSignalValue);
                } else {
                    strategy.setAutoSignal(autoSignalValue.toString());
                }
            } else {
                strategy.setAutoSignal(null);
            }
        }
    }

    /**
     * 迁移关联表的 strategyId（当 strategyId 变更时调用）
     * 先清除目标中的冲突记录，再迁移旧记录到新 strategyId
     */
    private void migrateRelatedStrategyId(String oldStrategyId, String newStrategyId) {
        // 1. 迁移策略参数表
        // 唯一索引 (strategy_id, param_name)，需先清除目标中同名参数避免冲突
        List<Object> oldParamNames = strategyParameterService.listObjs(
                new LambdaQueryWrapper<StrategyParameter>()
                        .eq(StrategyParameter::getStrategyId, oldStrategyId)
                        .select(StrategyParameter::getName)
        );
        if (!oldParamNames.isEmpty()) {
            strategyParameterService.remove(
                    new LambdaQueryWrapper<StrategyParameter>()
                            .eq(StrategyParameter::getStrategyId, newStrategyId)
                            .in(StrategyParameter::getName, oldParamNames)
            );
        }
        strategyParameterService.update(
                new LambdaUpdateWrapper<StrategyParameter>()
                        .eq(StrategyParameter::getStrategyId, oldStrategyId)
                        .set(StrategyParameter::getStrategyId, newStrategyId)
        );
        // 2. 迁移入场规则表（子表 entry_rule_condition 通过 ruleId 关联，无需直接操作）
        strategyEntryRuleService.update(
                new LambdaUpdateWrapper<StrategyEntryRule>()
                        .eq(StrategyEntryRule::getStrategyId, oldStrategyId)
                        .set(StrategyEntryRule::getStrategyId, newStrategyId)
        );
        log.info("关联表 strategyId 迁移完成: {} -> {}", oldStrategyId, newStrategyId);
    }

    /**
     * 保存策略参数到 StrategyParameter 表
     */
    private void saveStrategyParameters(String strategyId, Map<String, Object> requestBody) {
        try {
            // 优先使用 defaultParameters，如果没有则使用 parameters
            Object parametersObj = requestBody.get("defaultParameters");
            if (parametersObj == null) {
                parametersObj = requestBody.get("parameters");
            }
            
            if (parametersObj == null) {
                log.debug("请求中没有 defaultParameters 或 parameters 字段，跳过参数保存");
                return;
            }

            // 解析参数（可能是 JSON 字符串、对象或 Map）
            List<Map<String, Object>> paramList = null;
            
            if (parametersObj instanceof String) {
                String paramJson = (String) parametersObj;
                if (paramJson.trim().isEmpty() || paramJson.equals("null")) {
                    log.debug("参数为空字符串或 null，跳过参数保存");
                    return;
                }
                paramList = objectMapper.readValue(paramJson, new TypeReference<List<Map<String, Object>>>() {});
            } else if (parametersObj instanceof List) {
                paramList = (List<Map<String, Object>>) parametersObj;
            } else if (parametersObj instanceof Map) {
                // 如果是 Map 对象，转换为列表格式（每个 key-value 作为一个参数）
                Map<String, Object> paramMap = (Map<String, Object>) parametersObj;
                paramList = new ArrayList<>();
                for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                    Map<String, Object> param = new HashMap<>();
                    param.put("name", entry.getKey());
                    param.put("type", "string"); // 默认类型
                    param.put("defaultValue", entry.getValue() != null ? entry.getValue().toString() : "");
                    paramList.add(param);
                }
            } else {
                log.warn("参数格式不正确，跳过参数保存: {}", parametersObj.getClass());
                return;
            }

            if (paramList == null || paramList.isEmpty()) {
                log.debug("参数列表为空，跳过参数保存");
                return;
            }

            // 转换为 StrategyParameter 列表
            List<StrategyParameter> parameters = new ArrayList<>();
            for (Map<String, Object> paramMap : paramList) {
                // 只保存有名称的参数
                String name = paramMap.get("name") != null ? paramMap.get("name").toString().trim() : null;
                if (name == null || name.isEmpty()) {
                    continue; // 跳过没有名称的参数
                }

                StrategyParameter param = new StrategyParameter();
                param.setName(name);
                param.setDisplayName(name);
                
                // 设置类型
                if (paramMap.containsKey("type")) {
                    String type = paramMap.get("type").toString();
                    param.setDataType(type);
                    param.setParamType(type);
                }
                
                // 设置默认值
                if (paramMap.containsKey("defaultValue")) {
                    Object defaultValue = paramMap.get("defaultValue");
                    if (defaultValue != null) {
                        param.setDefaultValue(defaultValue.toString());
                    }
                }
                
                // 设置最小值
                if (paramMap.containsKey("min")) {
                    Object min = paramMap.get("min");
                    if (min != null && !min.toString().trim().isEmpty()) {
                        param.setMinValue(min.toString());
                    }
                }
                
                // 设置最大值
                if (paramMap.containsKey("max")) {
                    Object max = paramMap.get("max");
                    if (max != null && !max.toString().trim().isEmpty()) {
                        param.setMaxValue(max.toString());
                    }
                }
                
                // 设置默认值
                param.setIsRequired(false);
                param.setIsVisible(true);
                param.setIsAdvanced(false);
                param.setIsArray(false);
                
                parameters.add(param);
            }

            // 保存参数
            if (!parameters.isEmpty()) {
                strategyParameterService.saveStrategyParameters(strategyId, parameters);
                log.info("策略参数保存成功: strategyId={}, count={}", strategyId, parameters.size());
            } else {
                log.debug("没有有效的参数需要保存");
            }

        } catch (Exception e) {
            log.error("保存策略参数失败: strategyId={}", strategyId, e);
            // 不抛出异常，避免影响策略本身的保存
        }
    }

    /**
     * 保存风控参数到 StrategyParameter 表
     */
    private void saveRiskControlParameters(String strategyId, Map<String, Object> requestBody) {
        try {
            // 获取风控配置（可能是 JSON 字符串或对象）
            Object riskControlObj = requestBody.get("riskControl");
            if (riskControlObj == null) {
                log.debug("请求中没有 riskControl 字段，跳过风控参数保存");
                return;
            }

            // 解析风控配置
            Map<String, Object> riskControlMap;
            if (riskControlObj instanceof String) {
                String riskControlJson = (String) riskControlObj;
                if (riskControlJson.trim().isEmpty() || riskControlJson.equals("null")) {
                    log.debug("riskControl 为空字符串或 null，跳过风控参数保存");
                    return;
                }
                riskControlMap = objectMapper.readValue(riskControlJson, new TypeReference<Map<String, Object>>() {});
            } else if (riskControlObj instanceof Map) {
                riskControlMap = (Map<String, Object>) riskControlObj;
            } else {
                log.warn("riskControl 格式不正确，跳过风控参数保存: {}", riskControlObj.getClass());
                return;
            }

            if (riskControlMap == null || riskControlMap.isEmpty()) {
                log.debug("风控配置为空，跳过风控参数保存");
                return;
            }

            // 转换为 StrategyParameter 列表
            List<StrategyParameter> parameters = new ArrayList<>();
            int displayOrder = 0;

            // 1. 仓位控制参数
            // 最大仓位
            Object maxPosition = riskControlMap.get("maxPosition");
            if (maxPosition != null) {
                StrategyParameter param = new StrategyParameter();
                param.setName("maxPosition");
                param.setDisplayName("最大仓位");
                param.setDescription("单个策略的最大持仓比例（%）");
                param.setGroupName("risk_control");
                param.setDataType("number");
                param.setParamType("number");
                param.setDefaultValue(maxPosition.toString());
                param.setMinValue("0");
                param.setMaxValue("100");
                param.setIsRequired(false);
                param.setIsVisible(true);
                param.setIsAdvanced(false);
                param.setIsArray(false);
                param.setDisplayOrder(displayOrder++);
                parameters.add(param);
            }
            
            // 单笔最大仓位
            Object maxPositionPerTrade = riskControlMap.get("maxPositionPerTrade");
            if (maxPositionPerTrade != null) {
                StrategyParameter param = new StrategyParameter();
                param.setName("maxPositionPerTrade");
                param.setDisplayName("单笔最大仓位");
                param.setDescription("单笔交易的最大仓位比例（%）");
                param.setGroupName("risk_control");
                param.setDataType("number");
                param.setParamType("number");
                param.setDefaultValue(maxPositionPerTrade.toString());
                param.setMinValue("0");
                param.setMaxValue("100");
                param.setIsRequired(false);
                param.setIsVisible(true);
                param.setIsAdvanced(false);
                param.setIsArray(false);
                param.setDisplayOrder(displayOrder++);
                parameters.add(param);
            }
            
            // 单笔最小仓位
            Object minPosition = riskControlMap.get("minPosition");
            if (minPosition != null) {
                StrategyParameter param = new StrategyParameter();
                param.setName("minPosition");
                param.setDisplayName("单笔最小仓位");
                param.setDescription("单笔交易的最小仓位比例（%），低于此值不开仓");
                param.setGroupName("risk_control");
                param.setDataType("number");
                param.setParamType("number");
                param.setDefaultValue(minPosition.toString());
                param.setMinValue("0");
                param.setMaxValue("100");
                param.setIsRequired(false);
                param.setIsVisible(true);
                param.setIsAdvanced(false);
                param.setIsArray(false);
                param.setDisplayOrder(displayOrder++);
                parameters.add(param);
            }
            
            // 启用仓位管理
            Object positionManagementEnabled = riskControlMap.get("positionManagementEnabled");
            if (positionManagementEnabled != null) {
                StrategyParameter param = new StrategyParameter();
                param.setName("positionManagementEnabled");
                param.setDisplayName("启用仓位管理");
                param.setDescription("启用后，系统会根据市场情况自动调整仓位");
                param.setGroupName("risk_control");
                param.setDataType("boolean");
                param.setParamType("boolean");
                param.setDefaultValue(positionManagementEnabled.toString());
                param.setIsRequired(false);
                param.setIsVisible(true);
                param.setIsAdvanced(false);
                param.setIsArray(false);
                param.setDisplayOrder(displayOrder++);
                parameters.add(param);
            }

            // 2. 止损参数
            Object stopLossObj = riskControlMap.get("stopLoss");
            if (stopLossObj instanceof Map) {
                Map<String, Object> stopLoss = (Map<String, Object>) stopLossObj;
                
                // 止损类型
                Object stopLossType = stopLoss.get("type");
                if (stopLossType != null) {
                    StrategyParameter param = new StrategyParameter();
                    param.setName("stopLossType");
                    param.setDisplayName("止损类型");
                    param.setDescription("止损方式：fixed_percent(固定百分比)、fixed_percent_trailing(固定百分比移动)、atr_based(ATR固定)、atr_trailing(ATR移动)");
                    param.setGroupName("risk_control");
                    param.setDataType("string");
                    param.setParamType("string");
                    param.setDefaultValue(stopLossType.toString());
                    param.setIsRequired(true);
                    param.setIsVisible(true);
                    param.setIsAdvanced(false);
                    param.setIsArray(false);
                    param.setDisplayOrder(displayOrder++);
                    parameters.add(param);
                }

                // 根据止损类型保存相应参数
                String stopLossTypeStr = stopLossType != null ? stopLossType.toString() : "";
                
                if ("fixed_percent".equals(stopLossTypeStr) || "fixed_percent_trailing".equals(stopLossTypeStr)) {
                    // 止损百分比
                    Object percent = stopLoss.get("percent");
                    if (percent != null) {
                        StrategyParameter param = new StrategyParameter();
                        param.setName("stopLossPercent");
                        param.setDisplayName("止损百分比");
                        param.setDescription("止损百分比（%）");
                        param.setGroupName("risk_control");
                        param.setDataType("number");
                        param.setParamType("number");
                        param.setDefaultValue(percent.toString());
                        param.setMinValue("0.1");
                        param.setMaxValue("10");
                        param.setIsRequired(true);
                        param.setIsVisible(true);
                        param.setIsAdvanced(false);
                        param.setIsArray(false);
                        param.setDisplayOrder(displayOrder++);
                        parameters.add(param);
                    }
                    
                    // 回溯K线数量（仅移动止损）
                    if ("fixed_percent_trailing".equals(stopLossTypeStr)) {
                        Object barCount = stopLoss.get("barCount");
                        if (barCount != null) {
                            StrategyParameter param = new StrategyParameter();
                            param.setName("stopLossBarCount");
                            param.setDisplayName("回溯K线数量");
                            param.setDescription("计算最高/最低价时回溯的K线数量，null表示无限制");
                            param.setGroupName("risk_control");
                            param.setDataType("number");
                            param.setParamType("number");
                            param.setDefaultValue(barCount.toString());
                            param.setIsRequired(false);
                            param.setIsVisible(true);
                            param.setIsAdvanced(false);
                            param.setIsArray(false);
                            param.setDisplayOrder(displayOrder++);
                            parameters.add(param);
                        }
                    }
                } else if ("atr_based".equals(stopLossTypeStr) || "atr_trailing".equals(stopLossTypeStr)) {
                    // ATR 倍数
                    Object atrMultiplier = stopLoss.get("atrMultiplier");
                    if (atrMultiplier != null) {
                        StrategyParameter param = new StrategyParameter();
                        param.setName("stopLossAtrMultiplier");
                        param.setDisplayName("止损ATR倍数");
                        param.setDescription("止损距离 = ATR × 倍数");
                        param.setGroupName("risk_control");
                        param.setDataType("number");
                        param.setParamType("number");
                        param.setDefaultValue(atrMultiplier.toString());
                        param.setMinValue("1.0");
                        param.setMaxValue("5.0");
                        param.setIsRequired(true);
                        param.setIsVisible(true);
                        param.setIsAdvanced(false);
                        param.setIsArray(false);
                        param.setDisplayOrder(displayOrder++);
                        parameters.add(param);
                    }
                    
                    // ATR 计算周期
                    Object atrPeriod = stopLoss.get("atrPeriod");
                    if (atrPeriod != null) {
                        StrategyParameter param = new StrategyParameter();
                        param.setName("stopLossAtrPeriod");
                        param.setDisplayName("止损ATR周期");
                        param.setDescription("计算ATR指标时使用的周期（根K线）");
                        param.setGroupName("risk_control");
                        param.setDataType("number");
                        param.setParamType("number");
                        param.setDefaultValue(atrPeriod.toString());
                        param.setMinValue("5");
                        param.setMaxValue("50");
                        param.setIsRequired(true);
                        param.setIsVisible(true);
                        param.setIsAdvanced(false);
                        param.setIsArray(false);
                        param.setDisplayOrder(displayOrder++);
                        parameters.add(param);
                    }
                }
            }

            // 3. 止盈参数
            Object takeProfitObj = riskControlMap.get("takeProfit");
            if (takeProfitObj instanceof Map) {
                Map<String, Object> takeProfit = (Map<String, Object>) takeProfitObj;
                
                // 是否启用止盈
                Object enabled = takeProfit.get("enabled");
                if (enabled != null) {
                    StrategyParameter param = new StrategyParameter();
                    param.setName("takeProfitEnabled");
                    param.setDisplayName("启用止盈");
                    param.setDescription("是否启用止盈功能");
                    param.setGroupName("risk_control");
                    param.setDataType("boolean");
                    param.setParamType("boolean");
                    param.setDefaultValue(enabled.toString());
                    param.setIsRequired(false);
                    param.setIsVisible(true);
                    param.setIsAdvanced(false);
                    param.setIsArray(false);
                    param.setDisplayOrder(displayOrder++);
                    parameters.add(param);
                }

                // 如果启用止盈，保存止盈类型和相关参数
                if (Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled))) {
                    // 止盈类型
                    Object takeProfitType = takeProfit.get("type");
                    if (takeProfitType != null) {
                        StrategyParameter param = new StrategyParameter();
                        param.setName("takeProfitType");
                        param.setDisplayName("止盈类型");
                        param.setDescription("止盈方式：fixed_percent(固定百分比)、atr_based(ATR固定)");
                        param.setGroupName("risk_control");
                        param.setDataType("string");
                        param.setParamType("string");
                        param.setDefaultValue(takeProfitType.toString());
                        param.setIsRequired(true);
                        param.setIsVisible(true);
                        param.setIsAdvanced(false);
                        param.setIsArray(false);
                        param.setDisplayOrder(displayOrder++);
                        parameters.add(param);
                    }

                    String takeProfitTypeStr = takeProfitType != null ? takeProfitType.toString() : "";
                    
                    if ("fixed_percent".equals(takeProfitTypeStr)) {
                        // 止盈百分比
                        Object percent = takeProfit.get("percent");
                        if (percent != null) {
                            StrategyParameter param = new StrategyParameter();
                            param.setName("takeProfitPercent");
                            param.setDisplayName("止盈百分比");
                            param.setDescription("止盈百分比（%）");
                            param.setGroupName("risk_control");
                            param.setDataType("number");
                            param.setParamType("number");
                            param.setDefaultValue(percent.toString());
                            param.setMinValue("0.1");
                            param.setMaxValue("50");
                            param.setIsRequired(true);
                            param.setIsVisible(true);
                            param.setIsAdvanced(false);
                            param.setIsArray(false);
                            param.setDisplayOrder(displayOrder++);
                            parameters.add(param);
                        }
                    } else if ("atr_based".equals(takeProfitTypeStr)) {
                        // ATR 倍数
                        Object atrMultiplier = takeProfit.get("atrMultiplier");
                        if (atrMultiplier != null) {
                            StrategyParameter param = new StrategyParameter();
                            param.setName("takeProfitAtrMultiplier");
                            param.setDisplayName("止盈ATR倍数");
                            param.setDescription("止盈距离 = ATR × 倍数");
                            param.setGroupName("risk_control");
                            param.setDataType("number");
                            param.setParamType("number");
                            param.setDefaultValue(atrMultiplier.toString());
                            param.setMinValue("1.0");
                            param.setMaxValue("10.0");
                            param.setIsRequired(true);
                            param.setIsVisible(true);
                            param.setIsAdvanced(false);
                            param.setIsArray(false);
                            param.setDisplayOrder(displayOrder++);
                            parameters.add(param);
                        }
                        
                        // ATR 计算周期
                        Object atrPeriod = takeProfit.get("atrPeriod");
                        if (atrPeriod != null) {
                            StrategyParameter param = new StrategyParameter();
                            param.setName("takeProfitAtrPeriod");
                            param.setDisplayName("止盈ATR周期");
                            param.setDescription("计算ATR指标时使用的周期（根K线）");
                            param.setGroupName("risk_control");
                            param.setDataType("number");
                            param.setParamType("number");
                            param.setDefaultValue(atrPeriod.toString());
                            param.setMinValue("5");
                            param.setMaxValue("50");
                            param.setIsRequired(true);
                            param.setIsVisible(true);
                            param.setIsAdvanced(false);
                            param.setIsArray(false);
                            param.setDisplayOrder(displayOrder++);
                            parameters.add(param);
                        }
                    }
                }
            }

            // 保存风控参数（先删除该策略的风控参数，再保存新的）
            if (!parameters.isEmpty()) {
                // 删除该策略的风控参数
                LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(StrategyParameter::getStrategyId, strategyId);
                wrapper.eq(StrategyParameter::getGroupName, "risk_control");
                strategyParameterService.remove(wrapper);
                
                // 设置策略ID和时间戳
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                for (StrategyParameter param : parameters) {
                    param.setStrategyId(strategyId);
                    param.setParamId(strategyParameterService.generateParamId());
                    param.setCreatedAt(now);
                    param.setUpdatedAt(now);
                }
                
                // 批量保存
                strategyParameterService.saveBatch(parameters);
                log.info("风控参数保存成功: strategyId={}, count={}", strategyId, parameters.size());
            } else {
                log.debug("没有有效的风控参数需要保存");
            }

        } catch (Exception e) {
            log.error("保存风控参数失败: strategyId={}", strategyId, e);
            // 不抛出异常，避免影响策略本身的保存
        }
    }

    /** 加仓配置参数 -> StrategyParameter 表，groupName = add_position_config */
    private void savePositionRiskParameters(String strategyId, Map<String, Object> requestBody) {
        Object obj = requestBody.get("positionRisk");
        if (obj == null) return;
        Map<String, Object> map = parseJsonToMap(obj);
        if (map == null || map.isEmpty()) return;
        try {
            // 加仓配置 → add_position_config 组（原有），只提取加仓相关字段
            Map<String, Object> addPosMap = new HashMap<>();
            copyIfPresent(map, addPosMap, "allowAddPosition");
            copyIfPresent(map, addPosMap, "addPosOnLossPct");
            copyIfPresent(map, addPosMap, "addPosOnProfitPct");
            copyIfPresent(map, addPosMap, "addPosOnProfitGapPct");
            copyIfPresent(map, addPosMap, "addPosOnLossGapPct");
            copyIfPresent(map, addPosMap, "addPositionCount");
            if (!addPosMap.isEmpty()) {
                String addPosJson = objectMapper.writeValueAsString(addPosMap);
                StrategyParameter addPosParam = new StrategyParameter();
                addPosParam.setName("config");
                addPosParam.setDisplayName("加仓配置");
                addPosParam.setGroupName("add_position_config");
                addPosParam.setDataType("string");
                addPosParam.setParamType("string");
                addPosParam.setDefaultValue(addPosJson);
                addPosParam.setDisplayOrder(0);
                saveParameterGroup(strategyId, "add_position_config", Collections.singletonList(addPosParam));
                log.info("add_position_config/config 保存成功: strategyId={}", strategyId);
            }
            // 仓位控制配置 → position_risk 组（新增），只提取仓位控制相关字段
            Map<String, Object> posRiskMap = new HashMap<>();
            copyIfPresent(map, posRiskMap, "maxPosition");
            copyIfPresent(map, posRiskMap, "positionManagementEnabled");
            copyIfPresent(map, posRiskMap, "positionMode");
            copyIfPresent(map, posRiskMap, "basePositionPct");
            copyIfPresent(map, posRiskMap, "singleTradeRiskPct");
            copyIfPresent(map, posRiskMap, "maxPositionPerTrade");
            copyIfPresent(map, posRiskMap, "minPosition");
            copyIfPresent(map, posRiskMap, "signalFrequencyEnabled");
            copyIfPresent(map, posRiskMap, "signalFrequencyGranularity");
            copyIfPresent(map, posRiskMap, "signalFrequencyMode");
            if (!posRiskMap.isEmpty()) {
                String posRiskJson = objectMapper.writeValueAsString(posRiskMap);
                StrategyParameter posRiskParam = new StrategyParameter();
                posRiskParam.setName("config");
                posRiskParam.setDisplayName("仓位控制配置");
                posRiskParam.setGroupName("position_risk");
                posRiskParam.setDataType("string");
                posRiskParam.setParamType("string");
                posRiskParam.setDefaultValue(posRiskJson);
                posRiskParam.setDisplayOrder(0);
                saveParameterGroup(strategyId, "position_risk", Collections.singletonList(posRiskParam));
                log.info("position_risk/config 保存成功: strategyId={}", strategyId);
            }
        } catch (Exception e) {
            log.warn("保存 positionRisk 配置失败: {}", e.getMessage());
        }
    }

    private void copyIfPresent(Map<String, Object> src, Map<String, Object> dest, String key) {
        if (src.containsKey(key)) {
            dest.put(key, src.get(key));
        }
    }

    /** 出场规则（止损/止盈/分批止盈）-> StrategyParameter 表，groupName = exit_rules_config */
    private void saveExitRulesParameters(String strategyId, Map<String, Object> requestBody) {
        Object obj = requestBody.get("exitRules");
        if (obj == null) return;
        Map<String, Object> map = parseJsonToMap(obj);
        if (map == null || map.isEmpty()) return;
        try {
            // 直接保存原始 JSON，保持与前端一致的 key 结构（takeProfit.percent 等）
            String rawJson = objectMapper.writeValueAsString(map);
            StrategyParameter param = new StrategyParameter();
            param.setName("config");
            param.setDisplayName("出场规则配置");
            param.setGroupName("exit_rules_config");
            param.setDataType("string");
            param.setParamType("string");
            param.setDefaultValue(rawJson);
            param.setDisplayOrder(0);
            saveParameterGroup(strategyId, "exit_rules_config", Collections.singletonList(param));
            log.info("exit_rules_config/config 保存成功: strategyId={}", strategyId);
        } catch (Exception e) {
            log.warn("保存 exit_rules_config/config 失败: {}", e.getMessage());
        }
    }

    /** 动态风控引擎（移动止损/移动止盈）-> StrategyParameter 表，groupName = dynamic_risk_engine */
    private void saveDynamicRiskEngineParameters(String strategyId, Map<String, Object> requestBody) {
        Object obj = requestBody.get("dynamicRiskEngine");
        if (obj == null) return;
        Map<String, Object> map = parseJsonToMap(obj);
        if (map == null || map.isEmpty()) return;
        try {
            String rawJson = objectMapper.writeValueAsString(map);
            StrategyParameter param = new StrategyParameter();
            param.setName("config");
            param.setDisplayName("动态风控引擎配置");
            param.setGroupName("dynamic_risk_engine");
            param.setDataType("string");
            param.setParamType("string");
            param.setDefaultValue(rawJson);
            param.setDisplayOrder(0);
            saveParameterGroup(strategyId, "dynamic_risk_engine", Collections.singletonList(param));
            log.info("dynamic_risk_engine/config 保存成功: strategyId={}", strategyId);
        } catch (Exception e) {
            log.warn("保存 dynamic_risk_engine/config 失败: {}", e.getMessage());
        }
    }

    /** 从 add_position_config + position_risk 两组合并还原仓位风控配置 */
    private Map<String, Object> buildPositionRiskResponse(String strategyId) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> addPos = loadParameterGroupMap(strategyId, "add_position_config", "config");
        if (addPos != null) result.putAll(addPos);
        Map<String, Object> posRisk = loadParameterGroupMap(strategyId, "position_risk", "config");
        if (posRisk != null) result.putAll(posRisk);
        return result;
    }

    /** 从 StrategyParameter 各组拼装 riskControl 对象（供前端回显，替代已删除的 Strategy.risk_control 列） */
    private Map<String, Object> buildRiskControlResponse(String strategyId) {
        Map<String, Object> result = new HashMap<>();
        result.putAll(buildPositionRiskResponse(strategyId));
        Map<String, Object> exitRules = loadParameterGroupMap(strategyId, "exit_rules_config", "config");
        if (exitRules != null) result.putAll(exitRules);
        return result;
    }

    private Map<String, Object> parseJsonToMap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) return (Map<String, Object>) obj;
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            if (s.isEmpty() || "null".equals(s)) return null;
            try {
                return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.debug("parseJsonToMap 失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    private void saveParameterGroup(String strategyId, String groupName, List<StrategyParameter> parameters) {
        if (parameters.isEmpty()) return;
        try {
            Set<String> names = parameters.stream()
                    .map(StrategyParameter::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!names.isEmpty()) {
                LambdaQueryWrapper<StrategyParameter> dupWrapper = new LambdaQueryWrapper<>();
                dupWrapper.eq(StrategyParameter::getStrategyId, strategyId);
                dupWrapper.eq(StrategyParameter::getGroupName, groupName);
                dupWrapper.in(StrategyParameter::getName, names);
                strategyParameterService.remove(dupWrapper);
            }
            LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StrategyParameter::getStrategyId, strategyId);
            wrapper.eq(StrategyParameter::getGroupName, groupName);
            strategyParameterService.remove(wrapper);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            for (StrategyParameter p : parameters) {
                p.setStrategyId(strategyId);
                p.setParamId(strategyParameterService.generateParamId());
                p.setCreatedAt(now);
                p.setUpdatedAt(now);
                p.setIsRequired(p.getIsRequired() != null && p.getIsRequired());
                p.setIsVisible(p.getIsVisible() == null || p.getIsVisible());
                p.setIsAdvanced(p.getIsAdvanced() != null && p.getIsAdvanced());
                p.setIsArray(p.getIsArray() != null && p.getIsArray());
            }
            strategyParameterService.saveBatch(parameters);
            log.info("策略参数组保存成功: strategyId={}, group={}, count={}", strategyId, groupName, parameters.size());
        } catch (Exception e) {
            log.error("保存策略参数组失败: strategyId={}, group={}", strategyId, groupName, e);
        }
    }

    private Map<String, Object> loadParameterGroupMap(String strategyId, String groupName) {
        try {
            LambdaQueryWrapper<StrategyParameter> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StrategyParameter::getStrategyId, strategyId);
            wrapper.eq(StrategyParameter::getGroupName, groupName);
            List<StrategyParameter> params = strategyParameterService.list(wrapper);
            if (params == null || params.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, Object> map = new HashMap<>();
            for (StrategyParameter p : params) {
                String name = p.getName();
                String val = p.getDefaultValue();
                String type = p.getDataType();
                if (name == null) continue;
                if (val == null) {
                    map.put(name, null);
                    continue;
                }
                Object parsed = val;
                if ("boolean".equalsIgnoreCase(type)) {
                    parsed = "true".equalsIgnoreCase(val);
                } else if ("number".equalsIgnoreCase(type)) {
                    try {
                        if (val.contains(".")) {
                            parsed = Double.parseDouble(val);
                        } else {
                            parsed = Long.parseLong(val);
                        }
                    } catch (Exception ignored) {
                        parsed = val;
                    }
                } else if ("string".equalsIgnoreCase(type)) {
                    parsed = val;
                }
                map.put(name, parsed);
            }
            return map;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** 加载单条 JSON 参数组（groupName + paramName），将其 defaultValue 解析为 Map */
    private Map<String, Object> loadParameterGroupMap(String strategyId, String groupName, String paramName) {
        try {
            String json = strategyParameterService.getParameterValue(strategyId, groupName, paramName);
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("loadParameterGroupMap({}, {}, {}) 失败: {}", strategyId, groupName, paramName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 复制策略
     *
     * @param id 策略ID（strategy_id或id）
     * @return 复制后的策略
     */
    @PostMapping("/strategies/{id}/duplicate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> duplicateStrategy(@PathVariable String id) {
        log.info("收到复制策略请求: id={}", id);

        try {
            Strategy sourceStrategy;

            sourceStrategy = strategyService.getByStrategyId(id);
            if (sourceStrategy == null) {
                try {
                    Long longId = Long.parseLong(id);
                    sourceStrategy = strategyService.getById(longId);
                } catch (NumberFormatException e) {
                }
            }

            if (sourceStrategy == null) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, "策略不存在"));
            }

            Strategy newStrategy = new Strategy();
            newStrategy.setStrategyId(strategyService.generateStrategyId());
            String newName = sourceStrategy.getName();
            if (newName != null) {
                newName = newName + " - 副本";
            }
            newStrategy.setName(newName);
            newStrategy.setDisplayName(sourceStrategy.getDisplayName());
            newStrategy.setDescription(sourceStrategy.getDescription());
            newStrategy.setBriefDescription(sourceStrategy.getBriefDescription());
            newStrategy.setStrategyType(sourceStrategy.getStrategyType());
            newStrategy.setCategoryId(sourceStrategy.getCategoryId());
            newStrategy.setSubCategory(sourceStrategy.getSubCategory());
            newStrategy.setTags(sourceStrategy.getTags());
            newStrategy.setCodeContent(sourceStrategy.getCodeContent());
            newStrategy.setClassName(sourceStrategy.getClassName());
            newStrategy.setFilePath(sourceStrategy.getFilePath());
            newStrategy.setEntryPoint(sourceStrategy.getEntryPoint());
            newStrategy.setDefaultParameters(sourceStrategy.getDefaultParameters());
            newStrategy.setParameterSchema(sourceStrategy.getParameterSchema());
            newStrategy.setDependencies(sourceStrategy.getDependencies());
            newStrategy.setAutoSignal(sourceStrategy.getAutoSignal());
            newStrategy.setStatus("draft");
            newStrategy.setVisibility(sourceStrategy.getVisibility());
            newStrategy.setIsSystem(false);
            newStrategy.setIsTemplate(false);
            newStrategy.setFrequency(sourceStrategy.getFrequency());
            newStrategy.setMarketType(sourceStrategy.getMarketType());
            newStrategy.setTimeFrame(sourceStrategy.getTimeFrame());
            newStrategy.setSupportsLong(sourceStrategy.getSupportsLong());
            newStrategy.setSupportsShort(sourceStrategy.getSupportsShort());
            newStrategy.setSupportsLeverage(sourceStrategy.getSupportsLeverage());
            newStrategy.setMaxPositionCount(sourceStrategy.getMaxPositionCount());
            newStrategy.setMinCapital(sourceStrategy.getMinCapital());
            newStrategy.setOwnerId(sourceStrategy.getOwnerId());
            newStrategy.setOwnerName(sourceStrategy.getOwnerName());
            newStrategy.setTeamId(sourceStrategy.getTeamId());
            newStrategy.setCreatedBy(sourceStrategy.getCreatedBy());
            newStrategy.setCreatedByName(sourceStrategy.getCreatedByName());

            boolean success = strategyService.save(newStrategy);

            if (!success) {
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("复制失败"));
            }

            Map<String, Object> strategyMap = convertStrategyToMap(newStrategy);

            return ResponseEntity.ok(ApiResponse.success("复制成功", strategyMap));

        } catch (Exception e) {
            log.error("复制策略失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("复制失败: " + e.getMessage()));
        }
    }

    @PostMapping("/strategy/ai-generate")
    public ResponseEntity<ApiResponse<AiStrategyRecommendation>> aiGenerate(@RequestBody @Valid AiGenerateRequest request) {
        log.info("收到AI策略生成请求: prompt={}, marketType={}", request.getPrompt(), request.getMarketType());
        ApiResponse<AiStrategyRecommendation> result = aiStrategyService.generate(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/strategy/ai-confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiConfirm(@RequestBody @Valid AiConfirmRequest request) {
        log.info("收到AI确认创建请求: botName={}", request.getBotName());
        ApiResponse<Map<String, Object>> result = aiStrategyService.confirm(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 从 "U000000000000001" 格式的用户ID中提取数字部分
     */
    private static Long parseUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0L;
        }
        // 去掉 U 前缀，提取数字
        String numeric = userId.startsWith("U") ? userId.substring(1) : userId;
        return Long.parseLong(numeric);
    }
}
