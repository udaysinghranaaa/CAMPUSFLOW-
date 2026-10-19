[CmdletBinding()]
param([string]$MavenPath,[string]$JavaPath)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/Verification-Security.ps1"
$saved=@{};foreach($key in @('DB_HOST','DB_PORT','DB_NAME','DB_USERNAME','DB_PASSWORD','PGPASSWORD')){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$server=$null;$baseUrl=$null;$security=$null;$passed=$false
$owned=@{departmentId=$null;code=$null;sessions=@();blocks=@();coverage=@();subjects=@();terms=@();rooms=@()}
$accounts=@();$student=$null;$sections=@();$batch=$null;$faculty=@()
function Api {
 param([string]$Method,[string]$Path,[int]$Expected,$Body=$null,[string]$Bearer=$env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN)
 $request=@{Uri="$baseUrl$Path";Method=$Method;UseBasicParsing=$true;TimeoutSec=20;Headers=@{Authorization="Bearer $Bearer"}}
 if($null -ne $Body){$request.ContentType='application/json';$request.Body=$Body|ConvertTo-Json -Depth 8 -Compress}
 try{$response=Invoke-WebRequest @request;$status=[int]$response.StatusCode;$content=$response.Content}catch{if(-not $_.Exception.Response){throw 'Phase5 API connection failed; inspect locally.'};$status=[int]$_.Exception.Response.StatusCode;$content=$null}
 if($status -ne $Expected){throw "Phase5 API check failed: $Method $Path expected $Expected received $status. Response omitted to protect credentials."}
 Write-Host "PASS $Method $Path -> $status"
 if($content){return($content|ConvertFrom-Json)}
}
function Psql {
 param([string]$File,[hashtable]$Variables)
 $exe='C:/Program Files/PostgreSQL/18/bin/psql.exe';if(-not(Test-Path -LiteralPath $exe)){$exe=(Get-Command psql -ErrorAction Stop).Source}
 $env:PGPASSWORD=$env:DB_PASSWORD
 $arguments=@('-X','-w','-q','-t','-A','-h',$env:DB_HOST,'-p',$env:DB_PORT,'-U',$env:DB_USERNAME,'-d',$env:DB_NAME,'-v','ON_ERROR_STOP=1')
 foreach($key in $Variables.Keys){$arguments+=@('-v',"${key}=$($Variables[$key])")};$arguments+=@('-f',$File)
 $result=& $exe @arguments
 if($LASTEXITCODE -ne 0){throw 'Phase5 SQL verification/cleanup failed. Preserve remaining fixture IDs.'};return "$result".Trim()
}
function Remove-OwnedApi {
 param([string]$Path,$Record,[string]$Property,[string]$Value)
 if(-not $Record){return}
 try{$current=Invoke-RestMethod -Uri "$baseUrl$Path/$($Record.id)" -Headers @{Authorization="Bearer $env:CAMPUSFLOW_VERIFY_ACCESS_TOKEN"} -TimeoutSec 15}catch{if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404){return};throw 'Cannot verify fixture ownership; preserve parent rows.'}
 if([string]$current.$Property -cne $Value){throw 'Fixture identity mismatch; no deletion attempted.'}
 $null=Api DELETE "$Path/$($Record.id)" 204
 $null=Api GET "$Path/$($Record.id)" 404
}
function Cleanup {
 if(-not $owned.departmentId){return}
 $manifest=$owned|ConvertTo-Json -Depth 8 -Compress
 $result=Psql "$PSScriptRoot/Cleanup-Phase5.sql" @{manifest=$manifest}
 if($result -cne 'CLEANED'){throw 'Phase5 fixture cleanup was not confirmed.'}
 foreach($account in @($script:accounts)){
  $result=Psql "$PSScriptRoot/Cleanup-Phase4Account.sql" @{account_id=$account.id;email=$account.email;role=$account.role;profile_id=$account.profileId}
  if($result -ne [string]$account.id){throw 'Verification account ownership cleanup failed.'}
  $script:accounts=@($script:accounts|Where-Object{$_.id -ne $account.id})
 }
 if($script:student){$result=Psql "$PSScriptRoot/Cleanup-Phase3Student.sql" @{student_id=$student.id;enrollment_number=$student.enrollmentNumber};if($result -ne [string]$student.id){throw 'Student ownership cleanup failed.'};$script:student=$null}
 foreach($record in @($script:sections)){Remove-OwnedApi '/api/v1/sections' $record 'batchId' ([string]$batch.id);$script:sections=@($script:sections|Where-Object{$_.id -ne $record.id})}
 if($script:batch){Remove-OwnedApi '/api/v1/batches' $batch 'departmentId' ([string]$owned.departmentId);$script:batch=$null}
 foreach($record in @($script:faculty)){Remove-OwnedApi '/api/v1/faculty' $record 'employeeCode' $record.employeeCode;$script:faculty=@($script:faculty|Where-Object{$_.id -ne $record.id})}
 Remove-OwnedApi '/api/v1/departments' ([pscustomobject]@{id=$owned.departmentId}) 'code' $owned.code
 $script:owned.departmentId=$null
 Write-Host 'PASS Phase5 cleanup: captured timetable, account and academic fixtures are absent.'
}
Push-Location $project
try {
 foreach($pair in @{DB_HOST='localhost';DB_PORT='5432';DB_NAME='campusflow_db';DB_USERNAME='campusflow_app'}.GetEnumerator()){if(-not [Environment]::GetEnvironmentVariable($pair.Key,'Process')){[Environment]::SetEnvironmentVariable($pair.Key,$pair.Value,'Process')}}
 if(-not $env:DB_PASSWORD){$secret=Read-Host 'CampusFlow database password (process only)' -AsSecureString;try{$env:DB_PASSWORD=[Net.NetworkCredential]::new('',$secret).Password}finally{$secret.Dispose();Remove-Variable secret}}
 $security=Begin-VerificationSecurity
 # Runs all regular + real PostgreSQL tests (including Phase5 concurrency) and Phase1-4 live regressions/cleanup.
 & "$PSScriptRoot/Verify-Phase4.ps1" -MavenPath $MavenPath -JavaPath $JavaPath
 if(-not $JavaPath){$JavaPath=(Get-Command java.exe -ErrorAction SilentlyContinue).Source;if(-not $JavaPath){$JavaPath='C:/Program Files/Java/jdk-26.0.1/bin/java.exe'}}
 $server=Start-Process -FilePath $JavaPath -ArgumentList '-jar','target/campusflow-backend-0.0.1-SNAPSHOT.jar','--server.port=0','--server.address=127.0.0.1' -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput 'target/phase5-live.log' -RedirectStandardError 'target/phase5-live-error.log'
 $deadline=(Get-Date).AddSeconds(90)
 while((Get-Date)-lt $deadline){$server.Refresh();if($server.HasExited){throw 'Phase5 verification-owned server exited.'};$match=Select-String -LiteralPath 'target/phase5-live.log' -Pattern 'Tomcat started on port (\d+)'|Select-Object -Last 1;if($match){$baseUrl='http://127.0.0.1:'+$match.Matches[0].Groups[1].Value;break};Start-Sleep -Milliseconds 250}
 if(-not $baseUrl){throw 'Phase5 server startup timeout.'}
 Connect-VerificationAdmin -BaseUrl $baseUrl
 $health=Api GET '/api/campus/health' 200;if($health.status -cne 'UP'){throw 'Health mismatch.'}
 $tag=[guid]::NewGuid().ToString('N').Substring(0,16).ToUpperInvariant();$owned.code="P5_$tag"
 $dep=Api POST '/api/v1/departments' 201 @{name='Phase5 verification';code=$owned.code;description='Temporary verification-owned record'};$owned.departmentId=$dep.id
 foreach($i in 0..1){$faculty+=Api POST '/api/v1/faculty' 201 @{fullName="Phase5 Faculty $tag $i";email="p5f$i-$($tag.ToLowerInvariant())@example.test";employeeCode="P5F${i}_$tag";departmentId=$dep.id;designation='Lecturer'}}
 $zone=try{[TimeZoneInfo]::FindSystemTimeZoneById('Asia/Kolkata')}catch{[TimeZoneInfo]::FindSystemTimeZoneById('India Standard Time')};$day=[TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow,$zone).Date;$offset=(([int]$day.DayOfWeek+6)%7);$monday=$day.AddDays(-$offset);$date=$monday.ToString('yyyy-MM-dd');$end=$monday.AddDays(14).ToString('yyyy-MM-dd')
 $batch=Api POST '/api/v1/batches' 201 @{name="Phase5 $tag";startYear=$monday.Year;endYear=($monday.Year+4);departmentId=$dep.id;active=$true}
 foreach($i in 0..1){$sections+=Api POST '/api/v1/sections' 201 @{name="S$i";batchId=$batch.id;capacity=30;active=$true}}
 foreach($i in 0..1){$room=Api POST '/api/v1/rooms' 201 @{code="P5R${i}_$tag";name='Verification room';building='Verification';floor='1';type='CLASSROOM';capacity=60;active=$true};$owned.rooms+=@{id=$room.id;code=$room.code}}
 $subject=Api POST '/api/v1/subjects' 201 @{code="P5_$tag";name='Verification subject';departmentId=$dep.id;description=$null;active=$true};$owned.subjects+=@{id=$subject.id;code=$subject.code}
 $term=Api POST '/api/v1/timetable/terms' 201 @{name="Phase5 $tag";departmentId=$dep.id;semester=1;startDate=$date;endDate=$end;active=$true};$owned.terms+=@{id=$term.id;name=$term.name}
 $body=@{academicTermId=$term.id;sectionId=$sections[0].id;subjectId=$subject.id;facultyId=$faculty[0].id;roomId=$owned.rooms[0].id;dayOfWeek=1;startTime='09:00';endTime='10:00';effectiveStartDate=$date;effectiveEndDate=$end;status='DRAFT';sessionType='LECTURE'}
 $session=Api POST '/api/v1/timetable/sessions' 201 $body;$owned.sessions+=@{id=$session.id;term_id=$term.id}
 $null=Api PATCH "/api/v1/timetable/sessions/$($session.id)/publish" 200
 $clash=$body.Clone();$clash.sectionId=$sections[1].id;$clash.roomId=$owned.rooms[1].id;$null=Api POST '/api/v1/timetable/sessions' 409 $clash
 $clash=$body.Clone();$clash.sectionId=$sections[1].id;$clash.facultyId=$faculty[1].id;$null=Api POST '/api/v1/timetable/sessions' 409 $clash
 $clash=$body.Clone();$clash.facultyId=$faculty[1].id;$clash.roomId=$owned.rooms[1].id;$null=Api POST '/api/v1/timetable/sessions' 409 $clash
 $student=Api POST '/api/v1/students' 201 @{enrollmentNumber="P5_$tag";rollNumber=$tag;fullName="Phase5 Student $tag";email="p5s-$($tag.ToLowerInvariant())@example.test";phoneNumber=$null;departmentId=$dep.id;batchId=$batch.id;sectionId=$sections[0].id;currentSemester=1;admissionYear=$monday.Year}
 $password=[guid]::NewGuid().ToString('N')
 $sa=Api POST '/api/v1/admin/accounts' 201 @{email="p5student-$($tag.ToLowerInvariant())@example.test";initialPassword=$password;role='STUDENT';studentId=$student.id};$accounts+=@{id=$sa.id;email=$sa.email;role='STUDENT';profileId=$student.id}
 $fa=Api POST '/api/v1/admin/accounts' 201 @{email="p5faculty-$($tag.ToLowerInvariant())@example.test";initialPassword=$password;role='FACULTY';facultyId=$faculty[0].id};$accounts+=@{id=$fa.id;email=$fa.email;role='FACULTY';profileId=$faculty[0].id}
 $sc=Api POST '/api/v1/auth/login' 200 @{email=$sa.email;password=$password};$fc=Api POST '/api/v1/auth/login' 200 @{email=$fa.email;password=$password};$password=$null
 $studentWeek=Api GET "/api/v1/timetable/student/me?weekStart=$date" 200 -Bearer $sc.accessToken;if($studentWeek.sessions.Count -ne 1 -or $studentWeek.sessions[0].sessionId -ne $session.id){throw 'Student own timetable mismatch.'}
 $null=Api GET "/api/v1/timetable/sections/$($sections[1].id)?weekStart=$date" 403 -Bearer $sc.accessToken
 $null=Api POST '/api/v1/timetable/sessions' 403 $body -Bearer $sc.accessToken
 $facultyWeek=Api GET "/api/v1/timetable/faculty/me?weekStart=$date" 200 -Bearer $fc.accessToken;if($facultyWeek.sessions.Count -ne 1){throw 'Faculty own timetable mismatch.'}
 $null=Api GET '/api/v1/timetable/student/me' 403 -Bearer $fc.accessToken
 $block=@{facultyId=$faculty[1].id;date=$date;startTime='11:00';endTime='12:00';reason='Private verification-only reason'}
 $null=Api POST '/api/v1/timetable/unavailability' 403 $block -Bearer $fc.accessToken
 $block.facultyId=$faculty[0].id;$block.startTime='09:30';$block.endTime='10:30';$null=Api POST '/api/v1/timetable/unavailability' 409 $block -Bearer $fc.accessToken
 $block.startTime='11:00';$block.endTime='12:00';$savedBlock=Api POST '/api/v1/timetable/unavailability' 201 $block -Bearer $fc.accessToken;$owned.blocks+=@{id=$savedBlock.id;faculty_id=$faculty[0].id}
 $available=Api GET "/api/v1/faculty-finder/$($faculty[0].id)/availability?date=$date&startTime=12:00&endTime=13:00" 200 -Bearer $sc.accessToken;if($available.state -cne 'UNKNOWN'){throw 'Missing coverage must yield UNKNOWN.'}
 $coverage=Api PUT "/api/v1/timetable/terms/$($term.id)/coverage" 200 @{facultyId=$faculty[0].id;dayMask=63;startTime='08:00';endTime='18:00'};$owned.coverage+=@{id=$coverage.id;term_id=$term.id;faculty_id=$faculty[0].id}
 foreach($check in @(@{from='09:00';to='10:00';state='BUSY'},@{from='11:00';to='12:00';state='UNAVAILABLE'},@{from='12:00';to='13:00';state='FREE'})){$result=Api GET "/api/v1/faculty-finder/$($faculty[0].id)/availability?date=$date&startTime=$($check.from)&endTime=$($check.to)" 200 -Bearer $sc.accessToken;if($result.state -cne $check.state -or $result.PSObject.Properties.Name -contains 'reason' -or $result.PSObject.Properties.Name -contains 'email'){throw 'Finder state/privacy mismatch.'}}
 $null=Api POST "/api/v1/timetable/sessions/$($session.id)/exceptions" 201 @{exceptionDate=$date;type='RESCHEDULED';startTime='08:00';endTime='09:00';facultyId=$faculty[0].id;roomId=$owned.rooms[0].id;reason='Verification reschedule'}
 $null=Api POST "/api/v1/timetable/sessions/$($session.id)/exceptions" 201 @{exceptionDate=$date;type='CANCELLED';reason='Verification cancellation'}
 $view=Api GET "/api/v1/timetable/student/me?weekStart=$date" 200 -Bearer $sc.accessToken;if($view.sessions.Count -ne 0){throw 'Dated cancellation not reflected.'}
 $null=Api PATCH "/api/v1/timetable/sessions/$($session.id)/cancel" 200
 Cleanup
 $passed=$true
 Write-Host 'PASS Phase5: all Maven/PostgreSQL tests, Phase1-4 regressions, live scheduling/ownership/finder checks and fixture cleanup.'
} finally {
 try{if($baseUrl -and $owned.departmentId){Cleanup}}catch{$passed=$false;Write-Warning "Cleanup blocked. Preserve owned department $($owned.departmentId), term/session/account IDs and inspect locally."}
 try{End-VerificationSecurity -Snapshot $security -BaseUrl $baseUrl}finally{if($server){$server.Refresh();if(-not $server.HasExited){$server.Kill();$server.WaitForExit(10000)|Out-Null}};foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')};$sc=$null;$fc=$null;$password=$null;Pop-Location}
 if(-not $passed){Write-Host 'FAIL Phase5 verification or cleanup; no database reset or broad deletion was attempted.'}
}
if(-not $passed){throw 'Phase5 did not pass all required verification.'}