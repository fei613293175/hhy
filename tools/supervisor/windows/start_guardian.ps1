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
    if ($override -and (Test-Path -LiteralPath $override)) {
        return [pscustomobject]@{ Executable = $override; Prefix = @() }
    }
    $bundled = Get-ChildItem `
        -Path (Join-Path $env:USERPROFILE ".cache\codex-runtimes") `
        -Filter "python.exe" -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "\\dependencies\\python\\python.exe$" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($bundled) {
        return [pscustomobject]@{ Executable = $bundled.FullName; Prefix = @() }
    }
    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py -and $py.Source -notmatch "\\WindowsApps\\") {
        return [pscustomobject]@{ Executable = $py.Source; Prefix = @("-3") }
    }
    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python -and $python.Source -notmatch "\\WindowsApps\\") {
        return [pscustomobject]@{ Executable = $python.Source; Prefix = @() }
    }
    throw "Python was not found. Install Python or set HHY_PYTHON."
}

$invocation = Get-PythonInvocation
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $runtime "guardian-$stamp.log"
& $invocation.Executable @($invocation.Prefix) $entry --repo $repo --daemon *>> $log
exit $LASTEXITCODE
