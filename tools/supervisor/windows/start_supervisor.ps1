[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [switch]$SkipStartupDelay
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
$runtime = Join-Path $repo "governance\runtime\supervisor"
$config = Join-Path $repo "tools\supervisor\supervisor_config.yaml"
$entry = Join-Path $repo "tools\supervisor\hhy_supervisor.py"
$complete = Join-Path $runtime "PROGRAM_COMPLETE.lock"

if (Test-Path -LiteralPath $complete) {
    Write-Output "PROGRAM_COMPLETE is present; Supervisor will not start."
    exit 0
}
if (-not (Test-Path -LiteralPath $entry)) {
    throw "Supervisor entrypoint is missing: $entry"
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$delay = 120
if (Test-Path -LiteralPath $config) {
    $match = Select-String -LiteralPath $config -Pattern "startup_delay_seconds:\s*(\d+)" | Select-Object -First 1
    if ($match -and $match.Matches[0].Groups[1].Value) {
        $delay = [int]$match.Matches[0].Groups[1].Value
    }
}
if (-not $SkipStartupDelay -and $delay -gt 0) {
    Start-Sleep -Seconds $delay
}

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
    $python3 = Get-Command python3.exe -ErrorAction SilentlyContinue
    if ($python3 -and $python3.Source -notmatch "\\WindowsApps\\") {
        return [pscustomobject]@{ Executable = $python3.Source; Prefix = @() }
    }
    throw "Python was not found. Install Python or set HHY_PYTHON."
}

$invocation = Get-PythonInvocation
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $runtime "supervisor-$stamp.log"
$arguments = @($entry, "--repo", $repo)
& $invocation.Executable @($invocation.Prefix) @arguments *>> $log
exit $LASTEXITCODE
