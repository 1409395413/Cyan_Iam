# =============================================================================
#  Stop local Redis gracefully (SAVE first so nothing is lost)
#  Usage: powershell -ExecutionPolicy Bypass -File .\toolchain\redis-stop.ps1
# =============================================================================
$Root = Join-Path $env:USERPROFILE '.toolchain'
$cli  = Join-Path $Root 'redis\redis-cli.exe'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pass = $env:REDIS_PASSWORD
if ([string]::IsNullOrWhiteSpace($pass) -and (Test-Path (Join-Path $repoRoot '.env'))) {
    $line = Get-Content (Join-Path $repoRoot '.env') | Where-Object { $_ -match '^\s*REDIS_PASSWORD\s*=' } | Select-Object -First 1
    if ($line) { $pass = ($line -split '=', 2)[1].Trim().Trim('"', "'") }
}
# 未配置密码时不做猜测：留空并提示，由下面的进程终止分支兜底
if ([string]::IsNullOrWhiteSpace($pass)) { $pass = ''; Write-Host '[redis] REDIS_PASSWORD 未配置，跳过 SAVE' -ForegroundColor DarkYellow }

if ((Test-Path $cli) -and -not ([string]::IsNullOrWhiteSpace($pass))) {
    $null = & $cli -a $pass save 2>$null
    Write-Host '[redis] SAVE issued'
}

$proc = Get-Process redis-server -ErrorAction SilentlyContinue
if ($proc) {
    $proc | Stop-Process -Force
    Write-Host ('[redis] stopped pid ' + ($proc.Id -join ', '))
} else {
    Write-Host '[redis] not running'
}
