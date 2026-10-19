# Shared development verification helpers. No User/Machine environment writes.
function Begin-VerificationSecurity {
    $snapshot=@{}
    foreach($name in @('JWT_SECRET','CAMPUSFLOW_VERIFY_ACCESS_TOKEN','CAMPUSFLOW_VERIFY_REFRESH_TOKEN')) {
        $snapshot[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
    }
    $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN=$null
    $env:CAMPUSFLOW_VERIFY_REFRESH_TOKEN=$null
    if(-not $env:JWT_SECRET) {
        $bytes=New-Object byte[] 32; $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes); $env:JWT_SECRET=[Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
    }
    return $snapshot
}
function Connect-VerificationAdmin {
    param([string]$BaseUrl)
    $loginEmail=$env:CAMPUSFLOW_VERIFY_ADMIN_EMAIL
    if(-not $loginEmail) { $loginEmail=Read-Host 'Existing ADMIN login email' }
    $loginPassword=$env:CAMPUSFLOW_VERIFY_ADMIN_PASSWORD
    if(-not $loginPassword) {
        $secure=Read-Host 'Existing ADMIN password' -AsSecureString
        try { $loginPassword=[Net.NetworkCredential]::new('', $secure).Password } finally { $secure.Dispose() }
    }
    try {
        $body=@{email=$loginEmail;password=$loginPassword}|ConvertTo-Json -Compress
        $credential=Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body
        $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN=$credential.accessToken
        $env:CAMPUSFLOW_VERIFY_REFRESH_TOKEN=$credential.refreshToken
        Write-Host 'PASS authenticated verification ADMIN session'
    } catch { throw 'Administrator login failed. Bootstrap an administrator explicitly or check local credentials.' }
    finally { $loginPassword=$null;$body=$null;$credential=$null }
}
function End-VerificationSecurity {
    param([hashtable]$Snapshot,[string]$BaseUrl)
    try {
        if($BaseUrl -and $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN -and $env:CAMPUSFLOW_VERIFY_REFRESH_TOKEN) {
            try {
                $null=Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/logout" -Method Post -ContentType 'application/json' `
                    -Headers @{Authorization="Bearer $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN"} `
                    -Body (@{refreshToken=$env:CAMPUSFLOW_VERIFY_REFRESH_TOKEN}|ConvertTo-Json -Compress)
            } catch { Write-Warning 'Could not revoke the temporary verification admin session; inspect locally.' }
        }
    } finally {
        if($Snapshot) { foreach($name in $Snapshot.Keys) { [Environment]::SetEnvironmentVariable($name,$Snapshot[$name],'Process') } }
    }
}
