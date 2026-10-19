# run-backend.ps1
# Get the directory of the script
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Signal Backend Services      " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. Load environment variables from .env
$EnvFile = Join-Path $ScriptDir ".env"
if (Test-Path $EnvFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Yellow
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                $key = $parts[0].Trim()
                $value = $parts[1].Trim()
                if ($value.StartsWith('"') -and $value.EndsWith('"')) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                [System.Environment]::SetEnvironmentVariable($key, $value, [System.EnvironmentVariableTarget]::Process)
                # Set it for the current powershell session environment too
                Set-Item "env:$key" $value
                if ($key -eq "SPRING_DATA_REDIS_HOST" -and ($value.StartsWith("redis://") -or $value.StartsWith("rediss://"))) {
                    [System.Environment]::SetEnvironmentVariable("SPRING_DATA_REDIS_URL", $value, [System.EnvironmentVariableTarget]::Process)
                    Set-Item "env:SPRING_DATA_REDIS_URL" $value
                }
            }
        }
    }
} else {
    Write-Warning ".env file not found in $ScriptDir!"
}

# 2. Build the project
Write-Host "Building parent Maven project and common-lib..." -ForegroundColor Yellow
& .\mvnw.cmd clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed! Cannot start services."
    exit 1
}

# 3. Start services in separate cmd windows so their logs are visible
$services = @(
    @{ name = "auth-service"; port = 8081 },
    @{ name = "integration-service"; port = 8082 },
    @{ name = "scoring-service"; port = 8083 },
    @{ name = "triage-service"; port = 8084 },
    @{ name = "analytics-service"; port = 8085 },
    @{ name = "gateway-service"; port = 8080 }
)

Write-Host "Starting microservices in separate windows..." -ForegroundColor Yellow
foreach ($svc in $services) {
    $name = $svc.name
    $port = $svc.port
    Write-Host "Launching $name on port $port..." -ForegroundColor Green
    
    $argList = "/k title $name && cd $name && ..\mvnw.cmd spring-boot:run"
    Start-Process cmd.exe -ArgumentList $argList -WorkingDirectory $ScriptDir
    Start-Sleep -Seconds 2
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All services launched in separate windows!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
