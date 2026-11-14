<#
.SYNOPSIS
    Dumps UI hierarchy (accessibility tree) from device or emulator without opening GUI window.
.PARAMETER Serial
    ADB serial ID (optional).
.PARAMETER OutputFile
    Local path to save the XML dump (default: "android/logs/ui_dump.xml").
#>
param(
    [string]$Serial = "",
    [string]$OutputFile = "android/logs/ui_dump.xml"
)

$target = if ($Serial) { "-s $Serial" } else { "" }

$outputDir = Split-Path -Path $OutputFile -Parent
if ($outputDir -and !(Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

Write-Host "[Headless] Dumping UI hierarchy via uiautomator..." -ForegroundColor Cyan
& adb $target.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries) shell uiautomator dump /sdcard/window_dump.xml
& adb $target.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries) pull /sdcard/window_dump.xml $OutputFile

if (Test-Path $OutputFile) {
    Write-Host "[Headless] UI hierarchy dumped to: $OutputFile" -ForegroundColor Green
    $xml = [xml](Get-Content $OutputFile -Raw)
    $nodes = $xml.SelectNodes("//node[@text!='' or @content-desc!='']")
    Write-Host "`n[Interactive UI Elements Preview]:" -ForegroundColor Yellow
    foreach ($node in $nodes | Select-Object -First 15) {
        $desc = if ($node.GetAttribute("text")) { $node.GetAttribute("text") } else { $node.GetAttribute("content-desc") }
        $cls = $node.GetAttribute("class").Split(".")[-1]
        $res = $node.GetAttribute("resource-id").Split("/")[-1]
        Write-Host " - [$cls] $desc ($res)" -ForegroundColor Gray
    }
} else {
    Write-Error "[Headless] Failed to pull window_dump.xml from device."
}
