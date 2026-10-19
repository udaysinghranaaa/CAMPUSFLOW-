[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$previousDbPassword = $env:DB_PASSWORD
$previousPgPassword = $env:PGPASSWORD
try {
    if (-not $env:DB_HOST) { $env:DB_HOST = 'localhost' }
    if (-not $env:DB_PORT) { $env:DB_PORT = '5432' }
    if (-not $env:DB_NAME) { $env:DB_NAME = 'campusflow_db' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'campusflow_app' }
    if (-not $env:DB_PASSWORD) {
        $secret = Read-Host 'CampusFlow database password' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
        Remove-Variable secret
    }
    $psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
    if (-not (Test-Path -LiteralPath $psql)) { $psql = (Get-Command psql -ErrorAction Stop).Source }
    $env:PGPASSWORD = $env:DB_PASSWORD
    & $psql -X -w -h $env:DB_HOST -p $env:DB_PORT -U $env:DB_USERNAME -d $env:DB_NAME -v ON_ERROR_STOP=1 -c 'SELECT current_database(), current_user;'
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL connection verification failed.' }
    Write-Host 'PostgreSQL connection verified. No schema or data was changed.'
} finally {
    $env:DB_PASSWORD = $previousDbPassword
    $env:PGPASSWORD = $previousPgPassword
}
