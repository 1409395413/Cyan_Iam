# Loads .env into the current session (and optionally into Java process env).
# Usage:  . .\toolchain\load-env.ps1
param()

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $Root '.env'

if (-not (Test-Path $envFile)) {
  Write-Host "[env] no .env found, using application.yml defaults" -ForegroundColor Yellow
  return
}

foreach ($line in Get-Content $envFile) {
  $trimmed = $line.Trim()
  if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
  $idx = $trimmed.IndexOf('=')
  if ($idx -lt 1) { continue }
  $k = $trimmed.Substring(0, $idx).Trim()
  $v = $trimmed.Substring($idx + 1).Trim().Trim('"', "'")
  [Environment]::SetEnvironmentVariable($k, $v, 'Process')
  Set-Item -Path ("Env:" + $k) -Value $v
}

Write-Host "[env] loaded from $envFile"
