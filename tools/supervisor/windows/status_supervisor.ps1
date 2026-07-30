[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [string]$TaskName = "HHY-Governance-V5-Supervisor"
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

function Get-PythonInvocation {
    $override = $env:HHY_PYTHON
    if ($override) { return [pscustomobject]@{ Executable = $override; Prefix = @() } }
    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) { return [pscustomobject]@{ Executable = $py.Source; Prefix = @("-3") } }
    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python) { return [pscustomobject]@{ Executable = $python.Source; Prefix = @() } }
    $python3 = Get-Command python3.exe -ErrorAction SilentlyContinue
    if ($python3) { return [pscustomobject]@{ Executable = $python3.Source; Prefix = @() } }
    throw "Python was not found. Install Python or set HHY_PYTHON."
}

$invocation = Get-PythonInvocation
$entry = Join-Path $repo "tools\supervisor\supervisor_status.py"
& $invocation.Executable @($invocation.Prefix) $entry --repo $repo
exit $LASTEXITCODE
