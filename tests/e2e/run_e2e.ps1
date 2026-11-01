# CutPro E2E Automated Test Suite PowerShell Wrapper
# Usage:
#   .\tests\e2e\run_e2e.ps1 -Tier 1
#   .\tests\e2e\run_e2e.ps1 -All
#   .\tests\e2e\run_e2e.ps1 -Filter "FEAT-CORE"

param(
    [int]$Tier,
    [switch]$All,
    [string]$Filter,
    [switch]$VerboseOutput
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
Set-Location $projectRoot

$cmdArgs = @("tests/e2e/run_tests.py")

if ($All) {
    $cmdArgs += "--all"
}
if ($Tier -gt 0) {
    $cmdArgs += "--tier"
    $cmdArgs += "$Tier"
}
if ($Filter) {
    $cmdArgs += "--filter"
    $cmdArgs += $Filter
}
if ($VerboseOutput) {
    $cmdArgs += "--verbose"
}

Write-Host "Running CutPro Opaque-Box E2E Tests with Python..." -ForegroundColor Cyan
python @cmdArgs
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host "All CutPro E2E Tests Passed Successfully." -ForegroundColor Green
} else {
    Write-Host "CutPro E2E Tests Encountered Failures (Exit Code $exitCode)." -ForegroundColor Red
}

exit $exitCode
