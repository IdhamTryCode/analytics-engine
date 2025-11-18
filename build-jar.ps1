# PowerShell script to build JAR file for java-engine service
# This must be run before building the docker image

$ErrorActionPreference = "Stop"

# Refresh environment variables first
$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
$javaBin = "$javaHome\bin"

# Try to get from registry, otherwise use default
$env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = $javaHome
}

# Refresh PATH
$userPath = [System.Environment]::GetEnvironmentVariable('PATH', 'User')
$machinePath = [System.Environment]::GetEnvironmentVariable('PATH', 'Machine')
$env:PATH = "$userPath;$machinePath"

Write-Host "Building JAR file for analytics-engine..." -ForegroundColor Cyan
Set-Location analytics-core-legacy

# Check if Java is installed
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "Error: Java is not installed or not in PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "Trying to use Java from: $javaBin" -ForegroundColor Yellow
    if (Test-Path "$javaBin\java.exe") {
        $env:PATH = "$javaBin;$env:PATH"
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    }
    if (-not $javaCmd) {
        Write-Host "Please restart PowerShell and try again" -ForegroundColor Yellow
        exit 1
    }
}

# Check Java version
try {
    $javaVersionOutput = java -version 2>&1 | Select-Object -First 1 | Out-String
    if ($javaVersionOutput -match 'version "(\d+)') {
        $version = [int]$matches[1]
        if ($version -lt 21) {
            Write-Host "Error: Java 21+ is required, but found Java $version" -ForegroundColor Red
            exit 1
        }
        Write-Host "Java version: $version" -ForegroundColor Green
    } else {
        Write-Host "Warning: Could not determine Java version, continuing anyway..." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Warning: Could not check Java version, continuing anyway..." -ForegroundColor Yellow
}

# Get version from pom.xml
$version = (Select-String -Path pom.xml -Pattern '<version>([^<]+)</version>' | Select-Object -First 1).Matches.Groups[1].Value
Write-Host "Building version: $version" -ForegroundColor Cyan

# Build JAR file
Write-Host "Running Maven build (this may take a while)..." -ForegroundColor Cyan

# Set JAVA_HOME if not set
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = $javaHome
}

# Try mvnw.cmd first (Windows), then run Maven wrapper jar directly
if (Test-Path ".\mvnw.cmd") {
    & .\mvnw.cmd clean install -DskipTests -Dcheckstyle.skip=true -Dair.check.skip-dependency=true -P exec-jar
} elseif (Test-Path ".\.mvn\wrapper\maven-wrapper.jar") {
    # Run Maven wrapper jar directly with Java
    $mavenWrapperJar = Resolve-Path ".\.mvn\wrapper\maven-wrapper.jar"
    $projectBaseDir = (Get-Location).Path
    
    Write-Host "Using Maven wrapper jar directly..." -ForegroundColor Cyan
    # First, build all modules without exec-jar profile (to avoid profile validation error)
    Write-Host "Building all modules (with checkstyle and dependency analysis skipped)..." -ForegroundColor Yellow
    & java "-Dmaven.multiModuleProjectDirectory=$projectBaseDir" "-classpath" "$mavenWrapperJar" "org.apache.maven.wrapper.MavenWrapperMain" "clean" "install" "-DskipTests" "-Dcheckstyle.skip=true" "-Dmaven.checkstyle.skip=true" "-Dair.check.skip-checkstyle=true" "-Dair.check.skip-dependency=true"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed. Check the error messages above." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "All modules built successfully. Building executable JAR for analytics-server..." -ForegroundColor Green
    # Now build analytics-server with exec-jar profile to create executable JAR
    & java "-Dmaven.multiModuleProjectDirectory=$projectBaseDir" "-classpath" "$mavenWrapperJar" "org.apache.maven.wrapper.MavenWrapperMain" "package" "-DskipTests" "-Dcheckstyle.skip=true" "-Dmaven.checkstyle.skip=true" "-Dair.check.skip-checkstyle=true" "-Dair.check.skip-dependency=true" "-pl" "analytics-server" "-P" "exec-jar"
} else {
    Write-Host "Error: Maven wrapper not found!" -ForegroundColor Red
    Write-Host "Please ensure you're in the analytics-core-legacy directory" -ForegroundColor Yellow
    exit 1
}

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "JAR file built successfully!" -ForegroundColor Green
    Write-Host "Location: analytics-server\target\analytics-server-${version}-executable.jar" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "You can now build and run docker-compose:" -ForegroundColor Yellow
    Write-Host "  docker-compose build" -ForegroundColor White
    Write-Host "  docker-compose up" -ForegroundColor White
} else {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

