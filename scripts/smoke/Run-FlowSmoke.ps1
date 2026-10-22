# Real-API three-flow smoke (Horse -> Training -> Medical) against a running local FE+BE and a NEW seeded local DB.
# Writes test data into $env:RH_DATABASE (never point it at a shared/demo DB). Passwords are read from the
# seed helper's DPAPI file and never printed; JWTs/evidence go to an output folder outside the repository.
[CmdletBinding()]
param(
    [string]$FrontendBase = 'http://127.0.0.1:5173',
    [string]$BackendBase = 'http://127.0.0.1:8080',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
if ($env:RH_DATABASE -cnotmatch '^racehorse_local_[a-z0-9_]{1,70}$') { throw 'NEW_LOCAL_DATABASE_NAME_REQUIRED' }
$accountsPath = Join-Path $env:LOCALAPPDATA "Racehorse/LocalDb/$env:RH_DATABASE/accounts.dpapi.json"
if (-not (Test-Path -LiteralPath $accountsPath)) { throw 'SEEDED_LOCAL_ACCOUNTS_REQUIRED' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $env:LOCALAPPDATA ("Racehorse/smoke/$env:RH_DATABASE/" + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss')) }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$tokens = [ordered]@{}
foreach ($a in (Get-Content -LiteralPath $accountsPath -Raw | ConvertFrom-Json)) {
    $password = [Net.NetworkCredential]::new('', ($a.encryptedPassword | ConvertTo-SecureString)).Password
    $body = @{ email = $a.email; password = $password } | ConvertTo-Json -Compress
    $password = $null
    $response = Invoke-RestMethod -Method Post -Uri "$FrontendBase/api/auth/login" -ContentType 'application/json' -Body $body
    $body = $null
    $tokens[$a.role] = @{ email = $a.email; token = $response.data.accessToken }
}
$tokens | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'tokens.json') -Encoding utf8
"Logged in $($tokens.Count) roles through $FrontendBase"

$env:RH_BASE = $FrontendBase
$env:RH_BE_BASE = $BackendBase
$failed = $false
Push-Location $OutputDirectory
try {
    foreach ($flow in 'flow1', 'flow2', 'flow2b', 'flow3') {
        & python (Join-Path $PSScriptRoot "$flow.py")
        if ($LASTEXITCODE -ne 0) { $failed = $true; break }
    }
} finally {
    Pop-Location
    Remove-Item -LiteralPath (Join-Path $OutputDirectory 'tokens.json') -Force -ErrorAction SilentlyContinue
}
"Evidence: $OutputDirectory"
if ($failed) { throw 'FLOW_SMOKE_SCRIPT_ERROR' }
foreach ($e in Get-ChildItem -LiteralPath $OutputDirectory -Filter 'flow*-evidence.json') {
    $r = Get-Content -LiteralPath $e.FullName -Raw | ConvertFrom-Json
    if ($r.pass -ne $r.requests) { throw "FLOW_SMOKE_FAILED $($e.Name) $($r.pass)/$($r.requests)" }
}
'FLOW_SMOKE_PASS'
