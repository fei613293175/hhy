[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$runtime = Join-Path $repo "governance\runtime\supervisor"
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$marker = Join-Path $runtime "STOP_REQUESTED"
Set-Content -LiteralPath $marker -Value "OWNER_REQUESTED_STOP" -Encoding UTF8
Write-Output "Stop requested. The Supervisor will stop before the next batch and preserve evidence."
