# Script to build and run both StateCraft and StateCraftEconomy together
# The StateCraft build.gradle includes Economy source sets directly,
# so we don't need to copy JAR files - just run from StateCraft

$ErrorActionPreference = "Stop"

Write-Host "=== Running StateCraft with Economy (source integration) ===" -ForegroundColor Cyan
Write-Host "Note: Economy mod is loaded directly from source via build.gradle source sets" -ForegroundColor Yellow

Push-Location "StateCraft"
./gradlew runClient
Pop-Location

