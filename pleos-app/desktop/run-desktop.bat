@echo off
setlocal
set "ROOT=%~dp0.."
if exist "%USERPROFILE%\.jdks\jbr-21.0.11" set "JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=D:\gradle-home"
pushd "%ROOT%"
call "%ROOT%\gradlew.bat" :desktop:run --console=plain
popd
