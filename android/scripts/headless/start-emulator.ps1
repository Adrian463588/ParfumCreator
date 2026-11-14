<#
.SYNOPSIS
    Starts an Android Virtual Device in ultra-lightweight headless CLI mode (~1.5GB RAM).
.PARAMETER AvdName
    Name of the AVD (default: "codex_psikotes_api33").
.PARAMETER MemoryMb
    RAM limit for the VM in MB (default: 1536).
#>
param(
    [string]$AvdName = "codex_psikotes_api33",
    [int]$MemoryMb = 1536,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

Write-Host "[Headless] Checking if emulator is already running..." -ForegroundColor Cyan
$existing = & adb devices | Select-String "emulator-"
if ($existing) {
    Write-Host "[Headless] Emulator already attached: $($existing.Line.Trim())" -ForegroundColor Green
    exit 0
}

Write-Host "[Headless] Launching AVD '$AvdName' with -memory $MemoryMb -no-window -no-audio -no-boot-anim..." -ForegroundColor Cyan

$flags = @(
    "-avd", $AvdName,
    "-no-window",
    "-no-audio",
    "-no-boot-anim",
    "-gpu", "swiftshader_indirect",
    "-memory", $MemoryMb.ToString(),
    "-timezone", "Asia/Jakarta"
)

$emulatorProc = Start-Process -FilePath "emulator" -ArgumentList $flags -PassThru -NoNewWindow
Write-Host "[Headless] Emulator process started with PID: $($emulatorProc.Id)" -ForegroundColor Yellow

Write-Host "[Headless] Waiting for adb device and boot completion (max $TimeoutSeconds s)..." -ForegroundColor Cyan
$startTime = Get-Date

# Wait for device to appear in adb
& adb wait-for-device

$bootCompleted = $false
while ((Get-Date) -lt $startTime.AddSeconds($TimeoutSeconds)) {
    $prop = & adb shell getprop sys.boot_completed 2>$null
    if ($prop -match "1") {
        $bootCompleted = $true
        break
    }
    Start-Sleep -Seconds 2
}

if ($bootCompleted) {
    Write-Host "[Headless] AVD '$AvdName' is fully booted and ready!" -ForegroundColor Green
    & adb devices
} else {
    Write-Warning "[Headless] Timeout waiting for sys.boot_completed. Emulator may still be initializing."
}
