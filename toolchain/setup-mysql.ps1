# =============================================================================
#  Install MySQL 8 as a portable instance (no admin / no MSI installer)
#  Usage: powershell -ExecutionPolicy Bypass -File .\toolchain\setup-mysql.ps1
#  Skipped automatically when %USERPROFILE%\.toolchain\mysql already exists.
# =============================================================================
$ErrorActionPreference = 'Stop'

$Root  = Join-Path $env:USERPROFILE '.toolchain'
$Dl    = Join-Path $Root '_downloads'
$Tmp   = Join-Path $Root '_tmp'
$Dest  = Join-Path $Root 'mysql'
$null  = New-Item -ItemType Directory -Force -Path $Root, $Dl, $Tmp

if (Test-Path (Join-Path $Dest 'bin\mysqld.exe')) {
    Write-Host ('[mysql] already installed at ' + $Dest)
    exit 0
}

$zip = Join-Path $Dl 'mysql-8.0.40-winx64.zip'
if (-not (Test-Path $zip)) {
    Write-Host '[mysql] downloading MySQL 8.0.40 (~243 MB, please wait) ...'
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri 'https://dev.mysql.com/get/Downloads/MySQL-8.0/mysql-8.0.40-winx64.zip' `
                      -OutFile $zip -UseBasicParsing -TimeoutSec 2400
    Write-Host ('[mysql] downloaded ' + [math]::Round(((Get-Item $zip).Length / 1MB), 1) + ' MB')
}

Write-Host '[mysql] extracting ...'
$stage = Join-Path $Tmp 'mysql_x'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
Expand-Archive -Path $zip -DestinationPath $stage -Force
$inner = @(Get-ChildItem $stage -Directory)
if ($inner.Count -eq 1) { Move-Item $inner[0].FullName $Dest -Force }
else { New-Item -ItemType Directory -Force -Path $Dest | Out-Null; Copy-Item (Join-Path $stage '*') $Dest -Recurse -Force }
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue

if (Test-Path (Join-Path $Dest 'bin\mysqld.exe')) {
    Write-Host ('[mysql] installed at ' + $Dest) -ForegroundColor Green
    Write-Host '[mysql] next step: .\toolchain\mysql-start.ps1  (initializes data dir on first run)'
} else {
    Write-Host '[mysql] install failed - set it up manually with the MSI installer' -ForegroundColor Red
}
