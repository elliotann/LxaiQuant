/**
 * AI / ML 领域库模块根包。
 *
 * <p>目标子包（代码自 {@code ai-quant} 的 {@code engine.service.ml} 等迁入）：
 * <ul>
 *   <li>{@code factor} — 遗传规划、表达式树</li>
 *   <li>{@code training} — 模型训练</li>
 *   <li>{@code inference} — 推理与预测结果</li>
 *   <li>{@code search} — 自动搜参</li>
 *   <li>{@code storage} — 模型存储</li>
 *   <li>{@code config} — ML 配置</li>
 *   <li>{@code mapper} — 因子/搜参持久化</li>
 * </ul>
 *
 * <p>本模块为纯库：不包含 {@code @RestController}，不提供 {@code SpringApplication} 入口。
 */
package com.chain.ai.trade.ml;
