[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][uri]$ApiBaseUri,
    [switch]$CreateDraft
)
# Runs only against an operator-started local stack and its verified private database.
# Without -CreateDraft, this script refuses to create a horse; no SQL DML is issued.
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
if(-not $ApiBaseUri.IsLoopback -or $ApiBaseUri.Scheme -ne 'http' -or $ApiBaseUri.UserInfo -or $ApiBaseUri.Query -or $ApiBaseUri.Fragment -or $ApiBaseUri.AbsolutePath -ne '/') {
    throw 'LOOPBACK_API_ORIGIN_REQUIRED'
}
& (Join-Path $PSScriptRoot 'Verify-LocalDatabase.ps1')|Out-Null
# The child verifier initializes its own script scope; initialize this runner's classpath too.
Initialize-LocalJdbc
$receiptPath=Get-LocalReceiptPath
$receipt=Get-Content -LiteralPath $receiptPath -Raw|ConvertFrom-Json
if(-not $receipt.seeded){throw 'OWN_LOCAL_TEST_ACCOUNTS_REQUIRED'}
$accounts=Get-Content -LiteralPath (Join-Path (Split-Path $receiptPath) 'accounts.dpapi.json') -Raw|ConvertFrom-Json
$manager=@($accounts|Where-Object role -eq 'CLUB_MANAGER')
if($manager.Count -ne 1){throw 'ONE_LOCAL_MANAGER_REQUIRED'}
$fixture=Get-Content -LiteralPath (Join-Path $LocalDbRoot 'docs/examples/team-horse-draft.json') -Raw|ConvertFrom-Json
$report=[ordered]@{status='BLOCKED';database=$env:RH_DATABASE;api=$ApiBaseUri.GetLeftPart([UriPartial]::Authority);browserVerified=$false;results=@()}
$reportPath=Join-Path (Split-Path $receiptPath) ('horse-result-'+[guid]::NewGuid().ToString('N')+'.json')
$handler=[Net.Http.HttpClientHandler]::new();$handler.AllowAutoRedirect=$false;$handler.UseCookies=$false;$handler.UseProxy=$false
$client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(20)
$token=$null;$password=$null
function Read-Account {
    $env:RH_ACCOUNT_EMAIL=$manager[0].email
    try { return (((Invoke-LocalJdbc 'account') -join '')|ConvertFrom-Json) }
    finally {Remove-Item Env:RH_ACCOUNT_EMAIL -ErrorAction SilentlyContinue}
}
function Request-Local([string]$Method,[string]$Path,$Body=$null) {
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),[uri]::new($ApiBaseUri,$Path))
    try {
        if($token){$request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)}
        if($null -ne $Body){$request.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Compress),[Text.Encoding]::UTF8,'application/json')}
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $status=[int]$response.StatusCode
            $report.results+=@{method=$Method;path=$Path;httpStatus=$status}
            if($status -notin @(200,201,204)){throw "HTTP_${status}_LOCAL_SMOKE_FAILED"}
            $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if($status -eq 204){return $null}
            $envelope=$text|ConvertFrom-Json
            if(-not $envelope.success){throw 'API_SUCCESS_ENVELOPE_REQUIRED'}
            return $envelope.data
        } finally {$response.Dispose()}
    } finally {$request.Dispose()}
}
try {
    $before=Read-Account
    $secure=$manager[0].encryptedPassword|ConvertTo-SecureString
    $password=[Net.NetworkCredential]::new('',$secure).Password
    $login=Request-Local 'POST' '/api/auth/login' @{email=$manager[0].email;password=$password}
    $password=$null;$token=$login.accessToken;$login=$null
    if(-not $token){throw 'REAL_BACKEND_TOKEN_REQUIRED'}
    $me=Request-Local 'GET' '/api/auth/me'
    $after=Read-Account
    # Confirm this API actually uses the selected local SQL DB before any business write.
    if($me.id -ne $after.id -or $me.roles -notcontains 'CLUB_MANAGER' -or $null -eq $after.lastLogin -or $after.lastLogin -eq $before.lastLogin){throw 'API_DATABASE_LOGIN_READBACK_MISMATCH'}
    $list=Request-Local 'GET' ('/api/horses?q='+[uri]::EscapeDataString($fixture.horseCode))
    $matches=@($list.items|Where-Object horseCode -ceq $fixture.horseCode)
    if($matches.Count -gt 1){throw 'HORSE_CODE_NOT_UNIQUE'}
    if($matches.Count -eq 0){
        if(-not $CreateDraft){throw 'HORSE_ABSENT_USE_EXPLICIT_CREATE_DRAFT_ON_YOUR_NEW_STACK'}
        $horse=Request-Local 'POST' '/api/horses' $fixture
    } else {$horse=$matches[0]}
    if($horse.id -le 0){throw 'SERVER_HORSE_ID_REQUIRED'}
    $detail=Request-Local 'GET' ('/api/horses/'+$horse.id)
    $env:RH_HORSE_CODE=$fixture.horseCode
    $sql=(((Invoke-LocalJdbc 'horse') -join '')|ConvertFrom-Json)
    if($detail.id -ne $sql.id -or $detail.horseCode -cne $fixture.horseCode -or $sql.name -cne $fixture.name -or $detail.name -cne $sql.name -or $sql.lifecycle -cne 'DRAFT' -or $detail.lifecycle -cne 'DRAFT'){throw 'HORSE_HTTP_SQL_READBACK_MISMATCH'}
    $refresh=Request-Local 'GET' ('/api/horses/'+$horse.id)
    if($refresh.id -ne $sql.id -or $refresh.horseCode -cne $sql.horseCode){throw 'HORSE_REFRESH_MISMATCH'}
    Request-Local 'POST' '/api/auth/logout'|Out-Null
    $report.horse=@{id=$sql.id;horseCode=$sql.horseCode;lifecycle=$sql.lifecycle}
    $report.status='LOCAL_HTTP_SQL_HORSE_PASS_BROWSER_PENDING'
} catch {
    # Never serialize exceptions, HTTP bodies, passwords or JWTs into the report.
    $report.status='BLOCKED'
    $failure=$_.Exception.Message
    if($failure -cnotmatch '^[A-Z0-9_]{1,100}$'){$failure='LOCAL_HORSE_SMOKE_FAILED_SEE_PRIVATE_OPERATOR_CONTEXT'}
    $report.failure=$failure
    throw $failure
} finally {
    $token=$null;$password=$null;$accounts=$null
    Remove-Item Env:RH_HORSE_CODE -ErrorAction SilentlyContinue
    $client.Dispose()
    $report|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $reportPath -Encoding utf8
    "Local result: $reportPath (no credentials/tokens; browser verification remains separate)"
}
