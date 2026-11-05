$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Join-Path $projectRoot 'venv\Scripts\python.exe'
$requirements = Join-Path $projectRoot 'requirements-fastapi.txt'

function Test-ListeningPort {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Wait-ForPort {
    param([int]$Port, [int]$TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-ListeningPort -Port $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

if (-not (Test-Path -LiteralPath $python)) {
    throw "Python virtual environment not found: $python"
}

Push-Location $projectRoot
try {
    & $python -c 'import fastapi' 2>$null
    if ($LASTEXITCODE -ne 0) {
        & $python -m pip install -r $requirements
        if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed.' }
    }

    if (Test-ListeningPort -Port 8000) {
        Write-Host 'FastAPI is already listening on port 8000.'
    } else {
        $backend = Start-Process -FilePath $python `
            -ArgumentList '-m', 'uvicorn', 'backend.main:app', '--host', '127.0.0.1', '--port', '8000', '--reload' `
            -WorkingDirectory $projectRoot `
            -RedirectStandardOutput (Join-Path $projectRoot 'backend-dev.out.log') `
            -RedirectStandardError (Join-Path $projectRoot 'backend-dev.err.log') `
            -WindowStyle Hidden -PassThru
        Write-Host "Starting FastAPI, PID: $($backend.Id)"
    }

    if (-not (Wait-ForPort -Port 8000)) { throw 'FastAPI startup timed out. Check backend-dev.err.log.' }
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8000/api/health' -TimeoutSec 5
    if ($health.status -ne 'ok') { throw 'FastAPI health check failed.' }

    Write-Host 'FastAPI started successfully:'
    Write-Host '  API:      http://127.0.0.1:8000'
    Write-Host '  API docs: http://127.0.0.1:8000/docs'
} finally {
    Pop-Location
}
