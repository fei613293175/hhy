[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$TaskName = "HHY-Governance-V5-Guardian"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$entry = Join-Path $repo "tools\supervisor\windows\start_guardian.ps1"
if (-not (Test-Path -LiteralPath (Join-Path $repo ".git"))) {
    throw "Not a Git repository: $repo"
}
if (-not (Test-Path -LiteralPath $entry)) {
    throw "Guardian start script is missing: $entry"
}

$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$entry`" -RepoPath `"$repo`""
$action = New-ScheduledTaskAction -Execute $powershell -Argument $argument
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Days 3650)
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
    -Description "Always-on bounded Guardian for HHY Governance V5.0" `
    -Force | Out-Null

Write-Output "Installed task '$TaskName' for $repo"
Write-Output "Guardian will start at the next Windows logon and will start Supervisor automatically when needed."
