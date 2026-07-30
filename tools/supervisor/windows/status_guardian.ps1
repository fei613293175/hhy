[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$TaskName = "HHY-Governance-V5-Guardian"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$task = Get-ScheduledTask -TaskName $TaskName -TaskPath "\" -ErrorAction SilentlyContinue
if ($task) {
    $info = Get-ScheduledTaskInfo -TaskName $TaskName -TaskPath "\"
    Write-Output ("Scheduled task: {0}; State: {1}; LastRun: {2}; LastResult: {3}" -f $TaskName, $task.State, $info.LastRunTime, $info.LastTaskResult)
} else {
    Write-Output "Scheduled task '$TaskName' is not installed."
}

$state = Join-Path $repo "governance\runtime\supervisor\GUARDIAN_STATE.json"
if (Test-Path -LiteralPath $state) {
    Get-Content -Raw -LiteralPath $state
}
