# =============================================================================
#  Local toolchain installer (portable, no admin rights required)
#  Target dir : %USERPROFILE%\.toolchain   (does NOT touch Program Files)
#  Components : JDK 17 (Temurin) | Maven 3.9.11 | Redis 5.0.14 (Windows port)
#  Usage      : powershell -ExecutionPolicy Bypass -File .\setup.ps1
#  Note       : ASCII-only on purpose. PowerShell 5.1 mis-parses UTF-8 without BOM.
# =============================================================================
$ErrorActionPreference = 'Stop'

$Root = Join-Path $env:USERPROFILE '.toolchain'
$Dl   = Join-Path $Root '_downloads'
$Tmp  = Join-Path $Root '_tmp'
$null = New-Item -ItemType Directory -Force -Path $Root, $Dl, $Tmp

function Say($m)  { Write-Host $m }
function Step($m) { Say ''; Say ('==> ' + $m) }

function Fetch($url, $out) {
    if (Test-Path $out) { Say ('   cached: ' + (Split-Path $out -Leaf)); return $true }
    try {
        Say ('   GET ' + $url)
        $ProgressPreference = 'SilentlyContinue'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing -TimeoutSec 1800
        $ok = Test-Path $out
        if ($ok) { Say ('   saved ' + [math]::Round(((Get-Item $out).Length / 1MB), 1) + ' MB') }
        return $ok
    } catch {
        Say ('   FAILED: ' + $_.Exception.Message)
        if (Test-Path $out) { Remove-Item $out -Force -ErrorAction SilentlyContinue }
        return $false
    }
}

function UnzipTo($zip, $dest) {
    if (Test-Path $dest) { Say ('   already extracted: ' + (Split-Path $dest -Leaf)); return }
    $stage = Join-Path $Tmp ([IO.Path]::GetFileNameWithoutExtension($zip))
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    Say ('   extracting -> ' + $dest)
    Expand-Archive -Path $zip -DestinationPath $stage -Force
    $inner = @(Get-ChildItem $stage -Directory)
    $files = @(Get-ChildItem $stage -File)
    if ($inner.Count -eq 1 -and $files.Count -eq 0) {
        Move-Item $inner[0].FullName $dest -Force
    } else {
        $null = New-Item -ItemType Directory -Force -Path $dest
        Copy-Item (Join-Path $stage '*') $dest -Recurse -Force
    }
    Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- JDK 17 ----
Step 'Install JDK 17 (Eclipse Temurin)'
$jdkDir = Join-Path $Root 'jdk-17'
$jdkZip = Join-Path $Dl 'OpenJDK17-jdk_x64_windows_hotspot.zip'
if (Fetch 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkZip) {
    UnzipTo $jdkZip $jdkDir
}

# ---------------------------------------------------------------- Maven ----
Step 'Install Apache Maven 3.9.11'
$mvnDir = Join-Path $Root 'maven'
$mvnZip = Join-Path $Dl 'apache-maven-3.9.11-bin.zip'
$mvnGot = Fetch 'https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip' $mvnZip
if (-not $mvnGot) { $mvnGot = Fetch 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' $mvnZip }
if ($mvnGot) { UnzipTo $mvnZip $mvnDir }

# ---------------------------------------------------------------- Redis -----
Step 'Install Redis 5.0.14 (Windows port)'
$redisDir = Join-Path $Root 'redis'
$redisZip = Join-Path $Dl 'Redis-x64-5.0.14.1.zip'
if (Fetch 'https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip' $redisZip) {
    UnzipTo $redisZip $redisDir
}

# ---------------------------------------------------- Maven mirror config ---
Step 'Write Maven settings.xml (China CDN mirror)'
$m2 = Join-Path $env:USERPROFILE '.m2'
$null = New-Item -ItemType Directory -Force -Path $m2
$settings = @'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>aliyun public</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central,jcenter,!spring-snapshots,!spring-milestones</mirrorOf>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>jdk17</id>
      <activation><activeByDefault>true</activeByDefault></activation>
      <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
      </properties>
    </profile>
  </profiles>
</settings>
'@
Set-Content -Path (Join-Path $m2 'settings.xml') -Value $settings -Encoding UTF8

# ------------------------------------------------------- Environment vars ---
Step 'Configure user env: JAVA_HOME / M2_HOME / PATH'
$paths = @()
foreach ($p in @((Join-Path $jdkDir 'bin'), (Join-Path $mvnDir 'bin'), $redisDir)) {
    if (Test-Path $p) { $paths += $p }
}
if (Test-Path (Join-Path $jdkDir 'bin')) { setx JAVA_HOME $jdkDir | Out-Null }
if (Test-Path (Join-Path $mvnDir 'bin')) { setx M2_HOME $mvnDir | Out-Null }

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ([string]::IsNullOrWhiteSpace($userPath)) { $userPath = '' }
foreach ($p in $paths) {
    if ($userPath -notlike ('*' + $p + '*')) { $userPath = $p + ';' + $userPath }
}
[Environment]::SetEnvironmentVariable('Path', $userPath, 'User')
$env:Path = ($paths -join ';') + ';' + $env:Path
if (Test-Path $jdkDir)   { $env:JAVA_HOME = $jdkDir }
if (Test-Path $mvnDir)   { $env:M2_HOME   = $mvnDir }

# -------------------------------------------------- per-session wrappers ----
$pJoin = $paths -join ';'
$psEnv = @"
# Usage:  . .\toolchain\env.ps1     (note the leading dot)
`$env:JAVA_HOME = '$jdkDir'
`$env:M2_HOME   = '$mvnDir'
`$env:Path      = '$pJoin' + ';' + `$env:Path
Write-Host '[env] JDK / Maven / Redis added to current session PATH' -ForegroundColor Green
"@
Set-Content -Path (Join-Path $PSScriptRoot 'env.ps1') -Value $psEnv -Encoding ASCII

$batEnv = "@echo off`r`nREM Usage: call toolchain\env.bat`r`nset JAVA_HOME=$jdkDir`r`nset M2_HOME=$mvnDir`r`nset PATH=$pJoin;%PATH%`r`necho [env] JDK / Maven / Redis added to current session PATH"
Set-Content -Path (Join-Path $PSScriptRoot 'env.bat') -Value $batEnv -Encoding ASCII

# ------------------------------------------------------------- Verify -------
Step 'Verify'
function Probe($exe, $argList) {
    if (-not (Test-Path $exe) -and -not (Get-Command $exe -ErrorAction SilentlyContinue)) { return 'N/A' }
    try { (& $exe $argList 2>&1 | Select-Object -First 1) } catch { 'N/A' }
}
Say ('  java  : ' + (Probe (Join-Path $jdkDir 'bin\java.exe') @('-version')))
Say ('  javac : ' + (Probe (Join-Path $jdkDir 'bin\javac.exe') @('-version')))
$mvnV = Probe (Join-Path $mvnDir 'bin\mvn.cmd') @('-v')
Say ('  mvn   : ' + $mvnV)
Say ('  redis : ' + (Test-Path (Join-Path $redisDir 'redis-server.exe')))
Say ('  node  : ' + (Probe 'node' @('-v')))
Say ('  npm   : ' + (Probe 'npm' @('-v')))

Say ('DONE. Root: ' + $Root)
Write-Host 'DONE - see above'
