# =============================================================================
#  Start local MySQL 8 (portable instance, no admin rights required)
#  Usage: powershell -ExecutionPolicy Bypass -File .\toolchain\mysql-start.ps1
#  Data : %USERPROFILE%\.toolchain\mysql-data      Port: 3307 (避免撞已有 MySQL)
# =============================================================================
$ErrorActionPreference = 'Stop'

$Root   = Join-Path $env:USERPROFILE '.toolchain'
$MyBase = Join-Path $Root 'mysql'
$DataDir = Join-Path $Root 'mysql-data'
$Ini    = Join-Path $Root 'mysql-data.ini'
$myd    = Join-Path $MyBase 'bin\mysqld.exe'

if (-not (Test-Path $myd)) {
    Write-Host '[mysql] not installed - installing first ...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'setup-mysql.ps1')
}

# ---- 首次：生成配置文件 ----
if (-not (Test-Path $Ini)) {
    Write-Host '[mysql] writing my.ini'
    $basedir = $MyBase.Replace('\', '/')
    $datadir = $DataDir.Replace('\', '/')
    $iniText = @"
[mysqld]
basedir=$basedir
datadir=$datadir
port=3307
bind-address=127.0.0.1
default-time-zone=+08:00
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
max_connections=100
innodb_buffer_pool_size=128M
innodb_flush_log_at_trx_commit=1
tmp_table_size=64M
log-error=$datadir/mysql-error.log
[client]
port=3307
default-character-set=utf8mb4
"@
    Set-Content -Path $Ini -Value $iniText -Encoding ASCII
}

# ---- 首次：初始化数据字典 ----
if (-not (Test-Path (Join-Path $DataDir 'mysql'))) {
    $null = New-Item -ItemType Directory -Force -Path $DataDir
    Write-Host '[mysql] initializing data directory (root@localhost, empty password) ...'
    & $myd --defaults-file=$Ini --initialize-insecure --console
}

# ---- 已在跑？ ----
$conn = Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue
if ($conn) { Write-Host ('[mysql] already listening on 3307, pid=' + $conn.OwningProcess); exit 0 }

Write-Host '[mysql] starting ...'
Start-Process -FilePath $myd -ArgumentList ('--defaults-file=' + $Ini) -WindowStyle Hidden
$ok = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 1
    if (Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue) { $ok = $true; break }
}

if ($ok) {
    Write-Host '[mysql] up on 127.0.0.1:3307' -ForegroundColor Green
} else {
    Write-Host '[mysql] failed to start - check mysql-data\mysql-error.log' -ForegroundColor Red
}
Write-Host ('[mysql] config: ' + $Ini)
