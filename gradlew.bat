@echo off
setlocal

set "APP_HOME=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%gradle\wrapper\bootstrap-wrapper.ps1"
if errorlevel 1 exit /b %ERRORLEVEL%

if defined JAVA_HOME goto findJavaFromJavaHome

where java.exe >NUL 2>&1
if errorlevel 1 (
  echo Java was not found. Install Java 21 or set JAVA_HOME. 1>&2
  exit /b 1
)
set "JAVA_EXE=java.exe"
goto execute

:findJavaFromJavaHome
set "JAVA_HOME=%JAVA_HOME:"=%"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if exist "%JAVA_EXE%" goto execute

echo JAVA_HOME does not point to a valid Java installation: %JAVA_HOME% 1>&2
exit /b 1

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*
exit /b %ERRORLEVEL%
