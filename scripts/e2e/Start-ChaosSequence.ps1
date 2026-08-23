# Chaos orchestrator (design D1 + D6): bounces device WiFi around the offline
# window and verifies recovery.
#
# Default order (offline-first staging, D1):
#   adbe wifi off -> maestro(stage) -> sleep -> adbe wifi on
#   -> Wait-AdbDevice (timeout => skip recovery, propagate exit)
#   -> maestro(recovery) -> propagate exit
#
# -StageOnline fallback (design open question 2): stage runs online first and
# the WiFi cut happens immediately after it, so staging mutations still race
# the cut while every assert stays recovery-side.
#
# WiFi restoration is guaranteed by an ON-DEVICE detached timer armed before
# any cut (`sleep N; svc wifi enable`): over ADB-over-WiFi-only setups the
# host-side `adbe wifi on` cannot reach the device once WiFi is down, so the
# timer is the authoritative cleanup and the host-side command is a fast-path
# bonus (USB setups). A failed run must never leave the device offline.
#
# Every maestro call passes --no-reinstall-driver: the driver apps are
# permanently installed on the device and HyperOS/MIUI auto-cancels
# new-package adb installs (INSTALL_FAILED_USER_RESTRICTED).
#
# Exit codes:
#   0    = stage and recovery both green
#   else = first failing stage's exit code, propagated unchanged
[CmdletBinding()]
param(
    [string]$StageFlow,

    [string]$RecoveryFlow,

    # Device serial for maestro/adbe. Defaults to the first `adb devices`
    # row in state "device" (exit 2 when none).
    [string]$Serial,

    [ValidateRange(0, 3600)]
    [int]$OfflineSeconds = 45,

    [ValidateRange(5, 3600)]
    [int]$ReconnectTimeoutSeconds = 180,

    # ip:port used to reconnect adb after the WiFi bounce. Defaults to
    # $env:E2E_ADB_TARGET; required when the serial is not an ip:port literal.
    [string]$AdbTarget = $env:E2E_ADB_TARGET,

    # Stage online and cut WiFi right after the last mutation (OQ2 fallback).
    [switch]$StageOnline
)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if (-not ($StageFlow -and $StageFlow.Trim())) {
    $StageFlow = Join-Path $repoRoot '.maestro/flows/chaos-stage.yaml'
}
if (-not ($RecoveryFlow -and $RecoveryFlow.Trim())) {
    $RecoveryFlow = Join-Path $repoRoot '.maestro/flows/chaos-recovery.yaml'
}

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

# Reconnect target: explicit -AdbTarget > $env:E2E_ADB_TARGET (bound at
# param default) > the serial itself when it is an ip:port literal.
# Anything else fails fast BEFORE any WiFi operation (design OQ3).
if (-not ($AdbTarget -and $AdbTarget.Trim())) {
    if ($Serial -match '^\d{1,3}(\.\d{1,3}){3}:\d+$') {
        $AdbTarget = $Serial
    }
    else {
        Write-Error 'No adb reconnect target available. Set E2E_ADB_TARGET or pass -AdbTarget ip:port.' -ErrorAction Continue
        exit 2
    }
}
Write-Host "Reconnect target: $AdbTarget"

# Schedule the WiFi re-enable ON THE DEVICE before cutting connectivity.
# Over an ADB-over-WiFi-only setup, once WiFi goes down the host loses every
# transport, so the host-side cleanup (`adbe wifi on`) can never reach the
# device. A detached on-device timer survives the transport loss and restores
# WiFi even when this script dies mid-run. Margin keeps it firing AFTER the
# scripted offline window; the host-side `adbe wifi on` remains the primary
# fast-path restore.
function Enable-DeviceWifiRestoreTimer {
    param([string]$DeviceSerial, [int]$DelaySeconds)
    # Detached subshell: returns immediately, keeps running on the device.
    adb -s $DeviceSerial shell "(sleep $DelaySeconds; svc wifi enable) >/dev/null 2>&1 &" *> $null
    Write-Host "On-device WiFi restore timer armed (+${DelaySeconds}s)."
}

# adbe targets the device through ANDROID_SERIAL; save/restore caller's env.
$priorAndroidSerial = $env:ANDROID_SERIAL
$env:ANDROID_SERIAL = $Serial

Write-Host "Chaos sequence on $Serial (stage=$(Split-Path -Leaf $StageFlow), offline=${OfflineSeconds}s)"

try {
    # Arm the on-device safety net BEFORE any connectivity cut. Over WiFi-only
    # ADB this timer is the AUTHORITATIVE restore path (the host cannot reach
    # the device once WiFi is down); over USB it is just a redundant net.
    Enable-DeviceWifiRestoreTimer -DeviceSerial $Serial -DelaySeconds ($OfflineSeconds + 10)

    if (-not $StageOnline) {
        adbe wifi off *> $null
        if ($LASTEXITCODE -ne 0) {
            Write-Error 'adbe wifi off failed before staging.' -ErrorAction Continue
            exit 1
        }
        Write-Host 'WiFi OFF - staging offline.'
    }

    & maestro test $StageFlow --no-reinstall-driver --device $Serial
    $stageExit = $LASTEXITCODE
    if ($stageExit -ne 0) {
        Write-Error "Stage flow failed with exit ${stageExit}." -ErrorAction Continue
        exit $stageExit
    }

    if ($StageOnline) {
        adbe wifi off *> $null
        if ($LASTEXITCODE -ne 0) {
            Write-Error 'adbe wifi off failed after staging.' -ErrorAction Continue
            exit 1
        }
        Write-Host 'WiFi OFF immediately after staging.'
    }

    Start-Sleep -Seconds $OfflineSeconds

    adbe wifi on *> $null
    Write-Host 'WiFi ON - waiting for adb transport.'

    # Subprocess isolation: Wait-AdbDevice.ps1 uses `exit` for its contract,
    # so run it detached to keep exit-code semantics unambiguous here.
    $waitScript = Join-Path $PSScriptRoot 'Wait-AdbDevice.ps1'
    & pwsh -NoProfile -File $waitScript `
        -Target $AdbTarget `
        -TimeoutSeconds $ReconnectTimeoutSeconds
    $waitExit = $LASTEXITCODE
    if ($waitExit -ne 0) {
        Write-Error "Device did not reconnect within ${ReconnectTimeoutSeconds}s - skipping recovery flow." -ErrorAction Continue
        exit $waitExit
    }

    & maestro test $RecoveryFlow --no-reinstall-driver --device $Serial
    exit $LASTEXITCODE
}
finally {
    # Fast-path restore: only lands when a transport is still alive (USB
    # setups). On WiFi-only ADB the on-device timer armed above performs the
    # actual restore during Wait-AdbDevice's poll window.
    adbe wifi on *> $null
    Write-Host 'Cleanup: adbe wifi on issued (host side); on-device timer remains as authority.'
    if ($null -eq $priorAndroidSerial) {
        Remove-Item Env:\ANDROID_SERIAL -ErrorAction SilentlyContinue
    }
    else {
        $env:ANDROID_SERIAL = $priorAndroidSerial
    }
}
