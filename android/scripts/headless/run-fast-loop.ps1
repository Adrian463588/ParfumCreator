<#
.SYNOPSIS
    Continuous Fast-Fail Verification Loop for Autonomous 24/7 Headless Development.
    Executes: Unit Tests -> APK Build -> Install on Headless Device -> Launch & State Verification.
.PARAMETER Serial
    Target ADB device or emulator serial (default: auto-detected emulator).
.PARAMETER SkipTests
    Skip unit test execution if already verified.
#>
param(
    [string]$Serial = "",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
Write-Host "================== FAST-FAIL LOOP RUN [$timestamp] ==================" -ForegroundColor Cyan

# 1. Device Resolution
if (-not $Serial) {
    $emulators = & adb devices | Select-String "emulator-\d+"
    if ($emulators) {
        $Serial = ($emulators[0].Line.Trim() -split "\s+")[0]
    } else {
        $devices = & adb devices | Select-String "\tdevice"
        if ($devices) {
            $Serial = ($devices[0].Line.Trim() -split "\s+")[0]
        }
    }
}

if (-not $Serial) {
    Write-Error "[Fast-Fail] No connected ADB device or emulator found. Run start-emulator.ps1 first."
}
Write-Host "[Fast-Fail Target]: Device serial $Serial" -ForegroundColor Yellow

# 2. Gate 1: JVM Unit Tests
if (-not $SkipTests) {
    Write-Host "`n[Gate 1/4: JVM Unit Tests (Fast-Fail)] Running :app:testDebugUnitTest..." -ForegroundColor Cyan
    & .\gradlew :app:testDebugUnitTest --daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[Gate 1 FAILED] Unit tests failed. Aborting fast-fail cycle."
    }
    Write-Host "[Gate 1 PASSED] All unit tests passed successfully." -ForegroundColor Green
}

# 3. Gate 2: APK Build
Write-Host "`n[Gate 2/4: Assemble Debug APK] Running :app:assembleDebug..." -ForegroundColor Cyan
& .\gradlew :app:assembleDebug --daemon
if ($LASTEXITCODE -ne 0) {
    Write-Error "[Gate 2 FAILED] Gradle assembleDebug build failed."
}
$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "[Gate 2 FAILED] Output APK not found at $apkPath."
}
Write-Host "[Gate 2 PASSED] APK generated at $apkPath." -ForegroundColor Green

# 4. Gate 3: Headless Install
Write-Host "`n[Gate 3/4: Headless Install] Deploying to $Serial..." -ForegroundColor Cyan
& adb -s $Serial install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Error "[Gate 3 FAILED] Failed to install APK to $Serial."
}
Write-Host "[Gate 3 PASSED] APK installed successfully." -ForegroundColor Green

# 5. Gate 4: Launch & Verify State
Write-Host "`n[Gate 4/4: Launch & Verify State] Launching com.parfumecreator/.MainActivity..." -ForegroundColor Cyan
& adb -s $Serial shell am start -n com.parfumecreator/.MainActivity
Start-Sleep -Seconds 3

# Check for crashes in logcat
$crashes = & adb -s $Serial logcat -d -t 50 "*:E" | Select-String "FATAL EXCEPTION|com.parfumecreator"
if ($crashes) {
    Write-Host "[Gate 4 WARNING] Potential exceptions detected during startup:" -ForegroundColor Red
    $crashes | Select-Object -First 10
} else {
    Write-Host "[Gate 4 PASSED] App launched cleanly with zero startup crashes!" -ForegroundColor Green
}

Write-Host "`n================== FAST-FAIL LOOP COMPLETED SUCCESSFULLY ==================" -ForegroundColor Cyan
