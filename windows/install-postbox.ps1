#Requires -Version 5.1
<#
.SYNOPSIS
    Postbox Offline Mail - Windows Local Build & Android/WSA Installer (No Server / No Hosting)
.DESCRIPTION
    Builds the offline Postbox Android APK or Google Play App Bundle (.aab) locally on Windows,
    or sideloads the compiled APK to an Android device / Windows Subsystem for Android (WSA) via ADB.
    Runs strictly locally on your machine and never launches any server or hosted service.
.EXAMPLE
    .\windows\install-postbox.ps1 -Action Info
    .\windows\install-postbox.ps1 -Action BuildApk
    .\windows\install-postbox.ps1 -Action BuildBundle
    .\windows\install-postbox.ps1 -Action InstallAdb
    .\windows\install-postbox.ps1 -Action InstallWsa
#>
[CmdletBinding()]
param(
    [ValidateSet("Info", "BuildApk", "BuildReleaseApk", "BuildBundle", "InstallAdb", "InstallWsa", "Test")]
    [string]$Action = "Info",

    [string]$ApkPath = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AppId = "com.aistudio.postbox.v7k9qm"

function Find-OfflineApk {
    $candidates = @(
        (Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"),
        (Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"),
        (Join-Path $RepoRoot ".build-outputs\apk\debug\app-debug.apk"),
        (Join-Path $RepoRoot "dist\app-debug.apk")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    return $null
}

function Invoke-GradleTask {
    param([string]$Task)
    $gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
    if (-not $gradleCmd) {
        throw "Gradle CLI ('gradle') was not found on PATH. Install Gradle 8+ and JDK 17+, or export the APK via Docker."
    }
    Push-Location $RepoRoot
    try {
        Write-Host "Running gradle $Task ..." -ForegroundColor Cyan
        & gradle $Task
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle task '$Task' failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Show-Info {
    Write-Host "Postbox Offline Mail (Windows Packaging Helper)" -ForegroundColor Green
    Write-Host "  Application ID : $AppId"
    Write-Host "  Runtime Mode   : 100% offline Android APK (Room SQLite, zero network/server)"
    Write-Host "  Workspace      : $RepoRoot"

    $apk = Find-OfflineApk
    if ($apk) {
        $sizeKb = [Math]::Round((Get-Item $apk).Length / 1KB)
        Write-Host "  Found APK      : $apk ($sizeKb KB)"
    } else {
        Write-Host "  Found APK      : None yet. Run with -Action BuildApk"
    }

    $aab = Join-Path $RepoRoot "app\build\outputs\bundle\release\app-release.aab"
    if (Test-Path $aab) {
        $aabKb = [Math]::Round((Get-Item $aab).Length / 1KB)
        Write-Host "  Found Play AAB : $aab ($aabKb KB)"
    }
}

switch ($Action) {
    "Info" {
        Show-Info
    }
    "BuildApk" {
        Invoke-GradleTask ":app:assembleDebug"
        Show-Info
    }
    "BuildReleaseApk" {
        if (-not $env:STORE_PASSWORD -or -not $env:KEY_PASSWORD) {
            Write-Warning "Set KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD environment variables for release signing."
        }
        Invoke-GradleTask ":app:assembleRelease"
        Show-Info
    }
    "BuildBundle" {
        if (-not $env:STORE_PASSWORD -or -not $env:KEY_PASSWORD) {
            Write-Warning "Set KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD environment variables to sign app-release.aab for Google Play."
        }
        Invoke-GradleTask ":app:bundleRelease"
        Show-Info
    }
    "Test" {
        Invoke-GradleTask ":app:testDebugUnitTest"
    }
    "InstallAdb" {
        $adb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $adb) {
            throw "'adb' was not found on PATH. Install Android SDK Platform-Tools for Windows."
        }
        $targetApk = if ($ApkPath) { $ApkPath } else { Find-OfflineApk }
        if (-not $targetApk) {
            Invoke-GradleTask ":app:assembleDebug"
            $targetApk = Find-OfflineApk
        }
        if (-not $targetApk -or -not (Test-Path $targetApk)) {
            throw "Could not locate an APK file to install."
        }
        Write-Host "Installing $targetApk via ADB..." -ForegroundColor Cyan
        & adb install -r $targetApk
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    "InstallWsa" {
        $adb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $adb) {
            throw "'adb' was not found on PATH. Install Android SDK Platform-Tools for Windows."
        }
        $targetApk = if ($ApkPath) { $ApkPath } else { Find-OfflineApk }
        if (-not $targetApk) {
            Invoke-GradleTask ":app:assembleDebug"
            $targetApk = Find-OfflineApk
        }
        Write-Host "Connecting to Windows Subsystem for Android (127.0.0.1:58526)..." -ForegroundColor Cyan
        & adb connect 127.0.0.1:58526
        & adb -s 127.0.0.1:58526 install -r $targetApk
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
