[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$TaskName = "HHY-Governance-V5-Supervisor"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$entry = Join-Path $repo "tools\supervisor\windows\start_supervisor.ps1"
if (-not (Test-Path -LiteralPath (Join-Path $repo ".git"))) {
    throw "Not a Git repository: $repo"
}
if (-not (Test-Path -LiteralPath $entry)) {
    throw "Supervisor start script is missing: $entry"
}

$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$entry`" -RepoPath `"$repo`""
$action = New-ScheduledTaskAction -Execute $powershell -Argument $argument
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 8)
$principal = New-ScheduledTaskPrincipal `
    -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType Interactive `
    -RunLevel Limited

Register-ScheduledTask `
    -TaskName $TaskName `
    -TaskPath "\" `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Description "Bounded external supervisor for HHY Governance V5.0" `
    -Force | Out-Null

Write-Output "Installed task '$TaskName' for $repo"
Write-Output "The task starts at user logon; the script applies the configured startup delay."
