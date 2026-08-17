@echo off
echo ========================================
echo       AI量化交易平台 - 项目概览
echo ========================================
echo.
echo 项目根目录: %~dp0
echo.
echo 项目结构:
echo ├── frontend/          Vue2前端项目 (端口:8080)
echo ├── ai-frontend-web/   Vue3前端项目 (端口:3000) ⭐️
echo ├── ai-quant/          量化分析平台 (端口:8081)
echo ├── ai-engine/         AI引擎模块 (端口:8082)
echo ├── ai-order/          订单库模块 (无独立端口, 由 ai-quant 加载)
echo ├── ai-signal/         信号处理模块
echo ├── ai-risk/           风险控制模块
echo ├── ai-data/           数据处理模块
echo ├── ai-common/         公共工具模块
echo └── ai-extension/      扩展功能模块
echo.
echo 技术栈:
echo 前端: Vue 2.6.14 + Element UI + ECharts
echo 后端: Spring Boot + Java 21 + MySQL + MongoDB
echo 交易: Ta4j 0.22.0
echo.
echo 快速启动:
echo 1. 启动前端: cd frontend && start-dev.bat
echo 2. 启动后端: start-all-services.bat
echo.
echo 访问地址:
echo 前端界面: http://localhost:8080
echo 后端API:  ai-quant http://localhost:8081  ^|  ai-task (XXL-JOB)
echo.
echo 按任意键继续...
pause >nul
