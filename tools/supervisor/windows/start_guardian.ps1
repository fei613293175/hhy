[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$entry = Join-Path $repo "tools\supervisor\supervisor_guardian.py"
$runtime = Join-Path $repo "governance\runtime\supervisor"
if (-not (Test-Path -LiteralPath $entry)) {
    throw "Guardian entrypoint is missing: $entry"
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null

function Get-PythonInvocation {
    $override = $env:HHY_PYTHON
    if ($override) { return [pscustomobject]@{ Executable = $override; Prefix = @() } }
    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) { return [pscustomobject]@{ Executable = $py.Source; Prefix = @("-3") } }
    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python) { return [pscustomobject]@{ Executable = $python.Source; Prefix = @() } }
    throw "Python was not found. Install Python or set HHY_PYTHON."
}

$invocation = Get-PythonInvocation
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $runtime "guardian-$stamp.log"
& $invocation.Executable @($invocation.Prefix) $entry --repo $repo --daemon *>> $log
exit $LASTEXITCODE
