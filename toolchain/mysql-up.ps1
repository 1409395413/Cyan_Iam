# Starts local MySQL so that the session-independent instance keeps running.
param()

$exe = Join-Path $env:USERPROFILE '.toolchain\mysql\bin\mysqld.exe'
$ini = Join-Path $env:USERPROFILE '.toolchain\mysql-data.ini'
if (-not (Test-Path $exe)) { Write-Host '[mysql] mysql not installed' -ForegroundColor Red; exit 1 }

$c = Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue
if ($c) { Write-Host ('[mysql] already listening on 3307 (pid=' + $c.OwningProcess + ')'); exit 0 }

Start-Process -FilePath $exe -ArgumentList @('--defaults-file=' + $ini) -WindowStyle Hidden
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 1
  if (Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host '[mysql] UP on 3307' -ForegroundColor Green
    exit 0
  }
}
Write-Host '[mysql] failed to start, see mysql-error.log' -ForegroundColor Red
