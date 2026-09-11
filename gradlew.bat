@echo off
setlocal

if not "%GRADLE_BIN%"=="" (
  "%GRADLE_BIN%" %*
  exit /b %ERRORLEVEL%
)

where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)

echo Gradle 8.10.2 is not installed. Open this project in Android Studio or install Gradle 8.10.2, then run: gradle %*
exit /b 1
