@echo off
setlocal
set "MAVEN_VERSION=3.9.11"
set "BASE_DIR=%~dp0"
set "DIST_ROOT=%BASE_DIR%.mvn\dist"
set "MAVEN_HOME=%DIST_ROOT%\apache-maven-%MAVEN_VERSION%"
set "ARCHIVE=%DIST_ROOT%\apache-maven-%MAVEN_VERSION%-bin.zip"
if "%MAVEN_DISTRIBUTION_BASE_URL%"=="" (
  set "BASE_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven"
) else (
  set "BASE_URL=%MAVEN_DISTRIBUTION_BASE_URL%"
)
set "DOWNLOAD_URL=%BASE_URL%/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
  echo Downloading Apache Maven %MAVEN_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "Invoke-WebRequest -UseBasicParsing '%DOWNLOAD_URL%' -OutFile '%ARCHIVE%';" ^
    "Invoke-WebRequest -UseBasicParsing '%DOWNLOAD_URL%.sha512' -OutFile '%ARCHIVE%.sha512';" ^
    "$expected=(Get-Content '%ARCHIVE%.sha512').Trim();" ^
    "$actual=(Get-FileHash '%ARCHIVE%' -Algorithm SHA512).Hash.ToLower();" ^
    "if ($expected.ToLower() -ne $actual) { throw 'Maven distribution checksum verification failed.' };" ^
    "Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%DIST_ROOT%' -Force"
  if errorlevel 1 exit /b 1
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
endlocal
