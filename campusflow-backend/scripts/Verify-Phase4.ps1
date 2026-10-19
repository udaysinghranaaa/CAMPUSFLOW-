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
$batchId = $null
$sectionId = $null
$studentId = $null
$studentEnrollment = $null
$verificationAccounts = @()
$verificationSucceeded = $false
$cleanupSucceeded = $true
$savedDatabaseEnvironment = @{} 
foreach ($key in @('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME')) { $savedDatabaseEnvironment[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }

function Assert-Api {
    param([string]$Method, [string]$Path, [int]$Expected, $Body = $null, [string]$Bearer = $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN, [switch]$Anonymous)
    $arguments = @{ Uri = "$baseUrl$Path"; Method = $Method; UseBasicParsing = $true; TimeoutSec = 15 }
    if (-not $Anonymous -and $Bearer) { $arguments.Headers = @{Authorization="Bearer $Bearer"} }
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

function Remove-VerificationAccounts {
    if ($verificationAccounts.Count -eq 0) { return }
    $psqlPath='C:/Program Files/PostgreSQL/18/bin/psql.exe'
    if(-not (Test-Path -LiteralPath $psqlPath)) { $psqlPath=(Get-Command psql -ErrorAction Stop).Source }
    $env:PGPASSWORD=$env:DB_PASSWORD
    foreach($owned in @($verificationAccounts)) {
        $removed=& $psqlPath -X -w -q -t -A -h $env:DB_HOST -p $env:DB_PORT -U $env:DB_USERNAME -d $env:DB_NAME `
            -v ON_ERROR_STOP=1 -v "account_id=$($owned.id)" -v "email=$($owned.email)" -v "profile_id=$($owned.profileId)" -v "role=$($owned.role)" `
            -f "$PSScriptRoot/Cleanup-Phase4Account.sql"
        if($LASTEXITCODE -ne 0 -or "$removed".Trim() -ne "$($owned.id)") {throw "Verification-owned account cleanup failed for ID $($owned.id), role $($owned.role), profile $($owned.profileId); preserve dependent fixtures."}
        $script:verificationAccounts=@($verificationAccounts | Where-Object {$_.id -ne $owned.id})
        Write-Host "PASS cleanup verification account $($owned.id)"
    }
}
function Remove-VerificationStudent {
    if (-not $studentId) { return }
    $psqlPath = 'C:/Program Files/PostgreSQL/18/bin/psql.exe'
    if (-not (Test-Path -LiteralPath $psqlPath)) { $psqlPath = (Get-Command psql -ErrorAction Stop).Source }
    $env:PGPASSWORD = $env:DB_PASSWORD
    $removed = & $psqlPath -X -w -q -t -A -h $env:DB_HOST -p $env:DB_PORT -U $env:DB_USERNAME -d $env:DB_NAME `
        -v ON_ERROR_STOP=1 -v "student_id=$studentId" -v "enrollment_number=$studentEnrollment" `
        -f "$PSScriptRoot/Cleanup-Phase3Student.sql"
    if ($LASTEXITCODE -ne 0 -or "$removed".Trim() -ne "$studentId") {
        throw "Temporary student cleanup failed for ID $studentId. No broader cleanup was attempted."
    }
    Write-Host "PASS cleanup temporary student $studentId"
    $script:studentId = $null
}
function Remove-VerificationApiFixture {
    param([string]$Path, [long]$Id, [string]$OwnershipProperty, [string]$OwnershipValue)
    # Recheck the run-specific identifier/link before deleting an API fixture.
    try {
        $record = Invoke-RestMethod -Uri "$baseUrl$Path/$Id" -Method Get -TimeoutSec 15 `
            -Headers @{Authorization="Bearer $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN"}
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
            Write-Host "PASS cleanup $Path/$Id already absent"
            return
        }
        throw
    }
    if ($record.id -ne $Id -or [string]$record.$OwnershipProperty -cne $OwnershipValue) {
        throw "Fixture ownership mismatch for $Path/$Id; no deletion attempted."
    }
    $null = Assert-Api DELETE "$Path/$Id" 204
    $null = Assert-Api GET "$Path/$Id" 404
}
function Remove-VerificationFixtures {
    # Stop at a failed dependency, preserving parents instead of attempting FK-blocked deletes.
    Remove-VerificationAccounts
    if ($studentId) { Remove-VerificationStudent }
    if ($sectionId) {
        Remove-VerificationApiFixture '/api/v1/sections' $sectionId 'batchId' "$batchId"
        $script:sectionId = $null
    }
    if ($batchId) {
        Remove-VerificationApiFixture '/api/v1/batches' $batchId 'departmentId' "$departmentId"
        $script:batchId = $null
    }
    if ($facultyId) {
        Remove-VerificationApiFixture '/api/v1/faculty' $facultyId 'employeeCode' $token
        $script:facultyId = $null
    }
    if ($departmentId) {
        Remove-VerificationApiFixture '/api/v1/departments' $departmentId 'code' $token
        $script:departmentId = $null
    }
    if ($verificationAccounts.Count -ne 0 -or $studentId -or $sectionId -or $batchId -or $facultyId -or $departmentId) {
        throw 'Verification-owned fixture tracking is not empty.'
    }
    Write-Host 'PASS cleanup: no tracked fixtures remain for this verification run.'
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
    $outputLog = Join-Path $project 'target/phase4-live-server.log'
    $errorLog = Join-Path $project 'target/phase4-live-server-error.log'
    $server = Start-Process -FilePath $JavaPath -ArgumentList '-jar',
        'target/campusflow-backend-0.0.1-SNAPSHOT.jar','--server.port=0','--server.address=127.0.0.1' `
        -WorkingDirectory $project -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $server.Refresh()
        if ($server.HasExited) { throw 'Application exited during startup. Inspect target/phase4-live-server*.log locally.' }
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
    # Phase 3: new API checks share the temporary department and server above.
    $batchBody = @{name="Phase 3 Batch $token";startYear=2023;endYear=2027;departmentId=$departmentId;active=$true}
    $batch = Assert-Api POST '/api/v1/batches' 201 $batchBody
    $batchId = $batch.id
    $null = Assert-Api POST '/api/v1/batches' 409 $batchBody
    $null = Assert-Api GET "/api/v1/batches/$batchId" 200
    $null = Assert-Api GET "/api/v1/batches?departmentId=$departmentId" 200
    $batchBody.name = "Phase 3 Updated Batch $token"
    $updatedBatch = Assert-Api PUT "/api/v1/batches/$batchId" 200 $batchBody
    if ($updatedBatch.name -cne $batchBody.name) { throw 'Batch update mismatch.' }
    $sectionBody = @{name='A';batchId=$batchId;capacity=60;active=$true}
    $section = Assert-Api POST '/api/v1/sections' 201 $sectionBody
    $sectionId = $section.id
    $null = Assert-Api POST '/api/v1/sections' 409 $sectionBody
    $null = Assert-Api GET "/api/v1/sections/$sectionId" 200
    $null = Assert-Api GET "/api/v1/sections?batchId=$batchId" 200
    $sectionBody.capacity = 50
    $updatedSection = Assert-Api PUT "/api/v1/sections/$sectionId" 200 $sectionBody
    if ($updatedSection.capacity -ne 50) { throw 'Section update mismatch.' }
    $studentEnrollment = "PH3_$token"
    $studentBody = @{enrollmentNumber=$studentEnrollment;rollNumber=$token;fullName="Student $token";
        email="student-$token@example.edu";departmentId=$departmentId;batchId=$batchId;sectionId=$sectionId;
        currentSemester=1;admissionYear=2023}
    $student = Assert-Api POST '/api/v1/students' 201 $studentBody
    $studentId = $student.id
    $null = Assert-Api POST '/api/v1/students' 409 $studentBody
    $null = Assert-Api GET "/api/v1/students/$studentId" 200
    $null = Assert-Api GET '/api/v1/students?page=0&size=1' 200
    foreach ($queryFilter in @("departmentId=$departmentId","batchId=$batchId","sectionId=$sectionId",
            "enrollmentNumber=$studentEnrollment","name=$token")) {
        $page = Assert-Api GET "/api/v1/students?$queryFilter" 200
        if ($page.totalElements -ne 1 -or $page.content[0].id -ne $studentId) { throw 'Student filter mismatch.' }
    }
    $null = Assert-Api DELETE "/api/v1/students/$studentId" 405
    $null = Assert-Api DELETE "/api/v1/sections/$sectionId" 409
    $null = Assert-Api DELETE "/api/v1/batches/$batchId" 409
    $studentBody.fullName = "Updated Student $token"
    $studentBody.currentSemester = 2
    $updatedStudent = Assert-Api PUT "/api/v1/students/$studentId" 200 $studentBody
    if ($updatedStudent.currentSemester -ne 2 -or $updatedStudent.fullName -cne $studentBody.fullName) { throw 'Student update mismatch.' }
    $graduated = Assert-Api PATCH "/api/v1/students/$studentId/status" 200 @{status='GRADUATED'}
    if ($graduated.status -cne 'GRADUATED') { throw 'Student status mismatch.' }
    $combined = Assert-Api GET "/api/v1/students?departmentId=$departmentId&batchId=$batchId&sectionId=$sectionId&semester=2&status=GRADUATED&name=$token&enrollmentNumber=$studentEnrollment" 200
    if ($combined.totalElements -ne 1 -or $combined.content[0].id -ne $studentId) { throw 'Combined student filter mismatch.' }
    $emptyPage = Assert-Api GET "/api/v1/students?batchId=$batchId&page=1&size=1" 200
    if ($emptyPage.content.Count -ne 0) { throw 'Student pagination mismatch.' }
    $invalid = $studentBody.Clone(); $invalid.enrollmentNumber='CHANGED'
    $null = Assert-Api PUT "/api/v1/students/$studentId" 409 $invalid
    $invalid = $studentBody.Clone(); $invalid.currentSemester=0
    $null = Assert-Api PUT "/api/v1/students/$studentId" 400 $invalid
    $invalid = $studentBody.Clone(); $invalid.batchId=[long]::MaxValue
    $null = Assert-Api PUT "/api/v1/students/$studentId" 404 $invalid
    $null = Assert-Api PATCH "/api/v1/students/$studentId/status" 400 @{status='UNKNOWN'}
    $invalidBatch = $batchBody.Clone(); $invalidBatch.endYear=2022
    $null = Assert-Api POST '/api/v1/batches' 400 $invalidBatch
    $null = Assert-Api POST '/api/v1/sections' 400 @{name='B';batchId=$batchId;capacity=0;active=$true}
    # Full security checks use only accounts linked to this run's temporary profiles.
    $null=Assert-Api GET '/api/v1/admin/accounts' 401 -Anonymous
    $null=Assert-Api POST '/api/v1/departments' 401 @{name='Denied';code='DENIED'} -Anonymous
    $studentLogin="login-student-$token@example.edu".ToLowerInvariant()
    $facultyLogin="login-faculty-$token@example.edu".ToLowerInvariant()
    $initialPassword=[guid]::NewGuid().ToString('N')
    $newStudent=Assert-Api POST '/api/v1/admin/accounts' 201 @{email=$studentLogin;initialPassword=$initialPassword;role='STUDENT';studentId=$studentId}
    $verificationAccounts+=@{id=$newStudent.id;email=$newStudent.email;role='STUDENT';profileId=$studentId}
    $newFaculty=Assert-Api POST '/api/v1/admin/accounts' 201 @{email=$facultyLogin;initialPassword=$initialPassword;role='FACULTY';facultyId=$facultyId}
    $verificationAccounts+=@{id=$newFaculty.id;email=$newFaculty.email;role='FACULTY';profileId=$facultyId}
    $null=Assert-Api GET '/api/v1/admin/accounts' 200
    $null=Assert-Api GET "/api/v1/admin/accounts/$($newStudent.id)" 200
    $null=Assert-Api POST '/api/v1/admin/accounts' 400 @{email="denied-$token@example.edu";initialPassword=$initialPassword;role='ADMIN'}
    $studentCredentials=Assert-Api POST '/api/v1/auth/login' 200 @{email=$studentLogin;password=$initialPassword} -Anonymous
    $facultyCredentials=Assert-Api POST '/api/v1/auth/login' 200 @{email=$facultyLogin;password=$initialPassword} -Anonymous
    $own=Assert-Api GET '/api/v1/auth/me' 200 -Bearer $studentCredentials.accessToken
    if($own.student.id -ne $studentId -or $own.account.role -cne 'STUDENT') {throw 'Student ownership mismatch.'}
    $ownFaculty=Assert-Api GET '/api/v1/auth/me' 200 -Bearer $facultyCredentials.accessToken
    if($ownFaculty.faculty.id -ne $facultyId) {throw 'Faculty ownership mismatch.'}
    $null=Assert-Api GET "/api/v1/students/$studentId" 403 -Bearer $studentCredentials.accessToken
    $null=Assert-Api GET '/api/v1/admin/accounts' 403 -Bearer $facultyCredentials.accessToken
    $null=Assert-Api POST '/api/v1/departments' 403 @{name='Denied';code='DENIED'} -Bearer $studentCredentials.accessToken
    $directory=Assert-Api GET '/api/v1/directory/faculty' 200 -Bearer $studentCredentials.accessToken
    if($directory.content.Count -gt 0 -and $directory.content[0].PSObject.Properties.Name -contains 'employeeCode') {throw 'Directory exposes private fields.'}
    $null=Assert-Api POST '/api/v1/auth/login' 401 @{email=$studentLogin;password=[guid]::NewGuid().ToString('N')} -Anonymous
    $null=Assert-Api POST '/api/v1/auth/login' 401 @{email="unknown-$token@example.edu";password=$initialPassword} -Anonymous
    $parts=$studentCredentials.accessToken.Split('.')
    $first=if($parts[2][0] -eq 'A') {'B'} else {'A'}
    $tampered=$parts[0]+'.'+$parts[1]+'.'+$first+$parts[2].Substring(1)
    $null=Assert-Api GET '/api/v1/auth/me' 401 -Bearer $tampered
    $null=Assert-Api PATCH "/api/v1/admin/accounts/$($newStudent.id)/status" 200 @{enabled=$false}
    $null=Assert-Api GET '/api/v1/auth/me' 401 -Bearer $studentCredentials.accessToken
    $null=Assert-Api POST '/api/v1/auth/login' 401 @{email=$studentLogin;password=$initialPassword} -Anonymous
    $null=Assert-Api PATCH "/api/v1/admin/accounts/$($newStudent.id)/status" 200 @{enabled=$true}
    $null=Assert-Api GET '/api/v1/auth/me' 401 -Bearer $studentCredentials.accessToken
    $studentCredentials=Assert-Api POST '/api/v1/auth/login' 200 @{email=$studentLogin;password=$initialPassword} -Anonymous
    $changedPassword=[guid]::NewGuid().ToString('N')
    $null=Assert-Api POST '/api/v1/auth/change-password' 204 @{currentPassword=$initialPassword;newPassword=$changedPassword} -Bearer $studentCredentials.accessToken
    $null=Assert-Api GET '/api/v1/auth/me' 401 -Bearer $studentCredentials.accessToken
    $null=Assert-Api POST '/api/v1/auth/refresh' 401 @{refreshToken=$studentCredentials.refreshToken} -Anonymous
    $studentCredentials=Assert-Api POST '/api/v1/auth/login' 200 @{email=$studentLogin;password=$changedPassword} -Anonymous
    $rotated=Assert-Api POST '/api/v1/auth/refresh' 200 @{refreshToken=$studentCredentials.refreshToken} -Anonymous
    $null=Assert-Api GET '/api/v1/auth/me' 200 -Bearer $rotated.accessToken
    $null=Assert-Api POST '/api/v1/auth/refresh' 401 @{refreshToken=$studentCredentials.refreshToken} -Anonymous
    $null=Assert-Api GET '/api/v1/auth/me' 401 -Bearer $rotated.accessToken
    $null=Assert-Api POST '/api/v1/auth/refresh' 401 @{refreshToken=$rotated.refreshToken} -Anonymous
    $lastSession=Assert-Api POST '/api/v1/auth/login' 200 @{email=$studentLogin;password=$changedPassword} -Anonymous
    $null=Assert-Api POST '/api/v1/auth/logout' 204 @{refreshToken=$lastSession.refreshToken} -Bearer $lastSession.accessToken
    $null=Assert-Api POST '/api/v1/auth/refresh' 401 @{refreshToken=$lastSession.refreshToken} -Anonymous
    Remove-VerificationFixtures
    $unitTotal=(Get-ChildItem target/surefire-reports/TEST-*.xml | ForEach-Object {([xml](Get-Content -Raw $_.FullName)).testsuite.tests} | Measure-Object -Sum).Sum
    $integrationTotal=(Get-ChildItem target/failsafe-reports/TEST-*.xml | ForEach-Object {([xml](Get-Content -Raw $_.FullName)).testsuite.tests} | Measure-Object -Sum).Sum
    Write-Host "Actual Maven totals: $unitTotal regular tests; $integrationTotal PostgreSQL integration tests."
    $verificationSucceeded = $true
    Write-Host 'PASS Phase 4 database suite and live authentication/Phase 1-3 API checks.'
} finally {
    # Reuse exactly the same ownership checks and dependency order on failure/retry.
    if ($baseUrl) {
        try { Remove-VerificationFixtures }
        catch {
            $cleanupSucceeded = $false
            Write-Warning $_.Exception.Message
            Write-Warning "Preserved fixture IDs: accounts=$($verificationAccounts.id -join ','); student=$studentId; section=$sectionId; batch=$batchId; faculty=$facultyId; department=$departmentId. Inspect ownership locally before deleting."
        }
    }
    End-VerificationSecurity -Snapshot $securityEnvironment -BaseUrl $baseUrl
    if ($server) {
        try {
            $server.Refresh()
            if (-not $server.HasExited) { Stop-Process -Id $server.Id -ErrorAction Stop }
        } catch {
            $cleanupSucceeded = $false
            Write-Warning 'Temporary server cleanup needs local inspection.'
        }
    }
    $env:DB_PASSWORD = $previousDbPassword
    $env:PGPASSWORD = $previousPgPassword
    foreach ($key in $savedDatabaseEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key,$savedDatabaseEnvironment[$key],'Process')
    }
    Pop-Location
    if (-not $verificationSucceeded -or -not $cleanupSucceeded) {
        Write-Host 'FAIL Phase 4 verification or cleanup. Review the error above; no schema reset was attempted.'
    }
}

if (-not $cleanupSucceeded) { throw 'Verification cleanup incomplete; see warnings above.' }
