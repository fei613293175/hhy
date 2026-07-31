[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$guardian = Join-Path $repo "tools\supervisor\windows\install_guardian_task.ps1"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $guardian -RepoPath $repo
$runtime = Join-Path $repo "governance\runtime\supervisor"
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
Set-Content -LiteralPath (Join-Path $runtime "AUTONOMOUS_MODE_INSTALLED") -Value "ENABLED" -Encoding UTF8
$start = Join-Path $repo "tools\supervisor\windows\start_guardian.ps1"
Start-Process powershell.exe `
    -WindowStyle Hidden `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $start, "-RepoPath", $repo)
Write-Output "Autonomous mode is installed and Guardian was started immediately."
Write-Output "Guardian owns future Supervisor starts, worker takeover, bounded recovery, and Windows-login recovery."
