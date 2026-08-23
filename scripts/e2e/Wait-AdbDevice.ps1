# Polls `adb connect <target>` until the transport reports state "device".
# Used by Start-ChaosSequence.ps1 after the WiFi bounce to wait out the
# reconnect window.
#
# Exit codes:
#   0 = target reachable in state "device"
#   3 = timed out without reaching state "device"
[CmdletBinding()]
param(
    # adb endpoint to poll, e.g. 192.168.0.19:39161 or a full serial string.
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Target,

    [ValidateRange(1, 3600)]
    [int]$TimeoutSeconds = 120,

    [ValidateRange(1, 60)]
    [int]$PollSeconds = 5
)

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$escapedTarget = [regex]::Escape($Target)

while ($true) {
    adb connect $Target *> $null

    $stateLine = adb devices |
        Select-String -Pattern ("^$escapedTarget\s+device\s*$")
    if ($stateLine) {
        Write-Host "Device $Target is online."
        exit 0
    }

    if ((Get-Date) -ge $deadline) {
        Write-Error "Timed out after ${TimeoutSeconds}s waiting for adb device '$Target'." -ErrorAction Continue
        exit 3
    }

    Start-Sleep -Seconds $PollSeconds
}
