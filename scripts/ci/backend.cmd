@echo off
setlocal enabledelayedexpansion

echo ==^> Backend CI

if not exist "pom.xml" (
    echo Skip backend: pom.xml not found.
    exit /b 0
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven is not found in PATH.
    exit /b 1
)

echo Using Maven: mvn

call mvn -B -ntp ^
  -DskipITs=true ^
  -DskipE2E=true ^
  -Dspotless.check.skip=true ^
  verify

endlocal
