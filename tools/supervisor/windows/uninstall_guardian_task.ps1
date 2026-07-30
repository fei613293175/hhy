[CmdletBinding()]
param(
    [string]$TaskName = "HHY-Governance-V5-Guardian"
)

$ErrorActionPreference = "Stop"
$task = Get-ScheduledTask -TaskName $TaskName -TaskPath "\" -ErrorAction SilentlyContinue
if ($task) {
    Unregister-ScheduledTask -TaskName $TaskName -TaskPath "\" -Confirm:$false
    Write-Output "Uninstalled task '$TaskName'"
} else {
    Write-Output "Scheduled task '$TaskName' is not installed."
}
