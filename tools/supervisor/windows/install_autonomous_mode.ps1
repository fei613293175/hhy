[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$guardian = Join-Path $repo "tools\supervisor\windows\install_guardian_task.ps1"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $guardian -RepoPath $repo
Write-Output "Autonomous mode is installed. Guardian owns future Supervisor starts and bounded recovery."
