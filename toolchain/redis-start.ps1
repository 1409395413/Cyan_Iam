# =============================================================================
#  Start local Redis with the hardened dev config
#  Usage: powershell -ExecutionPolicy Bypass -File .\toolchain\redis-start.ps1
# =============================================================================
$ErrorActionPreference = 'Stop'

$Root  = Join-Path $env:USERPROFILE '.toolchain'
$Redis = Join-Path $Root 'redis'
$Repo  = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Repo  = if (Test-Path (Join-Path $PSScriptRoot '..\web')) { (Resolve-Path (Join-Path $PSScriptRoot '..')).Path } else { $Repo }

$exe = Join-Path $Redis 'redis-server.exe'
if (-not (Test-Path $exe)) { Write-Host 'redis-server.exe not found - run setup.ps1 first' -ForegroundColor Red; exit 1 }

# ---- password: $env:REDIS_PASSWORD  >  repo/.env  >  default ----
$pass = $env:REDIS_PASSWORD
if ([string]::IsNullOrWhiteSpace($pass)) {
    $envFile = Join-Path $Repo '.env'
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^\s*REDIS_PASSWORD\s*=' } | Select-Object -First 1
        if ($line) { $pass = ($line -split '=', 2)[1].Trim().Trim('"', "'") }
    }
}
if ([string]::IsNullOrWhiteSpace($pass)) { $pass = 'Cyan1120' }

# ---- data dir ----
$dataDir = (Join-Path $Root 'redis-data').Replace('\', '/')
$null = New-Item -ItemType Directory -Force -Path (Join-Path $Root 'redis-data')

# ---- render final config ----
$tpl  = Get-Content (Join-Path $PSScriptRoot 'redis.local.conf') -Raw
$conf = $tpl.Replace('__DATA_DIR__', $dataDir).Replace('__PASSWORD__', $pass)
$out  = Join-Path $Root 'redis.local.generated.conf'
Set-Content -Path $out -Value $conf -Encoding ASCII

# ---- already running? ----
$proc = Get-Process redis-server -ErrorAction SilentlyContinue
if ($proc) { Write-Host ('[redis] already running, pid=' + $proc[0].Id); exit 0 }

Write-Host '[redis] starting ...'
Start-Process -FilePath $exe -ArgumentList ('"' + $out + '"') -WindowStyle Hidden -PassThru |
    Select-Object -ExpandProperty Id | ForEach-Object { Write-Host ('[redis] pid=' + $_) }
Start-Sleep -Seconds 2

# 用环境变量传密码，避免 redis-cli 的 "-a is unsafe" 警告
$env:REDISCLI_AUTH = $pass
$cli = Join-Path $Redis 'redis-cli.exe'
if (Test-Path $cli) {
    $pong = (& $cli -h 127.0.0.1 ping 2>$null) -join ' '
    Write-Host ('[redis] PING -> ' + $pong)
    if ($pong -notlike '*PONG*') {
        Write-Host '[redis] server up but PING failed - wrong password?' -ForegroundColor Yellow
    }
}
Write-Host ('[redis] data dir: ' + (Join-Path $Root 'redis-data'))
Write-Host ('[redis] config  : ' + $out)
