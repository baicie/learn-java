@echo off
setlocal enabledelayedexpansion

echo ==^> Frontend CI

set "FRONTEND_DIR=web\console"

if not exist "%FRONTEND_DIR%\package.json" (
    echo Skip frontend: %FRONTEND_DIR%\package.json not found.
    exit /b 0
)

where pnpm >nul 2>&1
if errorlevel 1 (
    echo ERROR: pnpm is not found in PATH. Run: corepack enable
    exit /b 1
)

pushd "%FRONTEND_DIR%"
call pnpm install

call :run_if "format:check"
call :run_if "lint"
call :run_if "typecheck"
call :run_if "test"
call :run_if "build"

popd

endlocal
exit /b 0

:run_if
node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts['%~1'] ? 0 : 1)" >nul 2>&1
if errorlevel 1 (
    echo Skip %~1: script not found.
    exit /b 0
)
call pnpm run %~1
exit /b %errorlevel%
