<#
.SYNOPSIS
    Builds the RedicalClinic Windows Installer packages (.msi and .exe).

.PARAMETER Version
    The application version to package (defaults to "1.6").

.PARAMETER OutputDir
    Directory to place the final installers.
#>
param(
    [string]$Version = "1.6",
    [string]$OutputDir = "C:\Users\LAPTOP SPIRIT\Documents\FeedbackHub"
)

$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
$myerpDir = Join-Path $workspaceRoot "myerp"
$targetDir = Join-Path $myerpDir "target"
$packageInputDir = Join-Path $targetDir "package-input"
$distDir = Join-Path $targetDir "dist"

Write-Host "=== Building RedicalClinic Version $Version ===" -ForegroundColor Cyan

# 1. Setup Java and WiX environment
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\dev\Java21") {
        $env:JAVA_HOME = "C:\dev\Java21"
    }
}
$wixBin = "C:\Program Files (x86)\WiX Toolset v3.14\bin"
if (Test-Path $wixBin) {
    $env:PATH = "$wixBin;$env:PATH"
}

# Find Maven
$mvnCmd = "mvn"
if (-not (Get-Command "mvn" -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\dev\netbeans\java\maven\bin\mvn.cmd") {
        $mvnCmd = "C:\dev\netbeans\java\maven\bin\mvn.cmd"
    } elseif (Test-Path "C:\dev\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd") {
        $mvnCmd = "C:\dev\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd"
    }
}

# 2. Compile and package dependencies
Write-Host "Running Maven build..." -ForegroundColor Yellow
Push-Location $myerpDir
try {
    & $mvnCmd clean package dependency:copy-dependencies
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

# 3. Assemble package input directory
Write-Host "Assembling package input directory..." -ForegroundColor Yellow
if (Test-Path $packageInputDir) { Remove-Item -Recurse -Force $packageInputDir }
New-Item -ItemType Directory -Path $packageInputDir | Out-Null
Copy-Item "$targetDir\myerp-1.0-SNAPSHOT.jar" $packageInputDir
Copy-Item "$targetDir\dependency\*" $packageInputDir

# Prepare output dist directory
if (Test-Path $distDir) { Remove-Item -Recurse -Force $distDir }
New-Item -ItemType Directory -Path $distDir | Out-Null

$upgradeUuid = "43341FA5-4B05-33AF-B7F7-4B28C8DD1A4E"

# 4. Generate MSI package
Write-Host "Generating MSI package (Version $Version)..." -ForegroundColor Yellow
jpackage `
    --name RedicalClinic `
    --app-version $Version `
    --input $packageInputDir `
    --main-jar myerp-1.0-SNAPSHOT.jar `
    --main-class com.myerp.Main `
    --type msi `
    --win-shortcut `
    --win-menu `
    --win-dir-chooser `
    --win-upgrade-uuid $upgradeUuid `
    --dest $distDir

# 5. Generate EXE package
Write-Host "Generating EXE package (Version $Version)..." -ForegroundColor Yellow
jpackage `
    --name RedicalClinic `
    --app-version $Version `
    --input $packageInputDir `
    --main-jar myerp-1.0-SNAPSHOT.jar `
    --main-class com.myerp.Main `
    --type exe `
    --win-shortcut `
    --win-menu `
    --win-dir-chooser `
    --win-upgrade-uuid $upgradeUuid `
    --dest $distDir

# 6. Copy to destination directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Host "Copying installers to $OutputDir..." -ForegroundColor Green
Copy-Item "$distDir\RedicalClinic-$Version.msi" $OutputDir -Force
Copy-Item "$distDir\RedicalClinic-$Version.exe" $OutputDir -Force

# Cleanup temp packaging directory
Remove-Item -Recurse -Force $packageInputDir, $distDir -ErrorAction SilentlyContinue

Write-Host "=== Build Completed Successfully! ===" -ForegroundColor Green
Get-ChildItem -Path $OutputDir
