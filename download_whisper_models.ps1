# Download Whisper ONNX models from RTranslator
# These are optimized INT8 quantized models for mobile inference

$modelsDir = "android\app\src\main\assets\models"
$baseUrl = "https://github.com/niedev/RTranslator/releases/download/2.0.0"

# Create models directory if it doesn't exist
New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null

# Whisper model files needed
$whisperModels = @(
    "Whisper_cache_initializer.onnx",
    "Whisper_cache_initializer_batch.onnx",
    "Whisper_decoder.onnx",
    "Whisper_detokenizer.onnx",
    "Whisper_encoder.onnx",
    "Whisper_initializer.onnx"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Downloading Whisper ONNX Models" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

foreach ($model in $whisperModels) {
    $url = "$baseUrl/$model"
    $output = "$modelsDir\$model"

    if (Test-Path $output) {
        Write-Host "[SKIP] $model already exists" -ForegroundColor Yellow
    } else {
        Write-Host "[DOWNLOAD] $model..." -ForegroundColor Green
        try {
            Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
            $size = (Get-Item $output).Length / 1MB
            Write-Host "  Downloaded: $([math]::Round($size, 2)) MB`n" -ForegroundColor Gray
        } catch {
            Write-Host "  ERROR: Failed to download $model" -ForegroundColor Red
            Write-Host "  $($_.Exception.Message)`n" -ForegroundColor Red
        }
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Download Complete!" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

# Show total size
$totalSize = (Get-ChildItem $modelsDir -File | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "Total models size: $([math]::Round($totalSize, 2)) MB" -ForegroundColor Cyan
