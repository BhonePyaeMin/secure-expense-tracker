@echo off
rem Starts Expense Tracker with low-memory JVM settings (about 230 MB).
rem Builds the jar first if it's missing. After pulling or changing code, rebuild with:
rem   mvnw.cmd -DskipTests package
rem Extra options are passed to the app, e.g.  run.bat --spring.profiles.active=demo
setlocal
cd /d "%~dp0"

set JAR=target\expense-tracker-0.0.1-SNAPSHOT.jar
if not exist "%JAR%" (
    echo Building %JAR% ^(first run only^)...
    call "%~dp0mvnw.cmd" -B -q -DskipTests package || exit /b 1
)

java -Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar "%JAR%" %*
