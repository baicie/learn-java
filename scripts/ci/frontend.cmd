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
call pnpm run typecheck
call pnpm run build
popd

endlocal
