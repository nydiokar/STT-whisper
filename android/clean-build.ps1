# Clean Build Script for Android Project
# This script ensures clean builds by stopping all Gradle daemons first

Write-Host "🧹 Starting clean build process..." -ForegroundColor Green

# Step 1: Stop all Gradle daemons
Write-Host "📋 Stopping Gradle daemons..." -ForegroundColor Yellow
.\gradlew --stop

# Step 2: Wait a moment for processes to fully terminate
Start-Sleep -Seconds 2

# Step 3: Clean the project
Write-Host "🗑️ Cleaning project..." -ForegroundColor Yellow
.\gradlew clean

# Step 4: Check if clean was successful
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Clean completed successfully!" -ForegroundColor Green
    Write-Host "🚀 You can now run your build command" -ForegroundColor Cyan
} else {
    Write-Host "❌ Clean failed. You may need to restart Android Studio or kill remaining Java processes." -ForegroundColor Red
    Write-Host "💡 Try: Get-Process java | Stop-Process -Force" -ForegroundColor Yellow
}

Write-Host "📊 Current Gradle daemon status:" -ForegroundColor Blue
.\gradlew --status
