@echo off
echo ========================================
echo 启动 AI Task 应用
echo ========================================
cd /d %~dp0
mvn spring-boot:run
pause

