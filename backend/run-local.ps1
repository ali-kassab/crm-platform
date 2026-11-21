# Runs the backend locally against the docker-compose infrastructure.
# `mvn spring-boot:run` does not read the repo-root .env, so without this the app falls back to the
# application.yml defaults (e.g. DB password "crm") and fails with "password authentication failed".
# Usage (from backend/):  .\run-local.ps1   [extra Maven args, e.g. -Dspring-boot.run.profiles=dev,demo]
$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path $envFile)) { throw "Missing $envFile - copy .env.example to .env first." }
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        $value = $Matches[2].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($Matches[1], $value, 'Process')
    }
}
Push-Location $PSScriptRoot
try { & .\mvnw.cmd spring-boot:run @args } finally { Pop-Location }
