<#
.SYNOPSIS
    Gracefully stops the running headless Android emulator.
.PARAMETER Serial
    ADB serial ID of the emulator (default: auto-detected or emulator-5562).
#>
param(
    [string]$Serial = ""
)

Write-Host "[Headless] Stopping Android Emulator..." -ForegroundColor Cyan

if (-not $Serial) {
    $devices = & adb devices | Select-String "emulator-\d+"
    if ($devices) {
        $Serial = ($devices[0].Line.Trim() -split "\s+")[0]
    }
}

if ($Serial) {
    Write-Host "[Headless] Sending 'emu kill' to $Serial..." -ForegroundColor Yellow
    & adb -s $Serial emu kill 2>$null
    Start-Sleep -Seconds 2
    Write-Host "[Headless] Emulator $Serial stopped successfully." -ForegroundColor Green
} else {
    Write-Host "[Headless] No running emulator instance detected in adb devices." -ForegroundColor Gray
}
