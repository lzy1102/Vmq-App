@echo off
echo ========================================
echo   Restore Android Default Network Check
echo ========================================
echo.

adb devices | findstr /r "device$" >nul
if %errorlevel% neq 0 (
    echo [ERROR] No device found. Check USB debugging is on.
    pause
    exit /b 1
)

echo [1/3] Deleting HTTP portal URL...
adb shell settings delete global captive_portal_http_url

echo [2/3] Deleting HTTPS portal URL...
adb shell settings delete global captive_portal_https_url

echo [3/3] Deleting portal mode...
adb shell settings delete global captive_portal_mode

echo.
echo ========================================
echo   Done! Reboot or toggle airplane mode
echo ========================================
pause
