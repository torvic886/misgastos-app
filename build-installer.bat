@echo off
REM ============================================================
REM  Genera el instalador de MisGastos (.exe) con jpackage
REM  Requisitos en ESTE PC: JDK 17, Maven y WiX Toolset 3.14 en el PATH
REM  Antes de ejecutar: cierra la app, Spring Tools y DB Browser
REM ============================================================

set PROYECTO=C:\Users\THINKPAD\Documents\worskspace_sb\misgastos-app
set JAR=misgastos-app-0.0.1-SNAPSHOT.jar
set VERSION=1.0.0

cd /d %PROYECTO%

echo [1/3] Compilando el proyecto...
call mvn clean package -DskipTests
if errorlevel 1 goto error

echo [2/3] Preparando carpeta de entrada...
if exist target\installer-input rmdir /s /q target\installer-input
mkdir target\installer-input
copy target\%JAR% target\installer-input\ >nul
if errorlevel 1 goto error

echo [3/3] Generando el instalador...
jpackage ^
 --type exe ^
 --name MisGastos ^
 --app-version %VERSION% ^
 --vendor "MisGastos" ^
 --description "Control de gastos personales" ^
 --input target\installer-input ^
 --main-jar %JAR% ^
 --main-class org.springframework.boot.loader.launch.JarLauncher ^
 --dest target\installer ^
 --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.xml,java.logging,java.instrument,java.prefs,java.rmi,java.scripting,java.security.jgss,java.net.http,java.compiler,jdk.unsupported,jdk.crypto.ec,jdk.httpserver ^
 --java-options "-Dfile.encoding=UTF-8" ^
 --win-menu ^
 --win-shortcut ^
 --win-dir-chooser ^
 --win-per-user-install ^
 --win-upgrade-uuid 3f2b8c1e-7a4d-4e9b-9c55-1d2a6b7e8f90
if errorlevel 1 goto error

echo.
echo Listo. Instalador en: %PROYECTO%\target\installer\MisGastos-%VERSION%.exe
goto fin

:error
echo.
echo ERROR: revisa el mensaje de arriba.

:fin
pause
