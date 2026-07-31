[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$guardian = Join-Path $repo "tools\supervisor\windows\install_guardian_task.ps1"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $guardian -RepoPath $repo
$start = Join-Path $repo "tools\supervisor\windows\start_guardian.ps1"
Start-Process powershell.exe `
    -WindowStyle Hidden `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $start, "-RepoPath", $repo)
Write-Output "Autonomous mode is installed and Guardian was started immediately."
Write-Output "Guardian owns future Supervisor starts, worker takeover, bounded recovery, and Windows-login recovery."
