@echo off
echo ========================================
echo   Fix Android Network Warning
echo ========================================
echo.

adb devices | findstr /r "device$" >nul
if %errorlevel% neq 0 (
    echo [ERROR] No device found. Check USB debugging is on.
    pause
    exit /b 1
)

echo [1/3] Setting HTTP portal URL...
adb shell settings put global captive_portal_http_url http://connect.rom.miui.com/generate_204

echo [2/3] Setting HTTPS portal URL...
adb shell settings put global captive_portal_https_url https://connect.rom.miui.com/generate_204

echo [3/3] Setting portal mode...
adb shell settings put global captive_portal_mode 1

echo.
echo ========================================
echo   Done! Reboot or toggle airplane mode
echo ========================================
pause
