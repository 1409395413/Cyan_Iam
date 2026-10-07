# =============================================================================
#  Stop local MySQL gracefully
#  Usage: powershell -ExecutionPolicy Bypass -File .\toolchain\mysql-stop.ps1
# =============================================================================
$Root  = Join-Path $env:USERPROFILE '.toolchain'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function EnvFrom($file, $key) {
    if (-not (Test-Path $file)) { return $null }
    $line = Get-Content $file | Where-Object { $_ -match ('^\s*' + $key + '\s*=') } | Select-Object -First 1
    if ($line) { return ($line -split '=', 2)[1].Trim().Trim('"', "'") }
    return $null
}

$pw = $env:MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($pw)) { $pw = EnvFrom (Join-Path $repoRoot '.env') 'MYSQL_ROOT_PASSWORD' }
if ([string]::IsNullOrWhiteSpace($pw)) {
    $pw = EnvFrom (Join-Path $repoRoot '.env') 'MYSQL_PASSWORD'
}
if ([string]::IsNullOrWhiteSpace($pw)) { $pw = 'Cyan1120' }

$admin = Join-Path $Root 'mysql\bin\mysqladmin.exe'
if (Test-Path $admin) {
    $env:MYSQL_PWD = $pw
    $null = & $admin -h127.0.0.1 -P3307 -uroot shutdown 2>$null
    Start-Sleep -Seconds 2
}

$proc = Get-Process mysqld -ErrorAction SilentlyContinue
if ($proc) {
    $proc | Stop-Process -Force
    Write-Host ('[mysql] force stopped pid ' + ($proc.Id -join ', '))
} else {
    Write-Host '[mysql] stopped cleanly'
}
