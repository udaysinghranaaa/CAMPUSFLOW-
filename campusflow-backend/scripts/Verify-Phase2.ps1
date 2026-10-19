[CmdletBinding()]
param([string]$MavenPath, [string]$JavaPath)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$previousDbPassword = $env:DB_PASSWORD
$previousPgPassword = $env:PGPASSWORD
$server = $null
. "$PSScriptRoot/Verification-Security.ps1"
$securityEnvironment = $null
$departmentId = $null
$facultyId = $null
$baseUrl = $null

function Assert-Api {
    param([string]$Method, [string]$Path, [int]$Expected, $Body = $null)
    $arguments = @{ Uri = "$baseUrl$Path"; Method = $Method; UseBasicParsing = $true; TimeoutSec = 15 }
    if ($env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN) { $arguments.Headers = @{Authorization="Bearer $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN"} }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = ($Body | ConvertTo-Json -Compress)
    }
    try {
        $response = Invoke-WebRequest @arguments
        $status = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        if (-not $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $content = $null
        if ($status -ne $Expected) { throw "API check failed: $Method $Path expected $Expected, received $status." }
    }
    if ($status -ne $Expected) { throw "API check failed: $Method $Path expected $Expected, received $status." }
    Write-Host "PASS $Method $Path -> $status"
    if ($content) { return ($content | ConvertFrom-Json) }
}

Push-Location $project
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
    & "$PSScriptRoot/Test-DatabaseConnection.ps1"
    $securityEnvironment = Begin-VerificationSecurity

    if (-not $MavenPath) {
        $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($command) { $MavenPath = $command.Source }
        else {
            $cache = Join-Path $env:USERPROFILE '.m2/wrapper/dists/apache-maven-3.10.0'
            $MavenPath = Get-ChildItem -LiteralPath $cache -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
                Select-Object -First 1 -ExpandProperty FullName
        }
    }
    if (-not $MavenPath -or -not (Test-Path -LiteralPath $MavenPath)) {
        throw 'Maven not found. Supply -MavenPath with the path to Maven 3.10.0 bin/mvn.cmd.'
    }
    & $MavenPath "-Dmaven.repo.local=$project/target/maven-repository" -B -Ppostgres-it verify
    if ($LASTEXITCODE -ne 0) { throw 'Database test suite failed. Live API verification was not started.' }

    if (-not $JavaPath) {
        if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
            $JavaPath = "$env:JAVA_HOME/bin/java.exe"
        } elseif (Test-Path 'C:/Program Files/Java/jdk-26.0.1/bin/java.exe') {
            $JavaPath = 'C:/Program Files/Java/jdk-26.0.1/bin/java.exe'
        } else { throw 'Supply -JavaPath pointing to the JDK bin/java.exe executable.' }
    }
    $outputLog = Join-Path $project 'target/phase2-live-server.log'
    $errorLog = Join-Path $project 'target/phase2-live-server-error.log'
    $server = Start-Process -FilePath $JavaPath -ArgumentList '-jar',
        'target/campusflow-backend-0.0.1-SNAPSHOT.jar','--server.port=0','--server.address=127.0.0.1' `
        -WorkingDirectory $project -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $server.Refresh()
        if ($server.HasExited) { throw 'Application exited during startup. Inspect target/phase2-live-server*.log locally.' }
        $match = Select-String -LiteralPath $outputLog -Pattern 'Tomcat started on port (\d+)' |
            Select-Object -Last 1
        if ($match) {
            $baseUrl = 'http://127.0.0.1:' + $match.Matches[0].Groups[1].Value
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $baseUrl) { throw 'Application startup timed out.' }
    $health = Assert-Api GET '/api/campus/health' 200
    if ($health.status -cne 'UP' -or $health.application -cne 'CampusFlow' -or
        $health.message -cne 'CampusFlow Backend is Running!') { throw 'Health response mismatch.' }

    Connect-VerificationAdmin -BaseUrl $baseUrl
    $token = [guid]::NewGuid().ToString('N').Substring(0,16).ToUpperInvariant()
    $departmentBody = @{name='Phase 2 Verification'; code=$token; description='Temporary verification record'}
    $department = Assert-Api POST '/api/v1/departments' 201 $departmentBody
    $departmentId = $department.id
    $null = Assert-Api POST '/api/v1/departments' 409 $departmentBody
    $null = Assert-Api GET "/api/v1/departments/$departmentId" 200
    $null = Assert-Api GET "/api/v1/departments?code=$token&page=0&size=10" 200
    $departmentBody.name = 'Phase 2 Updated'
    $updatedDepartment = Assert-Api PUT "/api/v1/departments/$departmentId" 200 $departmentBody
    if ($updatedDepartment.name -cne $departmentBody.name) { throw 'Department update mismatch.' }

    $facultyBody = @{ fullName="Phase 2 Faculty $token"; email="$token@example.edu"; employeeCode=$token;
        designation='Lecturer'; departmentId=$departmentId }
    $member = Assert-Api POST '/api/v1/faculty' 201 $facultyBody
    $facultyId = $member.id
    # Exercise each optional filter independently, including the formerly failing null-name path.
    $null = Assert-Api GET '/api/v1/faculty?page=0&size=1' 200
    $byDepartment = Assert-Api GET "/api/v1/faculty?departmentId=$departmentId&page=0&size=1" 200
    if ($byDepartment.totalElements -ne 1 -or $byDepartment.content[0].id -ne $facultyId) {
        throw 'Department-only faculty filter mismatch.'
    }
    $byActive = Assert-Api GET '/api/v1/faculty?active=true&page=0&size=1' 200
    if ($byActive.content.Count -lt 1 -or $byActive.content[0].active -ne $true) {
        throw 'Active-only faculty filter mismatch.'
    }
    $lowerName = $token.ToLowerInvariant()
    $byName = Assert-Api GET "/api/v1/faculty?name=$lowerName&page=0&size=1" 200
    if ($byName.totalElements -ne 1 -or $byName.content[0].id -ne $facultyId) {
        throw 'Case-insensitive name-only faculty filter mismatch.'
    }
    $nextPage = Assert-Api GET "/api/v1/faculty?departmentId=$departmentId&page=1&size=1" 200
    if ($nextPage.content.Count -ne 0 -or $nextPage.totalElements -ne 1) {
        throw 'Faculty pagination mismatch.'
    }
    $null = Assert-Api POST '/api/v1/faculty' 409 $facultyBody
    $null = Assert-Api GET "/api/v1/faculty/$facultyId" 200
    $null = Assert-Api DELETE "/api/v1/departments/$departmentId" 409
    $facultyBody.fullName = "Phase 2 Faculty Updated $token"
    $facultyBody.designation = 'Professor'
    $updatedFaculty = Assert-Api PUT "/api/v1/faculty/$facultyId" 200 $facultyBody
    if ($updatedFaculty.fullName -cne $facultyBody.fullName) { throw 'Faculty update mismatch.' }
    $inactive = Assert-Api PATCH "/api/v1/faculty/$facultyId/status" 200 @{active=$false}
    if ($inactive.active -ne $false) { throw 'Faculty status mismatch.' }
    $filtered = Assert-Api GET "/api/v1/faculty?departmentId=$departmentId&active=false&name=Updated&page=0&size=10" 200
    if ($filtered.totalElements -ne 1 -or $filtered.content[0].id -ne $facultyId) { throw 'Faculty filter mismatch.' }
    $null = Assert-Api POST '/api/v1/departments' 400 @{name='';code=''}
    $invalidFaculty = $facultyBody.Clone()
    $invalidFaculty.email = 'invalid'
    $null = Assert-Api POST '/api/v1/faculty' 400 $invalidFaculty
    $invalidFaculty.email = "$token-invalid@example.edu"
    $invalidFaculty.departmentId = [long]::MaxValue
    $null = Assert-Api POST '/api/v1/faculty' 404 $invalidFaculty
    $null = Assert-Api DELETE "/api/v1/faculty/$facultyId" 204
    $null = Assert-Api GET "/api/v1/faculty/$facultyId" 404
    $facultyId = $null
    $null = Assert-Api DELETE "/api/v1/departments/$departmentId" 204
    $null = Assert-Api GET "/api/v1/departments/$departmentId" 404
    $departmentId = $null
    Write-Host 'Phase 2 real PostgreSQL tests and live CRUD verification passed.'
} finally {
    # Only remove rows created by this script; never clear existing project data.
    if ($facultyId -and $baseUrl) {
        try { $null = Assert-Api DELETE "/api/v1/faculty/$facultyId" 204 }
        catch { Write-Warning "Could not remove temporary faculty ID $facultyId; clean it up locally." }
    }
    if ($departmentId -and $baseUrl) {
        try { $null = Assert-Api DELETE "/api/v1/departments/$departmentId" 204 }
        catch { Write-Warning "Could not remove temporary department ID $departmentId; clean it up locally." }
    }
    End-VerificationSecurity -Snapshot $securityEnvironment -BaseUrl $baseUrl
    if ($server) {
        $server.Refresh()
        if (-not $server.HasExited) { Stop-Process -Id $server.Id }
    }
    $env:DB_PASSWORD = $previousDbPassword
    $env:PGPASSWORD = $previousPgPassword
    Pop-Location
}
