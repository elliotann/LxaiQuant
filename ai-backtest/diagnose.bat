@echo off
echo ========================================
echo AI Backtest Module 诊断工具
echo ========================================

echo.
echo [1/5] 检查Java环境...
java -version 2>&1 | findstr /C:"21" >nul
if %errorlevel% neq 0 (
    echo ❌ Java 21 未找到，请安装 JDK 21+
    goto :error
) else (
    echo ✅ Java 环境正常
)

echo.
echo [2/5] 检查项目结构...
if exist "src\main\java\com\chain\ai\trade\backtest\service\BacktestTaskService.java" (
    echo ✅ 项目结构完整
) else (
    echo ❌ 项目结构不完整，缺少核心文件
    goto :error
)

echo.
echo [3/5] 检查配置文件...
if exist "src\main\resources\application.yml" (
    echo ✅ 配置文件存在
    findstr "backtest" "src\main\resources\application.yml" >nul
    if %errorlevel% neq 0 (
        echo ⚠️ 配置文件可能缺少数据库配置
    )
) else (
    echo ❌ 配置文件不存在
    goto :error
)

echo.
echo [4/5] 检查数据库脚本...
if exist "src\main\resources\db\init.sql" (
    echo ✅ 数据库初始化脚本存在
) else (
    echo ❌ 数据库初始化脚本不存在
    goto :error
)

echo.
echo [5/5] 检查依赖配置...
if exist "pom.xml" (
    echo ✅ POM文件存在
    findstr "ai-common" "pom.xml" >nul
    if %errorlevel% neq 0 (
        echo ⚠️ POM文件可能缺少必要依赖
    ) else (
        echo ✅ 依赖配置正常
    )
) else (
    echo ❌ POM文件不存在
    goto :error
)

echo.
echo ========================================
echo 🎉 诊断完成！所有检查通过
echo ========================================
echo.
echo 下一步操作：
echo 1. 确保数据库已创建并运行
echo 2. 执行数据库初始化脚本
echo 3. 在其他模块中依赖此JAR包
echo.
goto :end

:error
echo.
echo ========================================
echo ❌ 诊断失败，请修复上述问题
echo ========================================
echo.

:end
pause
