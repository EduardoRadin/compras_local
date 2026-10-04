# Gera app-debug.apk — define JAVA_HOME automaticamente (Android Studio)
$ErrorActionPreference = "Stop"

$javaCandidates = @(
    "C:\Program Files\Android\Android Studio\jbr",
    "${env:ProgramFiles}\Android\Android Studio\jbr",
    "${env:LocalAppData}\Programs\Android\Android Studio\jbr"
)

$javaHome = $javaCandidates | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1

if (-not $javaHome) {
    Write-Host "Java nao encontrado. Instale o Android Studio ou defina JAVA_HOME manualmente." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "Gerando APK..." -ForegroundColor Cyan

& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "APK pronto:" -ForegroundColor Green
    Write-Host $apk -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Copie app-debug.apk para o celular e instale." -ForegroundColor Green
} else {
    Write-Host "Build OK, mas APK nao encontrado em app\build\outputs\apk\debug\" -ForegroundColor Yellow
}
