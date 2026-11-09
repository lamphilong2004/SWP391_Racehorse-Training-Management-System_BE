# Optional, explicit deletion AFTER installing/verifying and reviewing a separate new DB.
# Never runs from Install-LocalDatabase. No connection killing, reset or automatic restore.
[CmdletBinding(SupportsShouldProcess, ConfirmImpact='High')]
param(
    [Parameter(Mandatory)][string]$OldDatabase,
    [Parameter(Mandatory)][string]$ConfirmOldDatabase,
    [Parameter(Mandatory)][string]$ExpectedMachine,
    [Parameter(Mandatory)][string]$BackupFile,
    [switch]$Execute
)
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
if($OldDatabase -notmatch '^racehorse_[a-z0-9_]{1,100}$'){throw 'OLD_RACEHORSE_DATABASE_NAME_REQUIRED'}
if($OldDatabase -ieq $env:RH_DATABASE){throw 'OLD_AND_NEW_DATABASE_MUST_DIFFER'}
if($OldDatabase -cne $ConfirmOldDatabase){throw 'EXACT_OLD_DATABASE_CONFIRMATION_REQUIRED'}
if($ExpectedMachine -ine $env:COMPUTERNAME){throw 'EXPLICIT_LOCAL_MACHINE_CONFIRMATION_REQUIRED'}
if(-not [IO.Path]::IsPathFullyQualified($BackupFile) -or $BackupFile.StartsWith('\\') -or [IO.Path]::GetExtension($BackupFile) -cne '.bak' -or (Test-Path -LiteralPath $BackupFile)){throw 'NEW_ABSOLUTE_BACKUP_FILE_REQUIRED'}
if(-not $Execute){'PLAN_ONLY: install and verify the new DB, stop old BE/SSMS connections, then repeat with -Execute; no SQL connection or write';return}
if(-not $PSCmdlet.ShouldProcess("$ExpectedMachine / $OldDatabase",'COPY_ONLY full backup with CHECKSUM; RESTORE VERIFYONLY; DROP only this old database')){return}
& (Join-Path $PSScriptRoot 'Verify-LocalDatabase.ps1')|Out-Null
Initialize-LocalJdbc
$names=@('RH_OLD_DATABASE','RH_RETIRE_CONFIRMATION','RH_RETIRE_MACHINE','RH_RETIRE_BACKUP')
$previous=@{};foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name)}
try {
    $env:RH_OLD_DATABASE=$OldDatabase;$env:RH_RETIRE_CONFIRMATION=$ConfirmOldDatabase
    $env:RH_RETIRE_MACHINE=$ExpectedMachine;$env:RH_RETIRE_BACKUP=$BackupFile
    Invoke-LocalJdbc 'retire'
} finally {foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name])}}
