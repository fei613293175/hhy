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
    "docs/03-continuity", "artifacts/context", "artifacts/handoffs"
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

$registry = Join-Path $worktreeBase ".assignments"
if (Test-Path -LiteralPath $registry -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $registry -Filter "*.json" -File) {
        $assignment = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
        if ($assignment.status -ne "ACTIVE") { continue }
        foreach ($existing in @($assignment.allowed_paths)) {
            foreach ($claim in $claims) {
                if (Test-ClaimsOverlap $claim ([string]$existing)) {
                    throw "Path claim overlaps active worker '$($assignment.worker_id)': $claim <-> $existing"
                }
            }
        }
    }
}

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
    created_at = [DateTimeOffset]::UtcNow.ToString("o")
    destructive_cleanup_supported = $false
}

if ($Execute) {
    New-Item -ItemType Directory -Path $worktreeBase -Force | Out-Null
    New-Item -ItemType Directory -Path $registry -Force | Out-Null
    $gitOutput = (& $GitExecutable -C $repository worktree add --quiet --detach $target $baseCommit 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "git worktree add failed (no automatic cleanup attempted): $gitOutput" }
    $assignmentPath = Join-Path $registry "$WorkerId.json"
    $record["assignment_path"] = $assignmentPath
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $assignmentPath -Encoding UTF8
}

$record | ConvertTo-Json -Depth 6
