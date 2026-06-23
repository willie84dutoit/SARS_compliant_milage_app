<#
.SYNOPSIS
    Everyday "get back to where I left off" dev reload script for the Mileage Tracker app.

.DESCRIPTION
    Every run kills whatever dev infrastructure is currently up and starts it fresh, then always
    does a fresh app reload. This is a deliberate "kill and restart everything" design (explicitly
    requested) - it is NOT idempotent/skip-if-running for the backend or the emulator:

      1. Backend (.venv + Flask):
         - Finds ANY process currently listening on port 8080 via
           "Get-NetTCPConnection -LocalPort 8080 -State Listen" -> OwningProcess and stops it with
           Stop-Process, regardless of whether THIS script started it. This catches a Flask
           instance left over from an earlier run, or started manually outside this script -
           scripts/.dev-pids.json is only consulted as a secondary nice-to-have for -Stop, never
           as the sole source of truth for "is something already on this port."
         - Creates backend/.venv and installs backend/requirements.txt ONLY if .venv does not
           already exist (the dependency install is NOT torn down/reinstalled every run - "start
           fresh" means restart the running process, not rebuild the pip install).
         - ALWAYS starts a brand new Flask process via the venv's own python.exe directly (no
           shell "activation" needed/possible across process boundaries) - this code path always
           executes, every run, so the "starting backend" log lines always fire.
         - This is the documented bare ".venv + python app.py" workflow (see backend/README.md).
           The docker-compose path (backend + Firestore emulator) is a separate, valid local-dev
           alternative documented there, but is intentionally NOT what this script drives.

      2. Android emulator:
         - Fails loudly if ANDROID_HOME is not set or does not point to a real directory.
         - Resolves adb.exe / emulator.exe explicitly from $ANDROID_HOME - these tools are NOT
           assumed to be bare commands on PATH (verified on this machine: User-scope
           ANDROID_HOME is set correctly, but a fresh shell does not necessarily have
           platform-tools/emulator on PATH).
         - ALWAYS kills any currently-running emulator first: finds every "emulator-XXXX device"
           entry in "adb devices" and runs "adb -s emulator-XXXX emu kill" against each, then
           polls "adb devices" until that entry actually disappears (does not just fire the kill
           command and move on).
         - ALWAYS then boots the test_device AVD fresh in the background and polls
           sys.boot_completed up to a timeout - same as before, every run, no skip branch.

      3. App build + install + launch:
         - ALWAYS runs ./gradlew installDebug (this is the actual "reload" the user wants every
           time this script runs) and surfaces real Gradle failure output, never swallowed.
         - Launches com.mileagetracker.app/.MainActivity explicitly after a successful install.

.PARAMETER Stop
    Optional bonus switch. Stops the background Flask process and the emulator that THIS script
    started and tracked via scripts/.dev-pids.json. Does not attempt to discover or kill
    processes it did not start itself (keeps this simple, no PID-guessing across unrelated
    Python/emulator processes on the machine).

.NOTES
    Run from anywhere; the script resolves all paths relative to the repository root (the
    parent of this scripts/ directory), so "cd"-ing into scripts/ first is not required.
#>

[CmdletBinding()]
param(
    [switch]$Stop
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Path setup - resolve everything relative to the repository root.
# ---------------------------------------------------------------------------
$repositoryRootDirectory = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRootDirectory "backend"
$backendVirtualEnvironmentDirectory = Join-Path $backendDirectory ".venv"
$backendVirtualEnvironmentPythonExecutable = Join-Path $backendVirtualEnvironmentDirectory "Scripts\python.exe"
$backendRequirementsFile = Join-Path $backendDirectory "requirements.txt"
$gradlewBatchFile = Join-Path $repositoryRootDirectory "gradlew.bat"
$backgroundProcessTrackingFile = Join-Path $PSScriptRoot ".dev-pids.json"

$flaskHealthCheckUrl = "http://127.0.0.1:8080/health"
$flaskListenPort = 8080
$androidVirtualDeviceName = "test_device"
$androidApplicationId = "com.mileagetracker.app"
$androidLauncherActivity = "$androidApplicationId/.MainActivity"
$emulatorBootTimeoutSeconds = 90
$emulatorKillConfirmationTimeoutSeconds = 30

# Summary tracking - populated as the script progresses, printed at the end.
$summaryLines = New-Object System.Collections.Generic.List[string]
function Add-SummaryLine([string]$summaryMessage) {
    $summaryLines.Add($summaryMessage)
}

# ---------------------------------------------------------------------------
# Background-process bookkeeping (used by both the normal run and -Stop).
# ---------------------------------------------------------------------------
function Read-TrackedProcessIds {
    if (Test-Path $backgroundProcessTrackingFile) {
        return Get-Content $backgroundProcessTrackingFile -Raw | ConvertFrom-Json
    }
    return [PSCustomObject]@{ flaskProcessId = $null; emulatorProcessId = $null }
}

function Write-TrackedProcessIds([Nullable[int]]$flaskProcessId, [Nullable[int]]$emulatorProcessId) {
    $existingTrackedProcessIds = Read-TrackedProcessIds
    $updatedFlaskProcessId = if ($null -ne $flaskProcessId) { $flaskProcessId } else { $existingTrackedProcessIds.flaskProcessId }
    $updatedEmulatorProcessId = if ($null -ne $emulatorProcessId) { $emulatorProcessId } else { $existingTrackedProcessIds.emulatorProcessId }
    $trackedProcessIdsObject = [PSCustomObject]@{
        flaskProcessId    = $updatedFlaskProcessId
        emulatorProcessId = $updatedEmulatorProcessId
    }
    $trackedProcessIdsObject | ConvertTo-Json | Set-Content -Path $backgroundProcessTrackingFile
}

# ---------------------------------------------------------------------------
# Android SDK location resolution - checks process scope first (the normal
# case once a shell has been opened after the variable was set), then falls
# back to the persisted User-scope and Machine-scope Windows environment
# variables. A brand-new PowerShell/Bash process does not automatically
# inherit User/Machine env vars that were set by a *previous* process in the
# same login session via setx/System Properties unless it was started after
# that write propagated - this resolves that gap instead of failing loudly
# on a false negative. Only returns $null when truly unset at all three
# scopes, which is the genuine "not configured" case worth failing loudly on.
# ---------------------------------------------------------------------------
function Resolve-AndroidSdkEnvironmentVariable([string]$environmentVariableName) {
    $processScopeValue = [Environment]::GetEnvironmentVariable($environmentVariableName, "Process")
    if (-not [string]::IsNullOrWhiteSpace($processScopeValue)) {
        return [PSCustomObject]@{ Value = $processScopeValue; FoundAtScope = "Process" }
    }

    $userScopeValue = [Environment]::GetEnvironmentVariable($environmentVariableName, "User")
    if (-not [string]::IsNullOrWhiteSpace($userScopeValue)) {
        return [PSCustomObject]@{ Value = $userScopeValue; FoundAtScope = "User" }
    }

    $machineScopeValue = [Environment]::GetEnvironmentVariable($environmentVariableName, "Machine")
    if (-not [string]::IsNullOrWhiteSpace($machineScopeValue)) {
        return [PSCustomObject]@{ Value = $machineScopeValue; FoundAtScope = "Machine" }
    }

    return $null
}

# ---------------------------------------------------------------------------
# Stop-ProcessListeningOnPort: the source of truth for "is Flask already
# running" is the OS socket table, not scripts/.dev-pids.json. This finds
# every process currently LISTENing on the given TCP port - regardless of
# whether this script started it (could be left over from an earlier run, or
# started manually) - and force-stops each one. Safe to call when nothing is
# listening (returns with no action).
# ---------------------------------------------------------------------------
function Stop-ProcessListeningOnPort([int]$tcpPort) {
    $listeningConnections = Get-NetTCPConnection -LocalPort $tcpPort -State Listen -ErrorAction SilentlyContinue
    if ($null -eq $listeningConnections) {
        return
    }

    $owningProcessIds = $listeningConnections | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($owningProcessId in $owningProcessIds) {
        $owningProcess = Get-Process -Id $owningProcessId -ErrorAction SilentlyContinue
        if ($null -ne $owningProcess) {
            Write-Host "Killing existing process on port $tcpPort - $($owningProcess.ProcessName) (PID $owningProcessId)." -ForegroundColor Yellow
            Stop-Process -Id $owningProcessId -Force
        }
    }
}

# ---------------------------------------------------------------------------
# -Stop mode: tear down only what this script itself started and tracked.
# ---------------------------------------------------------------------------
if ($Stop) {
    Write-Host "Stopping dev processes started by this script..." -ForegroundColor Cyan

    if (-not (Test-Path $backgroundProcessTrackingFile)) {
        Write-Host "No tracked process-id file found at $backgroundProcessTrackingFile - nothing to stop." -ForegroundColor Yellow
        exit 0
    }

    $trackedProcessIds = Read-TrackedProcessIds

    if ($null -ne $trackedProcessIds.flaskProcessId) {
        $existingFlaskProcess = Get-Process -Id $trackedProcessIds.flaskProcessId -ErrorAction SilentlyContinue
        if ($null -ne $existingFlaskProcess) {
            Stop-Process -Id $trackedProcessIds.flaskProcessId -Force
            Write-Host "Stopped tracked Flask process (PID $($trackedProcessIds.flaskProcessId))." -ForegroundColor Green
        } else {
            Write-Host "Tracked Flask process (PID $($trackedProcessIds.flaskProcessId)) was not running." -ForegroundColor Yellow
        }
    }

    if ($null -ne $trackedProcessIds.emulatorProcessId) {
        $existingEmulatorProcess = Get-Process -Id $trackedProcessIds.emulatorProcessId -ErrorAction SilentlyContinue
        if ($null -ne $existingEmulatorProcess) {
            Stop-Process -Id $trackedProcessIds.emulatorProcessId -Force
            Write-Host "Stopped tracked emulator process (PID $($trackedProcessIds.emulatorProcessId))." -ForegroundColor Green
        } else {
            Write-Host "Tracked emulator process (PID $($trackedProcessIds.emulatorProcessId)) was not running." -ForegroundColor Yellow
        }
    }

    Remove-Item -Path $backgroundProcessTrackingFile
    exit 0
}

Write-Host "==> Mileage Tracker dev reload starting..." -ForegroundColor Cyan

# ===========================================================================
# 1. BACKEND - project-local .venv + Flask app
# ===========================================================================
Write-Host "`n[1/3] Backend (.venv + Flask)" -ForegroundColor Cyan

$backendStatus = "unknown"

if (-not (Test-Path $backendVirtualEnvironmentDirectory)) {
    Write-Host "backend/.venv not found - creating it now." -ForegroundColor Yellow
    python -m venv $backendVirtualEnvironmentDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create backend/.venv (python -m venv exited with code $LASTEXITCODE)."
    }

    Write-Host "Installing pinned dependencies from backend/requirements.txt..." -ForegroundColor Yellow
    & $backendVirtualEnvironmentPythonExecutable -m pip install -r $backendRequirementsFile
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install backend dependencies (pip install exited with code $LASTEXITCODE)."
    }
    Add-SummaryLine "Backend: .venv was missing -> created fresh and installed requirements.txt."
} else {
    Add-SummaryLine "Backend: .venv already existed -> left untouched (no reinstall)."
}

Write-Host "Stopping any existing process on port $flaskListenPort (kill-and-restart-every-run policy)..." -ForegroundColor Yellow
Stop-ProcessListeningOnPort -tcpPort $flaskListenPort

Write-Host "Starting Flask fresh via the venv python.exe." -ForegroundColor Yellow
$flaskProcess = Start-Process -FilePath $backendVirtualEnvironmentPythonExecutable `
    -ArgumentList "app.py" `
    -WorkingDirectory $backendDirectory `
    -WindowStyle Hidden `
    -PassThru
Write-TrackedProcessIds -flaskProcessId $flaskProcess.Id -emulatorProcessId $null

$flaskStartupDeadline = (Get-Date).AddSeconds(15)
$flaskNowRunning = $false
while ((Get-Date) -lt $flaskStartupDeadline) {
    Start-Sleep -Milliseconds 500
    try {
        $healthResponseAfterStart = Invoke-WebRequest -Uri $flaskHealthCheckUrl -UseBasicParsing -TimeoutSec 2
        if ($healthResponseAfterStart.StatusCode -eq 200) {
            $flaskNowRunning = $true
            break
        }
    } catch {
        # Flask has not finished starting yet -- keep polling until the startup deadline.
        continue
    }
}

if ($flaskNowRunning) {
    Write-Host "Flask started successfully (PID $($flaskProcess.Id)) and is healthy." -ForegroundColor Green
    Add-SummaryLine "Backend: killed any existing process on port $flaskListenPort -> started fresh (PID $($flaskProcess.Id)), health check passed."
    $backendStatus = "pass (freshly restarted)"
} else {
    throw "Flask process was started (PID $($flaskProcess.Id)) but $flaskHealthCheckUrl never returned 200 within 15s. Check backend/app.py output."
}

# ===========================================================================
# 2. ANDROID EMULATOR
# ===========================================================================
Write-Host "`n[2/3] Android emulator" -ForegroundColor Cyan

$androidHomeResolution = Resolve-AndroidSdkEnvironmentVariable -environmentVariableName "ANDROID_HOME"
$androidSdkRootResolution = if ($null -eq $androidHomeResolution) { Resolve-AndroidSdkEnvironmentVariable -environmentVariableName "ANDROID_SDK_ROOT" } else { $null }
$resolvedAndroidSdkVariable = if ($null -ne $androidHomeResolution) { $androidHomeResolution } else { $androidSdkRootResolution }

if ($null -eq $resolvedAndroidSdkVariable) {
    throw "ANDROID_HOME (and ANDROID_SDK_ROOT) are not set in this environment at the Process, User, or Machine scope. Set ANDROID_HOME to your Android SDK location (expected: C:\Android\Sdk) before running this script."
}

$androidSdkRootDirectory = $resolvedAndroidSdkVariable.Value

if ($resolvedAndroidSdkVariable.FoundAtScope -ne "Process") {
    Write-Host "ANDROID_HOME/ANDROID_SDK_ROOT not set in this process, but found at $($resolvedAndroidSdkVariable.FoundAtScope) scope ($androidSdkRootDirectory) - using it for this run." -ForegroundColor Yellow
    $env:ANDROID_HOME = $androidSdkRootDirectory
    $env:ANDROID_SDK_ROOT = $androidSdkRootDirectory
}

if (-not (Test-Path $androidSdkRootDirectory)) {
    throw "ANDROID_HOME is set to $androidSdkRootDirectory but that directory does not exist. Fix the ANDROID_HOME environment variable before running this script."
}

$adbExecutablePath = Join-Path $androidSdkRootDirectory "platform-tools\adb.exe"
$emulatorExecutablePath = Join-Path $androidSdkRootDirectory "emulator\emulator.exe"

if (-not (Test-Path $adbExecutablePath)) {
    throw "adb.exe not found at expected path $adbExecutablePath. Verify the Android SDK platform-tools component is installed."
}
if (-not (Test-Path $emulatorExecutablePath)) {
    throw "emulator.exe not found at expected path $emulatorExecutablePath. Verify the Android SDK emulator component is installed."
}

$emulatorKillAndRebootStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

# ---------------------------------------------------------------------------
# Always kill any currently-running emulator(s) first - kill-and-restart
# policy, no "already running -> skip" branch. Each "emulator-XXXX device"
# entry gets an explicit "emu kill", and we then poll "adb devices" until
# that entry actually disappears rather than firing the kill and moving on.
# ---------------------------------------------------------------------------
$adbDevicesOutputBeforeKill = & $adbExecutablePath devices
$runningEmulatorLinesBeforeKill = $adbDevicesOutputBeforeKill | Where-Object { $_ -match "^emulator-\d+\s+device$" }

if ($runningEmulatorLinesBeforeKill) {
    foreach ($runningEmulatorLine in $runningEmulatorLinesBeforeKill) {
        $emulatorSerial = ($runningEmulatorLine -split "\s+")[0]
        Write-Host "Killing running emulator $emulatorSerial (kill-and-restart-every-run policy)..." -ForegroundColor Yellow
        & $adbExecutablePath -s $emulatorSerial emu kill

        $killConfirmationDeadline = (Get-Date).AddSeconds($emulatorKillConfirmationTimeoutSeconds)
        $emulatorActuallyGone = $false
        while ((Get-Date) -lt $killConfirmationDeadline) {
            Start-Sleep -Seconds 1
            $adbDevicesOutputDuringKill = & $adbExecutablePath devices
            $stillPresent = $adbDevicesOutputDuringKill | Where-Object { $_ -match "^$([regex]::Escape($emulatorSerial))\s+device$" }
            if (-not $stillPresent) {
                $emulatorActuallyGone = $true
                break
            }
        }

        if ($emulatorActuallyGone) {
            Write-Host "Confirmed $emulatorSerial is gone from 'adb devices'." -ForegroundColor Green
        } else {
            throw "Emulator $emulatorSerial did not disappear from 'adb devices' within ${emulatorKillConfirmationTimeoutSeconds}s after 'emu kill'. Check manually with: $adbExecutablePath devices"
        }
    }
} else {
    Write-Host "No running emulator found - nothing to kill." -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# Always boot test_device fresh - no "already running -> skip" branch.
# ---------------------------------------------------------------------------
Write-Host "Booting $androidVirtualDeviceName fresh in the background." -ForegroundColor Yellow
$emulatorProcess = Start-Process -FilePath $emulatorExecutablePath `
    -ArgumentList @("-avd", $androidVirtualDeviceName) `
    -WindowStyle Hidden `
    -PassThru
Write-TrackedProcessIds -flaskProcessId $null -emulatorProcessId $emulatorProcess.Id

Write-Host "Waiting for the emulator to be discoverable by adb..." -ForegroundColor Yellow
& $adbExecutablePath wait-for-device

Write-Host "Polling sys.boot_completed (timeout ${emulatorBootTimeoutSeconds}s)..." -ForegroundColor Yellow
$bootDeadline = (Get-Date).AddSeconds($emulatorBootTimeoutSeconds)
$emulatorFullyBooted = $false
while ((Get-Date) -lt $bootDeadline) {
    $bootCompletedValue = (& $adbExecutablePath shell getprop sys.boot_completed 2>$null).Trim()
    if ($bootCompletedValue -eq "1") {
        $emulatorFullyBooted = $true
        break
    }
    Start-Sleep -Seconds 2
}

$emulatorKillAndRebootStopwatch.Stop()
$emulatorKillAndRebootElapsedSeconds = [math]::Round($emulatorKillAndRebootStopwatch.Elapsed.TotalSeconds, 1)

if ($emulatorFullyBooted) {
    Write-Host "Emulator $androidVirtualDeviceName fully booted (kill+reboot took ${emulatorKillAndRebootElapsedSeconds}s)." -ForegroundColor Green
    Add-SummaryLine "Emulator: killed any running instance -> booted $androidVirtualDeviceName fresh (PID $($emulatorProcess.Id)) in ${emulatorKillAndRebootElapsedSeconds}s."
    $emulatorStatus = "pass (killed + freshly rebooted, ${emulatorKillAndRebootElapsedSeconds}s)"
} else {
    throw "Emulator $androidVirtualDeviceName did not report sys.boot_completed=1 within ${emulatorBootTimeoutSeconds}s. It may still be starting - check manually with: $adbExecutablePath devices"
}

# ===========================================================================
# 3. BUILD + INSTALL + LAUNCH - always runs, this is the actual "reload"
# ===========================================================================
Write-Host "`n[3/3] Build, install, and launch the app" -ForegroundColor Cyan

if (-not (Test-Path $gradlewBatchFile)) {
    throw "gradlew.bat not found at expected path $gradlewBatchFile."
}

Write-Host "Running ./gradlew installDebug (this always runs - it is the reload step)..." -ForegroundColor Yellow
Push-Location $repositoryRootDirectory
try {
    & $gradlewBatchFile installDebug
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($gradleExitCode -ne 0) {
    throw "gradlew installDebug failed with exit code $gradleExitCode. See the Gradle output above for the real error - it is not suppressed."
}

Write-Host "Install succeeded. Launching $androidLauncherActivity..." -ForegroundColor Yellow
& $adbExecutablePath shell am start -n $androidLauncherActivity
if ($LASTEXITCODE -ne 0) {
    throw "adb shell am start exited with code $LASTEXITCODE while launching $androidLauncherActivity."
}

Add-SummaryLine "App: gradlew installDebug succeeded and $androidLauncherActivity was launched."
$appStatus = "pass"

# ===========================================================================
# SUMMARY
# ===========================================================================
Write-Host "`n==================== SUMMARY ====================" -ForegroundColor Cyan
foreach ($summaryLine in $summaryLines) {
    Write-Host "  - $summaryLine"
}
Write-Host "`nBackend:  $backendStatus" -ForegroundColor Green
Write-Host "Emulator: $emulatorStatus" -ForegroundColor Green
Write-Host "App:      $appStatus" -ForegroundColor Green
Write-Host "==================================================`n" -ForegroundColor Cyan
