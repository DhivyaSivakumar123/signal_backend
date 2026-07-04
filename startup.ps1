$ErrorActionPreference = "Stop"
$JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:JAVA_HOME = $JAVA_HOME
$BackendDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Source .env file
Get-Content "$BackendDir\.env" | Where-Object { $_ -match '^\s*([^#][^=]+)=(.+)\s*$' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    Set-Item -Path "env:$key" -Value $value.Trim('"', "'")
}

$env:SIGNAL_FRONTEND_URL = "http://localhost:4200"

# Port order: 8080 gateway, 8081 auth, 8082 integration, 8083 scoring, 8084 triage, 8085 analytics
$services = @(
    @{ Name = "auth-service";     Port = 8081; Jvm = "" },
    @{ Name = "integration-service"; Port = 8082; Jvm = "" },
    @{ Name = "scoring-service";  Port = 8083; Jvm = "" },
    @{ Name = "triage-service";   Port = 8084; Jvm = "" },
    @{ Name = "analytics-service";Port = 8085; Jvm = "" },
    @{ Name = "gateway-service";  Port = 8080; Jvm = "-Dreactor.netty.pool.cleanupInterval=30000" }
)

Write-Host "=== Building all services ==="
& "$BackendDir\mvnw.cmd" clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

Write-Host "=== Starting services ==="
$jobs = @()
foreach ($svc in $services) {
    $jar = "$BackendDir\$($svc.Name)\target\$($svc.Name)-1.0.0.jar"
    $port = $svc.Port
    $jvm = $svc.Jvm
    $job = Start-Job -ScriptBlock {
        param($j, $p, $jvmArgs)
        Set-Location (Split-Path -Parent $j)
        $env:JAVA_HOME = $using:JAVA_HOME
        java $jvmArgs -jar $j --server.port=$p
    } -ArgumentList $jar, $port, $jvm
    $jobs += @{ Name = $svc.Name; Job = $job; Port = $port }
    Write-Host "  Started $($svc.Name) on port $port (Job $($job.Id))"
}

Write-Host "=== Waiting for services to be ready ==="
foreach ($sj in $jobs) {
    $name = $sj.Name
    $port = $sj.Port
    $maxWait = 90
    $ok = $false
    for ($i = 0; $i -lt $maxWait; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -UseBasicParsing -ErrorAction Stop
            if ($resp.status -eq "UP") { $ok = $true; break }
        } catch {}
        Start-Sleep -Seconds 1
    }
    if ($ok) { Write-Host "  $name : UP (port $port)" }
    else { Write-Host "  $name : TIMEOUT (port $port)" -ForegroundColor Yellow }
}

Write-Host "=== All services started ==="
Write-Host "Gateway: http://localhost:8080"
Write-Host ""

try {
    # Keep script running so jobs stay alive
    while ($true) {
        $running = $jobs | Where-Object { (Get-Job -Id $_.Job.Id -ErrorAction SilentlyContinue).State -eq "Running" }
        if ($running.Count -eq 0) { Write-Host "All services stopped."; break }
        Start-Sleep -Seconds 10
    }
} finally {
    Write-Host "Shutting down all services..."
    foreach ($sj in $jobs) {
        Stop-Job -Id $sj.Job.Id -ErrorAction SilentlyContinue
    }
}
