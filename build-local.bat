@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo === CyberServer local build (no GitHub Actions) ===
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-local.ps1"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
    echo.
    echo Build failed. Please share build-local.log.
) else (
    echo.
    echo CyberServer.jar is ready for testing.
)
pause
exit /b %RESULT%
