# Stops the Java backend started by start-backend.ps1
$procs = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*jdk-17*' }
if (-not $procs) { Write-Host '[backend] not running'; exit 0 }
foreach ($p in $procs) {
  Write-Host ('[backend] stopping pid=' + $p.Id)
  Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}
Write-Host '[backend] stopped' -ForegroundColor Green
