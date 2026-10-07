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
if ([string]::IsNullOrWhiteSpace($pass)) { $pass = 'Cyan1120' }

if (Test-Path $cli) {
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
