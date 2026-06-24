<#
.SYNOPSIS
    Replays a GPS route fixture (scripts/gps-routes/*.json) against a running Android emulator
    via "adb emu geo fix", to field-verify T-004's trip distance/stop logic without driving.

.DESCRIPTION
    Reads a fixture JSON (see scripts/gps-routes/normal-trip.json, stop-start-traffic.json,
    park-and-stop.json for the documented scenarios and timing rationale) and, for each waypoint
    in order, sleeps "delaySecondsAfterPrevious" seconds and then injects that coordinate via
    "adb emu geo fix <longitude> <latitude>" - note the argument order is longitude first, then
    latitude; this is the opposite of this project's own JSON field order (latitude, longitude)
    and is a common source of mistakes.

    Fails loudly (per this project's "never suppress command errors" rule) if adb cannot be
    resolved from $env:ANDROID_HOME / $env:ANDROID_SDK_ROOT, or if no emulator is currently
    listed as "device" in "adb devices" - it does not silently no-op.

    While this script runs, watch the app's own logs in a second terminal to correlate behaviour:
        adb logcat -s MT-Location:* MT-Trip:* MT-Service:* MT-ActivityRecognition:*

.PARAMETER FixturePath
    Path to a route fixture JSON file, e.g. scripts/gps-routes/stop-start-traffic.json

.EXAMPLE
    ./scripts/replay-gps-route.ps1 -FixturePath ./scripts/gps-routes/stop-start-traffic.json
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$FixturePath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $FixturePath)) {
    throw "Fixture file not found: $FixturePath"
}

$androidSdkRoot = $env:ANDROID_HOME
if (-not $androidSdkRoot) {
    $androidSdkRoot = $env:ANDROID_SDK_ROOT
}
if (-not $androidSdkRoot -or -not (Test-Path $androidSdkRoot)) {
    throw "ANDROID_HOME / ANDROID_SDK_ROOT is not set or does not point to a real directory. " +
        "This script will not assume adb is bare on PATH - set the SDK path explicitly first."
}

$adbExecutablePath = Join-Path $androidSdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $adbExecutablePath)) {
    throw "adb.exe not found at expected path: $adbExecutablePath"
}

$connectedDevicesOutput = & $adbExecutablePath devices
$runningEmulatorLine = $connectedDevicesOutput | Where-Object { $_ -match "^(emulator-\d+)\s+device\s*$" }
if (-not $runningEmulatorLine) {
    throw "No running emulator found in 'adb devices' output. Boot the test_device AVD first " +
        "(e.g. via scripts/start-dev.ps1) before replaying a route fixture.`nadb devices output:`n$connectedDevicesOutput"
}
$emulatorSerial = ($runningEmulatorLine -replace "\s+device\s*$", "").Trim()
Write-Host "Replaying '$FixturePath' against emulator '$emulatorSerial'"

$fixtureContent = Get-Content -Raw -Path $FixturePath | ConvertFrom-Json
$waypoints = $fixtureContent.waypoints
if (-not $waypoints -or $waypoints.Count -eq 0) {
    throw "Fixture '$FixturePath' has no waypoints."
}

Write-Host "Scenario: $($fixtureContent.scenario)  -  $($waypoints.Count) waypoints"

$replayStartTime = Get-Date
$elapsedSecondsSinceStart = 0

foreach ($waypoint in $waypoints) {
    $delaySeconds = [int]$waypoint.delaySecondsAfterPrevious
    if ($delaySeconds -gt 0) {
        Start-Sleep -Seconds $delaySeconds
    }
    $elapsedSecondsSinceStart = [int]((Get-Date) - $replayStartTime).TotalSeconds

    $latitude = $waypoint.latitude
    $longitude = $waypoint.longitude

    # adb emu geo fix takes <longitude> <latitude>, in that order.
    & $adbExecutablePath -s $emulatorSerial emu geo fix $longitude $latitude

    Write-Host ("[+{0,4}s] {1,-22} lat={2} lng={3}" -f $elapsedSecondsSinceStart, $waypoint.label, $latitude, $longitude)
}

Write-Host "Replay of '$FixturePath' complete. Total elapsed: ${elapsedSecondsSinceStart}s."
Write-Host "Check 'adb logcat -s MT-Location:* MT-Trip:* MT-Service:*' for the app's actual response."
