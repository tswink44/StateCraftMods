[CmdletBinding()]
param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$wrapper = Join-Path $PSScriptRoot "gradlew.bat"
$task = ":statecraft-economy:runClient"
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw "The root Gradle wrapper is missing: $wrapper"
}

if ($DryRun) {
    Write-Output "& `"$wrapper`" --project-dir `"$PSScriptRoot`" $task"
    return
}

Push-Location $PSScriptRoot
try {
    & $wrapper --project-dir $PSScriptRoot $task
    if ($LASTEXITCODE -ne 0) {
        throw "The combined StateCraft client exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
