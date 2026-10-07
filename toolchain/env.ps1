# Usage:  . .\toolchain\env.ps1     (note the leading dot)
$env:JAVA_HOME = 'C:\Users\Administrator\.toolchain\jdk-17'
$env:M2_HOME   = 'C:\Users\Administrator\.toolchain\maven'
$env:Path      = 'C:\Users\Administrator\.toolchain\jdk-17\bin;C:\Users\Administrator\.toolchain\maven\bin;C:\Users\Administrator\.toolchain\redis' + ';' + $env:Path
Write-Host '[env] JDK / Maven / Redis added to current session PATH' -ForegroundColor Green
