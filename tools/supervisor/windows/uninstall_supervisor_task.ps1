[CmdletBinding()]
param(
    [string]$TaskName = "HHY-Governance-V5-Supervisor"
)

$ErrorActionPreference = "Stop"
$task = Get-ScheduledTask -TaskName $TaskName -TaskPath "\" -ErrorAction SilentlyContinue
if ($null -eq $task) {
    Write-Output "Task '$TaskName' is not installed."
    exit 0
}

Unregister-ScheduledTask -TaskName $TaskName -TaskPath "\" -Confirm:$false
Write-Output "Uninstalled task '$TaskName'. Runtime logs and stop reports were preserved."
