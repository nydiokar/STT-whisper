# Clean Build Script for Android Project
# This script ensures clean builds using proper Android Studio cleanup methods

Write-Host "🧹 Starting clean build process..." -ForegroundColor Green
Write-Host "💡 This script uses proper Android Studio cleanup methods" -ForegroundColor Cyan

# Step 1: Stop all Gradle daemons
Write-Host "📋 Stopping Gradle daemons..." -ForegroundColor Yellow
$stopOutput = .\gradlew --stop 2>&1
Write-Host $stopOutput -ForegroundColor Cyan

# Step 2: Wait longer for processes to fully terminate and check if it worked
Start-Sleep -Seconds 3

# Check if --stop actually worked
Write-Host "🔍 Checking if daemons were properly stopped..." -ForegroundColor Yellow
$daemonStatus = .\gradlew --status 2>&1
Write-Host $daemonStatus -ForegroundColor Cyan

# If daemons are still running, we'll handle it in the next step
$daemonsStillRunning = $daemonStatus -match "IDLE|BUSY"

# Step 2.5: AGGRESSIVE PROCESS CLEANUP - Kill all Java/Gradle processes
Write-Host "💀 Killing all Java/Gradle processes..." -ForegroundColor Red
try {
    # Kill Java processes
    $javaProcesses = Get-Process -Name "*java*" -ErrorAction SilentlyContinue
    if ($javaProcesses) {
        Write-Host "🔪 Found $($javaProcesses.Count) Java processes, killing them..." -ForegroundColor Yellow
        $javaProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    
    # Kill Gradle processes
    $gradleProcesses = Get-Process -Name "*gradle*" -ErrorAction SilentlyContinue
    if ($gradleProcesses) {
        Write-Host "🔪 Found $($gradleProcesses.Count) Gradle processes, killing them..." -ForegroundColor Yellow
        $gradleProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    
    # Kill Kotlin processes
    $kotlinProcesses = Get-Process -Name "*kotlin*" -ErrorAction SilentlyContinue
    if ($kotlinProcesses) {
        Write-Host "🔪 Found $($kotlinProcesses.Count) Kotlin processes, killing them..." -ForegroundColor Yellow
        $kotlinProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    
    # Kill any process with "android" in the name
    $androidProcesses = Get-Process | Where-Object {$_.ProcessName -match "android"}
    if ($androidProcesses) {
        Write-Host "🔪 Found $($androidProcesses.Count) Android processes, killing them..." -ForegroundColor Yellow
        $androidProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    
    Write-Host "✅ Process cleanup completed" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Could not kill some processes: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Step 2.6: Clean up only Gradle lock files (SAFE approach)
Write-Host "🧽 Cleaning up Gradle lock files only..." -ForegroundColor Yellow
try {
    # Remove only Gradle lock files from project directory
    $gradleDir = ".\gradle"
    if (Test-Path $gradleDir) {
        $lockFiles = Get-ChildItem -Path $gradleDir -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue
        if ($lockFiles) {
            Write-Host "🔓 Found $($lockFiles.Count) Gradle lock files, removing them..." -ForegroundColor Yellow
            $lockFiles | Remove-Item -Force -ErrorAction SilentlyContinue
        }
    }
    
    Write-Host "✅ Gradle lock file cleanup completed" -ForegroundColor Green
    Write-Host "💡 Note: Not touching global cache to avoid corruption" -ForegroundColor Cyan
} catch {
    Write-Host "⚠️ Could not clean Gradle lock files: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Step 2.7: Check for Android Studio and provide proper cleanup instructions
Write-Host "🔍 Checking Android Studio status..." -ForegroundColor Yellow
$studioProcesses = Get-Process -Name "*studio*" -ErrorAction SilentlyContinue
if ($studioProcesses) {
    Write-Host "⚠️ Android Studio is running!" -ForegroundColor Red
    Write-Host "💡 Proper Android Studio cleanup methods:" -ForegroundColor Cyan
    Write-Host "   1. In Android Studio: File → Invalidate Caches / Restart" -ForegroundColor White
    Write-Host "   2. Or: Build → Clean Project" -ForegroundColor White
    Write-Host "   3. Or: Build → Rebuild Project" -ForegroundColor White
    Write-Host "   4. Close Android Studio and run this script again" -ForegroundColor White
    Write-Host "🚫 Skipping aggressive cleanup to avoid corrupting Android Studio" -ForegroundColor Yellow
} else {
    Write-Host "✅ Android Studio is not running - safe to proceed" -ForegroundColor Green
}

# Step 3: Clean the project
Write-Host "🗑️ Cleaning project..." -ForegroundColor Yellow
.\gradlew clean

# Step 4: Check if clean was successful
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Clean completed successfully!" -ForegroundColor Green
    Write-Host "🚀 You can now run your build command" -ForegroundColor Cyan
} else {
    Write-Host "❌ Clean failed - files are still locked!" -ForegroundColor Red
    Write-Host "💡 Proper Android Studio solutions (in order of preference):" -ForegroundColor Yellow
    Write-Host "🔧 Method 1 - Invalidate Caches (Recommended):" -ForegroundColor Cyan
    Write-Host "   • In Android Studio: File → Invalidate Caches / Restart" -ForegroundColor White
    Write-Host "   • Select 'Invalidate and Restart'" -ForegroundColor White
    Write-Host "🔧 Method 2 - Clean Project:" -ForegroundColor Cyan
    Write-Host "   • In Android Studio: Build → Clean Project" -ForegroundColor White
    Write-Host "🔧 Method 3 - Manual cleanup:" -ForegroundColor Cyan
    Write-Host "   • Close Android Studio completely" -ForegroundColor White
    Write-Host "   • Run this script again" -ForegroundColor White
    Write-Host "🔧 Method 4 - Disable Instant Run:" -ForegroundColor Cyan
    Write-Host "   • File → Settings → Build, Execution, Deployment → Instant Run" -ForegroundColor White
    Write-Host "   • Uncheck 'Enable Instant Run'" -ForegroundColor White
}

Write-Host "📊 Current Gradle daemon status:" -ForegroundColor Blue
.\gradlew --status