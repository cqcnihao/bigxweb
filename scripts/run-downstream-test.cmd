@echo off
rem ============================================================
rem  Downstream gateway-validation test (double-click to run)
rem  Usage:
rem    run-downstream-test.cmd        -> full mode (includes login)
rem    run-downstream-test.cmd skip   -> skip-login mode
rem  Exit code: 0 = all passed, 1 = some failed
rem ============================================================
setlocal

cd /d "%~dp0.."

set MODE=
if /i "%~1"=="skip" set MODE=-SkipLogin

powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\test-downstream-validation.ps1" %MODE%
set RC=%ERRORLEVEL%

echo.
if %RC%==0 (
    echo [OK] All cases passed
) else (
    echo [FAIL] Some cases failed, see output above
)

rem Pause only when double-clicked (cmd /c), not when called from a shell
echo %cmdcmdline% | find /i "cmd /c" >nul
if %errorlevel%==0 pause

endlocal & exit /b %RC%
