# PowerShell script to build docker-compose with automatic JAR build if needed
# This script checks if the JAR file exists, builds it if missing, then builds docker images

$ErrorActionPreference = "Stop"

# Get version from pom.xml or use default
$version = "0.21.3-SNAPSHOT"
$pomPath = "analytics-core-legacy\pom.xml"
if (Test-Path $pomPath) {
    $versionMatch = Select-String -Path $pomPath -Pattern '<version>([^<]+)</version>' | Select-Object -First 1
    if ($versionMatch) {
        $version = $versionMatch.Matches.Groups[1].Value
    }
}

$jarDir = "analytics-core-legacy\analytics-server\target"
function Get-JarPath {
    param(
        [string] $Dir,
        [string] $FallbackName
    )
    if (Test-Path $Dir) {
        $jar = Get-ChildItem -Path $Dir -Filter "analytics-server-*-executable.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jar) {
            return $jar.FullName
        }
    }
    return Join-Path $Dir $FallbackName
}
$jarPath = Get-JarPath -Dir $jarDir -FallbackName "analytics-server-${version}-executable.jar"

Write-Host "Checking for JAR file: $jarPath" -ForegroundColor Cyan

# Check if JAR file exists
if (-not (Test-Path $jarPath)) {
    Write-Host "JAR file not found. Building JAR file..." -ForegroundColor Yellow
    Write-Host ""
    
    # Run build-jar.ps1
    if (Test-Path ".\build-jar.ps1") {
        & .\build-jar.ps1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "JAR build failed!" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "Error: build-jar.ps1 not found!" -ForegroundColor Red
        Write-Host "Please run: .\build-jar.ps1 manually first" -ForegroundColor Yellow
        exit 1
    }
    
    # Verify JAR was created
    $jarPath = Get-JarPath -Dir $jarDir -FallbackName "analytics-server-${version}-executable.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "Error: JAR file still not found after build!" -ForegroundColor Red
        Write-Host "Expected location: $jarPath" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host ""
    Write-Host "JAR file built successfully!" -ForegroundColor Green
} else {
    Write-Host "JAR file found. Skipping build." -ForegroundColor Green
}

Write-Host ""
Write-Host "Building docker images..." -ForegroundColor Cyan
Write-Host ""

# Run docker compose build with all passed arguments
& docker compose -f docker-compose.yaml build $args

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker compose build failed!" -ForegroundColor Red
    exit 1
}

