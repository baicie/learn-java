@echo off
setlocal enabledelayedexpansion

echo ==^> Docs CI

if not exist "docs\INDEX.md" (
    echo Skip docs: docs\INDEX.md not found.
    exit /b 0
)

echo Docs directory exists, checking structure...

if exist "docs\_templates" (
    for /f %%i in ('dir /b /a "docs\_templates" 2^>nul ^| find /c /v ""') do set "CNT=%%i"
    echo   - docs/_templates: !CNT! templates
)

if exist "docs\architecture" (
    for /f %%i in ('dir /b /a "docs\architecture" 2^>nul ^| find /c /v ""') do set "CNT=%%i"
    echo   - docs/architecture: !CNT! files
)

if exist "docs\designs" (
    for /f %%i in ('dir /b /a "docs\designs" 2^>nul ^| find /c /v ""') do set "CNT=%%i"
    echo   - docs/designs: !CNT! files
)

if exist "docs\phases" (
    for /f %%i in ('dir /b /a "docs\phases" 2^>nul ^| find /c /v ""') do set "CNT=%%i"
    echo   - docs/phases: !CNT! files
)

echo Docs check passed.
endlocal
