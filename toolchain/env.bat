@echo off
REM Usage: call toolchain\env.bat
set JAVA_HOME=C:\Users\Administrator\.toolchain\jdk-17
set M2_HOME=C:\Users\Administrator\.toolchain\maven
set PATH=C:\Users\Administrator\.toolchain\jdk-17\bin;C:\Users\Administrator\.toolchain\maven\bin;C:\Users\Administrator\.toolchain\redis;%PATH%
echo [env] JDK / Maven / Redis added to current session PATH
