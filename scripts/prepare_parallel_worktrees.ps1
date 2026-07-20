[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $RepositoryRoot,
    [Parameter(Mandatory = $true)] [string] $WorktreeRoot,
    [Parameter(Mandatory = $true)] [string] $WorkerId,
    [Parameter(Mandatory = $true)] [string[]] $AllowedPath,
    [string] $BaseRef = "HEAD",
    [string] $GitExecutable = "git",
    [switch] $Execute
)

$ErrorActionPreference = "Stop"

function Get-FullPath([string] $PathValue) {
    return [System.IO.Path]::GetFullPath($PathValue).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
}

function Test-IsWithin([string] $Child, [string] $Parent) {
    $childFull = (Get-FullPath $Child) + [System.IO.Path]::DirectorySeparatorChar
    $parentFull = (Get-FullPath $Parent) + [System.IO.Path]::DirectorySeparatorChar
    return $childFull.StartsWith($parentFull, [System.StringComparison]::OrdinalIgnoreCase)
}

function Normalize-Claim([string] $Claim) {
    $normalized = $Claim.Replace("\", "/").Trim()
    while ($normalized.StartsWith("./", [System.StringComparison]::Ordinal)) {
        $normalized = $normalized.Substring(2)
    }
    $normalized = $normalized.TrimStart([char]'/').TrimEnd([char]'/')
    if ([string]::IsNullOrWhiteSpace($normalized)) { throw "Allowed path cannot be empty or repository-wide" }
    return $normalized
}

function Get-ClaimRoot([string] $Claim) {
    $wildcard = $Claim.IndexOfAny([char[]]"*?")
    $root = if ($wildcard -ge 0) { $Claim.Substring(0, $wildcard) } else { $Claim }
    return $root.TrimEnd("/")
}

function Test-ClaimsOverlap([string] $Left, [string] $Right) {
    $leftRoot = Get-ClaimRoot $Left
    $rightRoot = Get-ClaimRoot $Right
    if ([string]::IsNullOrWhiteSpace($leftRoot) -or [string]::IsNullOrWhiteSpace($rightRoot)) { return $true }
    return $leftRoot.Equals($rightRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        $leftRoot.StartsWith($rightRoot + "/", [System.StringComparison]::OrdinalIgnoreCase) -or
        $rightRoot.StartsWith($leftRoot + "/", [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-MaxDelegatedWorkers([string] $Repository) {
    $policyPath = Join-Path $Repository ".continuity\CONTINUITY_POLICY.yaml"
    if (-not (Test-Path -LiteralPath $policyPath -PathType Leaf)) {
        throw "Authoritative continuity policy is missing: $policyPath"
    }
    $policyText = Get-Content -LiteralPath $policyPath -Raw
    $match = [regex]::Match($policyText, "(?ms)^parallel_development:\s*.*?^\s{2}max_delegated_workers:\s*(\d+)\s*$")
    if (-not $match.Success) { throw "Cannot read parallel_development.max_delegated_workers from authoritative policy" }
    $value = [int]$match.Groups[1].Value
    if ($value -lt 1 -or $value -gt 3) { throw "Authoritative max_delegated_workers must remain between 1 and 3" }
    return $value
}

function Read-Assignments([string] $Registry) {
    $rows = @()
    if (-not (Test-Path -LiteralPath $Registry -PathType Container)) { return $rows }
    foreach ($file in Get-ChildItem -LiteralPath $Registry -Filter "*.json" -File) {
        try {
            $assignment = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        } catch {
            throw "Invalid assignment record (fail closed): $($file.FullName)"
        }
        if (-not $assignment.worker_id -or -not $assignment.status) {
            throw "Incomplete assignment record (fail closed): $($file.FullName)"
        }
        $rows += $assignment
    }
    return $rows
}

if ($WorkerId -notmatch "^[a-z0-9][a-z0-9._-]{1,63}$") {
    throw "WorkerId must match ^[a-z0-9][a-z0-9._-]{1,63}$"
}

$repository = Get-FullPath $RepositoryRoot
if (-not (Test-Path -LiteralPath $repository -PathType Container)) { throw "RepositoryRoot does not exist: $repository" }
$worktreeBase = Get-FullPath $WorktreeRoot
$target = Get-FullPath (Join-Path $worktreeBase $WorkerId)
if ((Test-IsWithin $target $repository) -or $target.Equals($repository, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Scratch worktree must be outside the canonical repository"
}
if (-not (Test-IsWithin $target $worktreeBase)) { throw "Resolved target escaped WorktreeRoot" }
if (Test-Path -LiteralPath $target) { throw "Target already exists; this script never deletes or reuses worktrees: $target" }

$claims = @($AllowedPath | ForEach-Object { Normalize-Claim $_ } | Sort-Object -Unique)
$forbidden = @(
    ".continuity", "CURRENT_STATUS.yaml", "NEXT_TASK.yaml", "releases",
    "docs/03-continuity", "artifacts/context", "artifacts/handoffs",
    "AGENTS.md", "START_HERE.md", "CHANGELOG.md", "CONTINUITY_POLICY.yaml",
    "config/CONTINUITY_POLICY.yaml", "catalogs/change_request_index.csv",
    "docs/01-architecture/adr", ".github/workflows", ".githooks"
)
foreach ($claim in $claims) {
    if ($claim -in @("*", "**", ".")) { throw "Repository-wide claims are forbidden: $claim" }
    foreach ($blocked in $forbidden) {
        if (Test-ClaimsOverlap $claim $blocked) { throw "Worker claim overlaps coordinator-owned path: $claim <-> $blocked" }
    }
}

$actualRoot = (& $GitExecutable -C $repository rev-parse --show-toplevel 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw "RepositoryRoot is not a Git worktree: $actualRoot" }
if (-not (Get-FullPath $actualRoot).Equals($repository, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "RepositoryRoot must be the Git worktree root: $actualRoot"
}
$baseCommit = (& $GitExecutable -C $repository rev-parse "$BaseRef`^{commit}" 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $baseCommit -notmatch "^[0-9a-fA-F]{40,64}$") {
    throw "Cannot resolve BaseRef '$BaseRef': $baseCommit"
}

$commonDirRaw = (& $GitExecutable -C $repository rev-parse --git-common-dir 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw "Cannot resolve Git common directory: $commonDirRaw" }
$commonDir = if ([System.IO.Path]::IsPathRooted($commonDirRaw)) { Get-FullPath $commonDirRaw } else { Get-FullPath (Join-Path $repository $commonDirRaw) }
$registry = Join-Path $commonDir "hhy-parallel-assignments"
$maxWorkers = Get-MaxDelegatedWorkers $repository

function Test-AssignmentSet([object[]] $Assignments) {
    $active = @($Assignments | Where-Object { $_.status -eq "ACTIVE" })
    if (@($active | Where-Object { $_.worker_id -eq $WorkerId }).Count -gt 0) {
        throw "WorkerId already has an active assignment: $WorkerId"
    }
    if ($active.Count -ge $maxWorkers) {
        throw "Delegated worker capacity reached: $($active.Count)/$maxWorkers"
    }
    foreach ($assignment in $active) {
        foreach ($existing in @($assignment.allowed_paths)) {
            foreach ($claim in $claims) {
                if (Test-ClaimsOverlap $claim ([string]$existing)) {
                    throw "Path claim overlaps active worker '$($assignment.worker_id)': $claim <-> $existing"
                }
            }
        }
    }
}

Test-AssignmentSet (Read-Assignments $registry)

$record = [ordered]@{
    schema = "hhy.parallel-worktree-assignment/v1"
    status = if ($Execute) { "ACTIVE" } else { "PLANNED" }
    worker_id = $WorkerId
    repository_root = $repository
    worktree_root = $worktreeBase
    target_path = $target
    base_ref = $BaseRef
    base_commit = $baseCommit.ToLowerInvariant()
    allowed_paths = $claims
    coordinator_owned_paths = $forbidden
    max_delegated_workers = $maxWorkers
    created_at = [DateTimeOffset]::UtcNow.ToString("o")
    destructive_cleanup_supported = $false
}

if ($Execute) {
    New-Item -ItemType Directory -Path $worktreeBase -Force | Out-Null
    New-Item -ItemType Directory -Path $registry -Force | Out-Null
    $lockPath = Join-Path $registry ".registry.lock"
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    try {
        Test-AssignmentSet (Read-Assignments $registry)
        $gitOutput = (& $GitExecutable -C $repository worktree add --quiet --detach $target $baseCommit 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw "git worktree add failed (no automatic cleanup attempted): $gitOutput" }
        $hooks = Join-Path $target ".hhy-no-commit-hooks"
        New-Item -ItemType Directory -Path $hooks -Force | Out-Null
        $preCommitHook = Join-Path $hooks "pre-commit"
        $prePushHook = Join-Path $hooks "pre-push"
        "#!/bin/sh`necho 'Delegated scratch worktrees may not commit' >&2`nexit 1`n" | Set-Content -LiteralPath $preCommitHook -Encoding ASCII
        "#!/bin/sh`necho 'Delegated scratch worktrees may not push' >&2`nexit 1`n" | Set-Content -LiteralPath $prePushHook -Encoding ASCII
        if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
            & chmod 700 -- $preCommitHook $prePushHook
            if ($LASTEXITCODE -ne 0) { throw "Cannot make delegated worktree hooks executable" }
        }
        & $GitExecutable -C $repository config extensions.worktreeConfig true
        & $GitExecutable -C $target config --worktree core.hooksPath .hhy-no-commit-hooks
        $assignmentPath = Join-Path $registry "$WorkerId.json"
        $record["assignment_path"] = $assignmentPath
        $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $assignmentPath -Encoding UTF8
    } finally {
        $lock.Dispose()
    }
}

$record | ConvertTo-Json -Depth 6
