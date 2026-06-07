#!/usr/bin/env pwsh
#Requires -Version 5.1

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Host "Catgirl Downloader Web" -ForegroundColor Cyan
Write-Host ""

# Detect Python
$python = if (Get-Command "python3" -ErrorAction SilentlyContinue) { "python3" }
           elseif (Get-Command "python" -ErrorAction SilentlyContinue) { "python" }
           else { $null }

if (-not $python) {
    Write-Host " E Python is not installed." -ForegroundColor Red
    Write-Host " * Download from https://www.python.org/downloads/"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host " ✓ $($python) $(& $python --version 2>&1)" -ForegroundColor Green

# Check / install dependencies
try {
    & $python -c "import flask" 2>$null
    Write-Host "✓ Dependencies satisfied" -ForegroundColor Green
} catch {
    Write-Host "→ Installing Python dependencies..." -ForegroundColor Yellow
    & $python -m pip install -r requirements.txt --quiet
    Write-Host "✓ Dependencies installed" -ForegroundColor Green
}

Write-Host ""
Write-Host " * Starting server..." -ForegroundColor Cyan
Write-Host " * Open http://localhost:5000 in your browser" -ForegroundColor White
Write-Host ""

& $python server.py
