<#
.SYNOPSIS
    Dumps recent Android error logs, crashes, and exceptions for ParfumCreator.
.PARAMETER Serial
    ADB serial ID (optional).
.PARAMETER Lines
    Number of logcat lines to retrieve (default: 200).
#>
param(
    [string]$Serial = "",
    [int]$Lines = 200
)

$target = if ($Serial) { "-s $Serial" } else { "" }

Write-Host "[Headless] Fetching recent system error logs..." -ForegroundColor Cyan
$cmdArgs = @()
if ($Serial) { $cmdArgs += @("-s", $Serial) }
$cmdArgs += @("logcat", "-d", "-t", $Lines.ToString(), "*:E")

$errors = & adb @cmdArgs
if ($errors) {
    Write-Host "[Recent Errors / Exceptions]:" -ForegroundColor Red
    $errors | Select-String "com.parfumecreator|AndroidRuntime|FATAL|Exception|Error" | Select-Object -Last 30
} else {
    Write-Host "[Headless] No critical errors or crashes logged in the last $Lines lines." -ForegroundColor Green
}
