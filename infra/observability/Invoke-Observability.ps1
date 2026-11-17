<#
.SYNOPSIS
Manages the repository-owned local observability services.
.PARAMETER Action
Start, stop, inspect, or verify the services without deleting their persistent data.
.EXAMPLE
.\infra\observability\Invoke-Observability.ps1 Start
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('Start', 'Stop', 'Status', 'Check')]
    [string] $Action = 'Status'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$dataDirectory = Join-Path $repository '.local/observability'
$composeFile = Join-Path $PSScriptRoot 'compose.yaml'

function Invoke-ObservationCompose {
    param([string[]] $Arguments)
    & docker compose --file $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE."
    }
}

if ($Action -eq 'Start') {
    foreach ($service in @('collector', 'prometheus', 'loki', 'jaeger', 'grafana')) {
        [void] (New-Item -ItemType Directory -Force -Path (Join-Path $dataDirectory $service))
    }
    Invoke-ObservationCompose @('config', '--quiet')
    Invoke-ObservationCompose @('pull')
    Invoke-ObservationCompose @('up', '--detach')
} elseif ($Action -eq 'Stop') {
    Invoke-ObservationCompose @('down')
    return
} elseif ($Action -eq 'Status') {
    Invoke-ObservationCompose @('ps', '--all')
    return
}

$endpoints = [ordered]@{
    Collector = 'http://127.0.0.1:13133/'
    Prometheus = 'http://127.0.0.1:9090/-/ready'
    Loki = 'http://127.0.0.1:3100/ready'
    Jaeger = 'http://127.0.0.1:16686/api/v3/services'
    Grafana = 'http://127.0.0.1:3000/api/health'
}
$deadline = [DateTime]::UtcNow.AddSeconds(90)
foreach ($entry in $endpoints.GetEnumerator()) {
    $ready = $false
    do {
        try {
            $response = Invoke-WebRequest -Uri $entry.Value -TimeoutSec 3
            $ready = $response.StatusCode -eq 200
        } catch {
            if ([DateTime]::UtcNow -ge $deadline) {
                Invoke-ObservationCompose @('logs', '--tail', '30')
                throw "Service $($entry.Key) did not become ready: $($_.Exception.Message)"
            }
            Start-Sleep -Milliseconds 500
        }
    } until ($ready -or [DateTime]::UtcNow -ge $deadline)
    if (-not $ready) {
        throw "Service $($entry.Key) returned an unsuccessful readiness response."
    }
    Write-Output "$($entry.Key): ready ($($entry.Value))"
}
if ($Action -eq 'Start') {
    Invoke-ObservationCompose @('exec', '-T', 'grafana', 'grafana', 'cli', '--homepath', '/usr/share/grafana',
        '--config', '/etc/grafana/grafana.ini', 'admin', 'reset-admin-password', 'admin')
}
Write-Output 'Grafana: http://127.0.0.1:3000; user: admin; password: admin'
