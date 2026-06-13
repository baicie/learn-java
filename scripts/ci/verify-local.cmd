@echo off
setlocal enabledelayedexpansion

echo ==^> Local verification
echo.

call scripts\ci\docs.cmd
echo.

call scripts\ci\backend.cmd
echo.

call scripts\ci\frontend.cmd
echo.

echo ==^> Local verification passed.
endlocal
