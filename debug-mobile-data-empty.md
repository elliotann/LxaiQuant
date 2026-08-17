# Debug Session: mobile-data-empty

**Status**: [OPEN]
**Session ID**: mobile-data-empty
**Date**: 2026-06-21

## Symptom
用户在手机上打开 H5 移动端首页，数据不显示（空白/无数据）。

## Hypotheses

| # | Hypothesis | How to Falsify | Status |
|---|-----------|----------------|--------|
| 1 | 后端 API 返回错误（如 401/403/500），导致 `Promise.allSettled` 中所有请求失败，store 数据为空 | 在 `loadData()` 中捕获并上报每个 API 的 HTTP 状态码和响应 | Pending |
| 2 | 手机端 Token 过期或未携带，后端返回 401，API 全部失败 | 上报 `Authorization` header 是否存在及 Token 前缀 | Pending |
| 3 | `getBaseUrl()` 在手机端指向了错误的服务器地址（如 `localhost` 或不可达的 URL） | 上报 `getBaseUrl()` 解析出的实际 baseURL | Pending |
| 4 | 近期 TODO 注释掉的 API（getPrices, unreadCount）导致页面依赖的数据源缺失或 UI 状态异常 | 检查 `notificationStore.unreadCount` 和 `priceMap` 的状态 | Pending |
| 5 | JavaScript 运行时异常导致整个 `loadData()` 或模板渲染中断 | 上报 `loadData()` 的进入/退出标记，以及 catch 中的完整 error 对象 | Pending |

## Evidence Log

| Time | Event | Data |
|------|-------|------|
| - | - | - |

## Root Cause

TBD

## Fix

TBD
