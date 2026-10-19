[CmdletBinding()]
param([string]$MavenPath,[string]$JavaPath='C:/Program Files/Java/jdk-26.0.1/bin/java.exe')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/Verification-Security.ps1"
$previous=@{};foreach($name in @('DB_PASSWORD','PGPASSWORD','DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PASSWORD')) {
    $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
}
$securityEnvironment=$null
Push-Location $project
try {
    if(-not $env:DB_PASSWORD) {
        $secure=Read-Host 'Database password' -AsSecureString
        try {$env:DB_PASSWORD=[Net.NetworkCredential]::new('', $secure).Password} finally {$secure.Dispose()}
    }
    & "$PSScriptRoot/Test-DatabaseConnection.ps1"
    $securityEnvironment=Begin-VerificationSecurity
    $env:BOOTSTRAP_ADMIN_EMAIL=Read-Host 'Initial ADMIN email (bootstrap refuses if any ADMIN already exists)'
    $secure=Read-Host 'Initial ADMIN password (12+ characters, at most 72 UTF-8 bytes)' -AsSecureString
    try {$env:BOOTSTRAP_ADMIN_PASSWORD=[Net.NetworkCredential]::new('', $secure).Password} finally {$secure.Dispose()}
    if(-not $MavenPath) {
        $command=Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if($command) {$MavenPath=$command.Source} else {
            $MavenPath=Get-ChildItem -LiteralPath "$env:USERPROFILE/.m2/wrapper/dists/apache-maven-3.10.0" -Filter mvn.cmd -Recurse |
                Select-Object -First 1 -ExpandProperty FullName
        }
    }
    if(-not $MavenPath) {throw 'Supply -MavenPath pointing to Maven bin/mvn.cmd.'}
    & $MavenPath "-Dmaven.repo.local=$project/target/maven-repository" -B package
    if($LASTEXITCODE -ne 0) {throw 'Build failed; no bootstrap attempted.'}
    & $JavaPath '-Dspring.main.web-application-type=none' '-jar' 'target/campusflow-backend-0.0.1-SNAPSHOT.jar' '--campusflow.bootstrap-admin=true'
    if($LASTEXITCODE -ne 0) {throw 'Bootstrap refused or failed. No existing account was overwritten.'}
} finally {
    End-VerificationSecurity -Snapshot $securityEnvironment -BaseUrl $null
    foreach($name in $previous.Keys) {[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
    Pop-Location
}
