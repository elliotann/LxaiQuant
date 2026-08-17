@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "OPENCLAW_STATE_DIR=%ROOT%\.openclaw-state"
set "OPENCLAW_CONFIG_PATH=%OPENCLAW_STATE_DIR%\openclaw.json"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%start-openclaw.ps1" %*
