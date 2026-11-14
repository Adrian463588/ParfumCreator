<#
.SYNOPSIS
    Reports status, resources, and boot state of attached Android devices and emulators.
#>
param()

Write-Host "=================== ANDROID RUNTIME STATUS ===================" -ForegroundColor Cyan
Write-Host "Attached ADB Devices:" -ForegroundColor Yellow
& adb devices -l

$emulatorProcs = Get-Process -Name "emulator*", "qemu-system*" -ErrorAction SilentlyContinue
if ($emulatorProcs) {
    Write-Host "`nActive Emulator Processes:" -ForegroundColor Yellow
    foreach ($p in $emulatorProcs) {
        $ramMb = [math]::Round($p.WorkingSet64 / 1MB, 1)
        Write-Host " - PID: $($p.Id) | Process: $($p.ProcessName) | RAM: $ramMb MB" -ForegroundColor Green
    }
} else {
    Write-Host "`nNo host emulator processes running." -ForegroundColor Gray
}

$emulators = & adb devices | Select-String "emulator-\d+"
foreach ($line in $emulators) {
    $serial = ($line.Line.Trim() -split "\s+")[0]
    $boot = (& adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
    $model = (& adb -s $serial shell getprop ro.product.model 2>$null).Trim()
    $api = (& adb -s $serial shell getprop ro.build.version.sdk 2>$null).Trim()
    Write-Host "`n[Emulator Details: $serial]" -ForegroundColor Cyan
    Write-Host " - Model: $model | Android API: $api | Boot Completed: $boot" -ForegroundColor Green
}
Write-Host "==============================================================" -ForegroundColor Cyan
