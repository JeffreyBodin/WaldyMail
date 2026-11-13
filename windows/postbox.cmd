@echo off
setlocal
set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=Info"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-postbox.ps1" -Action %ACTION%
exit /b %ERRORLEVEL%
