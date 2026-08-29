@echo off
REM Starts RuneLite with Leadman loaded as a built-in plugin.
REM Fresh PC with no Java? Use setup-windows.cmd instead.
REM Jagex account login: see docs\DEVELOPING.mdREM Uses the portable JDK in .tools\ if present, so no system-wide Java is needed.
setlocal
cd /d "%~dp0"

if "%JAVA_HOME%"=="" (
  for /d %%d in (".tools\jdk-11*") do set "JAVA_HOME=%%~fd"
)

if "%JAVA_HOME%"=="" (
  echo No JDK found. Install JDK 11, put a portable one in .tools\, or run setup-windows.cmd
  exit /b 1
)

echo JAVA_HOME=%JAVA_HOME%
call gradlew.bat run %*
