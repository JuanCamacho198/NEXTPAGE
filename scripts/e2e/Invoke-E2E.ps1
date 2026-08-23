# Tag-selective Maestro E2E runner. Composes the maestro CLI command and
# propagates maestro's exit code unchanged (design D6: a red run must never
# be masked by the runner).
#
# Usage examples:
#   pwsh scripts/e2e/Invoke-E2E.ps1 -Tags smoke
#   pwsh scripts/e2e/Invoke-E2E.ps1 -Tags regression,sync
#   pwsh scripts/e2e/Invoke-E2E.ps1 -FlowPaths <ordered flow list>   # stateful suites
#
# Exit codes:
#   0   = all flows green
#   2   = no adb device in state "device" was found
#   else = maestro's own exit code, propagated unchanged
#
# --no-reinstall-driver is ALWAYS passed: the Maestro driver apps are
# permanently installed on the target device, and HyperOS/MIUI auto-cancels
# new-package adb installs within ~50ms (INSTALL_FAILED_USER_RESTRICTED),
# which would fail every run before it starts.
[CmdletBinding()]
param(
    # Tags passed to maestro as --include-tags t1,t2,...
    [string[]]$Tags,

    # Single flow file or directory to test. Defaults to .maestro/flows.
    [string]$FlowPath,

    # Explicit ordered list of flow files. Use when flows have fixture
    # dependencies that require deterministic execution order.
    [string[]]$FlowPaths,

    # Device serial for maestro --device. Defaults to the first
    # `adb devices` row in state "device" (exit 2 when none).
    [string]$Serial,

    # Print the composed command without running it. Always exits 0.
    [switch]$DryRun
)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if (-not ($Serial -and $Serial.Trim())) {
    $deviceRow = adb devices |
        Where-Object { $_ -match '^\S+\s+device\s*$' } |
        Select-Object -First 1
    if (-not $deviceRow) {
        Write-Error 'No adb device in state "device" found.' -ErrorAction Continue
        exit 2
    }
    $Serial = ($deviceRow.Trim() -split '\s+')[0]
}

if ($FlowPaths -and $FlowPaths.Count -gt 0) {
    $targets = @($FlowPaths)
}
elseif ($FlowPath) {
    $targets = @($FlowPath)
}
else {
    $targets = @(Join-Path $repoRoot '.maestro/flows')
}

$maestroArgs = @('test') + $targets + @('--no-reinstall-driver')
if ($Tags -and $Tags.Count -gt 0) {
    $maestroArgs += @('--include-tags', ($Tags -join ','))
}
$maestroArgs += @('--device', $Serial)

if ($DryRun) {
    Write-Output ('maestro ' + ($maestroArgs -join ' '))
    exit 0
}

& maestro @maestroArgs
exit $LASTEXITCODE
