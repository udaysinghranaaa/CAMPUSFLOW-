[CmdletBinding()]
param([string]$MavenPath)
$ErrorActionPreference = 'Stop'
$backend = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../campusflow-backend'))
if (-not (Test-Path -LiteralPath (Join-Path $backend 'pom.xml'))) { throw 'Existing backend pom.xml not found.' }
if (-not $MavenPath) {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { $MavenPath = $command.Source }
    else {
        $cache = Join-Path $env:USERPROFILE '.m2/wrapper/dists/apache-maven-3.10.0'
        $MavenPath = Get-ChildItem -LiteralPath $cache -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $MavenPath -or -not (Test-Path -LiteralPath $MavenPath)) { throw 'Supply -MavenPath pointing to bin/mvn.cmd.' }
$saved = @{}
foreach ($name in @('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','DB_PASSWORD','JWT_SECRET')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $backend
try {
    if (-not $env:DB_HOST) { $env:DB_HOST = 'localhost' }
    if (-not $env:DB_PORT) { $env:DB_PORT = '5432' }
    if (-not $env:DB_NAME) { $env:DB_NAME = 'campusflow_db' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'campusflow_app' }
    if (-not $env:DB_PASSWORD) {
        $secret = Read-Host 'CampusFlow database password (process only)' -AsSecureString
        try { $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password }
        finally { $secret.Dispose(); Remove-Variable secret }
    }
    if (-not $env:JWT_SECRET) {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes); $env:JWT_SECRET = [Convert]::ToBase64String($bytes) }
        finally { $random.Dispose(); [Array]::Clear($bytes, 0, $bytes.Length) }
    }
    & $MavenPath "-Dmaven.repo.local=$backend/target/maven-repository" spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend did not exit successfully; inspect the safe local diagnostics.' }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    $saved.Clear()
    Pop-Location
}
