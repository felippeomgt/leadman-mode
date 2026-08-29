@echo off
REM One-shot setup for a fresh Windows PC: downloads JDK 11 into .tools\ and starts Leadman.
REM Double-click this file, or from cmd: setup-windows.cmd
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo.
echo  Leadman Mode - setup and launch
echo  ===============================
echo.

call :ensure_jdk
if errorlevel 1 exit /b 1

echo.
echo Starting RuneLite with Leadman (first launch may take several minutes)...
echo Look for: Leadman: loaded ... item rules
echo.
call run-dev.cmd %*
exit /b %ERRORLEVEL%

:ensure_jdk
if not "%JAVA_HOME%"=="" (
  echo Using JAVA_HOME=%JAVA_HOME%
  exit /b 0
)

for /d %%d in (".tools\jdk-11*") do (
  set "JAVA_HOME=%%~fd"
  echo Using portable JDK in .tools\
  exit /b 0
)

echo No JDK found. Downloading Temurin 11 into .tools\ ...
echo This is a one-time step (~190 MB).
echo.

if not exist ".tools" mkdir ".tools"

set "JDK_ZIP=.tools\jdk11-download.zip"
set "JDK_URL=https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference = 'SilentlyContinue';" ^
  "try { Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JDK_ZIP%' -UseBasicParsing }" ^
  "catch { Write-Error $_.Exception.Message; exit 1 }"

if errorlevel 1 (
  echo.
  echo Download failed. Check your internet connection and try again.
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '.tools' -Force }" ^
  "catch { Write-Error $_.Exception.Message; exit 1 }"

del /q "%JDK_ZIP%" 2>nul

for /d %%d in (".tools\jdk-11*") do (
  set "JAVA_HOME=%%~fd"
  echo JDK ready: %%~fd
  exit /b 0
)

echo.
echo JDK downloaded but folder not found under .tools\jdk-11*
echo Check .tools\ manually and run run-dev.cmd again.
exit /b 1
