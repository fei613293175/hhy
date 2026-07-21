[CmdletBinding()]
param(
    [switch]$Check,
    [string]$GitPath
)

$ErrorActionPreference = 'Stop'

function Resolve-HhyGit {
    param([string]$RequestedPath, [bool]$CheckOnly)

    if ($RequestedPath) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            throw "Requested Git executable does not exist: $RequestedPath"
        }
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $persisted = [Environment]::GetEnvironmentVariable('HHY_GIT_BIN', 'User')
    if ($persisted) {
        if (Test-Path -LiteralPath $persisted -PathType Leaf) {
            return (Resolve-Path -LiteralPath $persisted).Path
        }
        if ($CheckOnly) {
            throw "Persisted HHY_GIT_BIN does not exist: $persisted"
        }
    }

    $command = Get-Command git.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command -and (Test-Path -LiteralPath $command.Source -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $command.Source).Path
    }

    $candidates = @(
        (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe'),
        (Join-Path $env:ProgramFiles 'Git\cmd\git.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Git\cmd\git.exe')
    ) | Where-Object { $_ }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'Git executable not found. Install Git for Windows or run again with -GitPath <absolute git.exe>.'
}

$gitExe = Resolve-HhyGit -RequestedPath $GitPath -CheckOnly $Check.IsPresent
$gitDir = Split-Path -Parent $gitExe
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$pathParts = @($userPath -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$pathConfigured = [bool]($pathParts | Where-Object {
    $_.TrimEnd('\') -ieq $gitDir.TrimEnd('\')
})
$persistedGit = [Environment]::GetEnvironmentVariable('HHY_GIT_BIN', 'User')
$overrideConfigured = $persistedGit -and ($persistedGit -ieq $gitExe)
$changed = $false

if ($Check) {
    if (-not $overrideConfigured) {
        throw "User HHY_GIT_BIN is not configured for $gitExe"
    }
    if (-not $pathConfigured) {
        throw "User PATH does not contain $gitDir"
    }
} else {
    if (-not $pathConfigured) {
        $pathParts += $gitDir
        [Environment]::SetEnvironmentVariable('Path', (($pathParts -join ';') + ';'), 'User')
        $changed = $true
    }
    if (-not $overrideConfigured) {
        [Environment]::SetEnvironmentVariable('HHY_GIT_BIN', $gitExe, 'User')
        $changed = $true
    }
}

# Make the invoking shell and every child process usable immediately. Existing parent
# applications keep their original environment block and may be restarted later.
$env:HHY_GIT_BIN = $gitExe
if (-not (($env:Path -split ';') | Where-Object { $_.TrimEnd('\') -ieq $gitDir.TrimEnd('\') })) {
    $env:Path = "$gitDir;$env:Path"
}
$versionOutput = & $gitExe --version 2>&1
$gitExitCode = $LASTEXITCODE
$version = $versionOutput | Select-Object -First 1
if ($gitExitCode -ne 0 -or $version -notmatch '^git version ') {
    throw "Configured Git failed execution: $gitExe"
}

[ordered]@{
    status = 'PASS'
    mode = if ($Check) { 'CHECK' } else { 'INSTALL' }
    changed = $changed
    git = $gitExe
    git_dir = $gitDir
    version = $version
    user_hhy_git_bin = [Environment]::GetEnvironmentVariable('HHY_GIT_BIN', 'User')
    user_path_contains_git = [bool](([Environment]::GetEnvironmentVariable('Path', 'User') -split ';') |
        Where-Object { $_.TrimEnd('\') -ieq $gitDir.TrimEnd('\') })
    note = 'Restart long-running parent applications only when convenient so their inherited PATH refreshes.'
} | ConvertTo-Json -Depth 3
