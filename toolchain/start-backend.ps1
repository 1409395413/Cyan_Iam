# Starts the Java backend as a detached process. It survives the terminal session.
param([switch]$Rebuild)

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

# Windows may carry HTTP_PROXY/http_proxy duplicates. Start-Process builds a
# case-insensitive dictionary and fails on duplicates, so drop the twins first.
$seen = @{}
foreach ($e in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) {
  $k = $e.Key.ToString()
  $lk = $k.ToLowerInvariant()
  if ($seen.ContainsKey($lk)) { Remove-Item ("Env:" + $k) -ErrorAction SilentlyContinue }
  else { $seen[$lk] = $true }
}
# Health check must not go through a proxy
[System.Net.WebRequest]::DefaultWebProxy = $null

. (Join-Path $PSScriptRoot 'load-env.ps1') | Out-Null

$java = Join-Path $env:USERPROFILE '.toolchain\jdk-17\bin\java.exe'
if (-not (Test-Path $java)) { $java = 'java' }

if ($Rebuild) {
  $mvn = Join-Path $env:USERPROFILE '.toolchain\maven\bin\mvn.cmd'
  & $mvn -B -f (Join-Path $Root 'server\pom.xml') package -DskipTests -q
  if ($LASTEXITCODE -ne 0) { Write-Host '[backend] build FAILED' -ForegroundColor Red; exit 1 }
}

$jar = Join-Path $Root 'server\target\portfolio-server.jar'
if (-not (Test-Path $jar)) { Write-Host '[backend] jar missing, run with -Rebuild' -ForegroundColor Red; exit 1 }

$proc = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*jdk-17*' }
if ($proc) {
  Write-Host ('[backend] already running, pid=' + ($proc.Id -join ',')) -ForegroundColor Yellow
  exit 0
}

$logDir = Join-Path $Root '_smoke'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$out = Join-Path $logDir 'backend.log'
$err = Join-Path $logDir 'backend.err.log'

$p = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$jar`"", '--server.port=8080') `
  -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru

Write-Host ('[backend] started pid=' + $p.Id + '  log=' + $out)

$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 3
    if ($r.StatusCode -eq 200) { $ok = $true; break }
  } catch { }
}
if ($ok) {
  Write-Host '[backend] UP -> http://127.0.0.1:8080' -ForegroundColor Green
} else {
  Write-Host '[backend] did not become healthy, tail log:' -ForegroundColor Red
  if (Test-Path $err) { Get-Content $err -Tail 25 }
}
