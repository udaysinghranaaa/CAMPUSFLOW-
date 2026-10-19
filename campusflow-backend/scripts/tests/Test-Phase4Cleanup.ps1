# No database, server, credentials or external test modules are needed.
$ErrorActionPreference = 'Stop'
$source = Join-Path (Split-Path -Parent $PSScriptRoot) 'Verify-Phase4.ps1'
$errors = $null
$tree = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$null, [ref]$errors)
if ($errors.Count) { throw ($errors | Out-String) }
foreach ($name in @('Remove-VerificationApiFixture','Remove-VerificationFixtures')) {
    $definition = $tree.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name}, $true)
    if (-not $definition) { throw "Missing cleanup function $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}
function Assert-True($Condition, $Message) { if (-not $Condition) { throw $Message } }
function Assert-Throws([scriptblock]$Action, [string]$Expected) {
    try { & $Action } catch {
        if ($_.Exception.Message -notlike "*$Expected*") { throw }
        return
    }
    throw "Expected failure containing: $Expected"
}
function Reset-Fixtures {
    $script:baseUrl = 'http://verification.invalid'
    $script:token = 'OWNED123'
    $script:verificationAccounts = @(@{id=10}, @{id=11})
    $script:studentId = 1; $script:sectionId = 2; $script:batchId = 3
    $script:facultyId = 4; $script:departmentId = 5
    $script:events = [Collections.Generic.List[string]]::new()
    $script:accountFailure = $false; $script:studentFailure = $false
    $script:forbiddenPath = $null; $script:deleteFailure = $null
    $script:records = @{
        '/api/v1/sections/2' = [pscustomobject]@{id=2;batchId=3}
        '/api/v1/batches/3' = [pscustomobject]@{id=3;departmentId=5}
        '/api/v1/faculty/4' = [pscustomobject]@{id=4;employeeCode='OWNED123'}
        '/api/v1/departments/5' = [pscustomobject]@{id=5;code='OWNED123'}
    }
}
# Stub only SQL/HTTP boundaries. Exercise the production cleanup orchestration and ownership guards.
function Remove-VerificationAccounts {
    if ($accountFailure) { throw 'Account SQL failed' }
    if ($verificationAccounts.Count) { $events.Add('accounts'); $script:verificationAccounts=@() }
}
function Remove-VerificationStudent {
    Assert-True ($verificationAccounts.Count -eq 0) 'Accounts must be removed first'
    if ($studentFailure) { throw 'Student SQL failed' }
    $events.Add('student'); $script:studentId=$null
}
function Invoke-RestMethod {
    param($Uri, $Method, $TimeoutSec, $Headers)
    $path = $Uri.Substring($baseUrl.Length)
    $status = if ($path -eq $forbiddenPath) {403} elseif (-not $records.ContainsKey($path)) {404} else {200}
    if ($status -ne 200) {
        $failure = [Exception]::new("HTTP $status")
        $failure | Add-Member -NotePropertyName Response -NotePropertyValue ([pscustomobject]@{StatusCode=$status})
        throw $failure
    }
    return $records[$path]
}
function Assert-Api {
    param($Method, $Path, $Expected)
    if ($Method -eq 'DELETE') {
        if ($Path -eq $deleteFailure) { throw 'DELETE failed' }
        Assert-True ($Expected -eq 204) 'Unexpected delete expectation'
        $events.Add($Path); $records.Remove($Path)
    } elseif ($Method -eq 'GET') {
        Assert-True ($Expected -eq 404 -and -not $records.ContainsKey($Path)) 'Deleted fixture must be absent'
    } else { throw 'Unexpected HTTP operation' }
}
$passed = 0
Reset-Fixtures
Remove-VerificationFixtures
Assert-True (($events -join ',') -ceq 'accounts,student,/api/v1/sections/2,/api/v1/batches/3,/api/v1/faculty/4,/api/v1/departments/5') 'Incorrect dependency order'
Assert-True ($records.Count -eq 0) 'Owned fixtures remain'
$passed++
Remove-VerificationFixtures
Assert-True ($events.Count -eq 6) 'Repeated cleanup must not issue duplicate deletes'
$passed++
Reset-Fixtures; $script:accountFailure=$true
Assert-Throws { Remove-VerificationFixtures } 'Account SQL failed'
Assert-True ($events.Count -eq 0 -and $facultyId -eq 4 -and $departmentId -eq 5) 'Account failure must preserve all dependents'
$passed++
Reset-Fixtures; $script:studentFailure=$true
Assert-Throws { Remove-VerificationFixtures } 'Student SQL failed'
Assert-True ($events.Count -eq 1 -and $records.Count -eq 4) 'Student failure must preserve parents'
$passed++
Reset-Fixtures; $records['/api/v1/faculty/4'].employeeCode='REAL-FACULTY'
Assert-Throws { Remove-VerificationFixtures } 'ownership mismatch'
Assert-True ($records.ContainsKey('/api/v1/faculty/4') -and $records.ContainsKey('/api/v1/departments/5')) 'Unowned faculty and department must survive'
$passed++
Reset-Fixtures; $records.Remove('/api/v1/sections/2')
Remove-VerificationFixtures
Assert-True ($records.Count -eq 0) 'An already absent fixture must allow safe cleanup to continue'
$passed++
Reset-Fixtures; $script:forbiddenPath='/api/v1/faculty/4'
Assert-Throws { Remove-VerificationFixtures } 'HTTP 403'
Assert-True ($facultyId -eq 4 -and $departmentId -eq 5) 'Authorization errors must not count as absence'
$passed++
Reset-Fixtures; $records['/api/v1/departments/5'].code='REAL-DEPARTMENT'
Assert-Throws { Remove-VerificationFixtures } 'ownership mismatch'
Assert-True ($records.ContainsKey('/api/v1/departments/5')) 'Unowned department must survive'
$passed++
Reset-Fixtures; $records['/api/v1/sections/2'].id=999
Assert-Throws { Remove-VerificationFixtures } 'ownership mismatch'
Assert-True ($records.Count -eq 4 -and $sectionId -eq 2) 'Mismatched ID must prevent deletion'
$passed++
Reset-Fixtures; $script:deleteFailure='/api/v1/sections/2'
Assert-Throws { Remove-VerificationFixtures } 'DELETE failed'
Assert-True ($sectionId -eq 2 -and $records.Count -eq 4) 'Failed deletion must retain tracking for retry'
$script:deleteFailure=$null
Remove-VerificationFixtures
Assert-True ($records.Count -eq 0) 'Retry must finish cleanup'
$passed++
Write-Host "PASS $passed targeted cleanup regression scenarios (mocked SQL/HTTP boundaries)."
