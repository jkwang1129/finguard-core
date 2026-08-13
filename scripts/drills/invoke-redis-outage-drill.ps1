param(
    [Parameter(Mandatory = $true)]
    [string]$EnvironmentFile,

    [ValidateSet('finguard-day6')]
    [string]$ProjectName = 'finguard-day6',

    [string]$BaseUrl = 'http://127.0.0.1:18080',

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath(
    $EnvironmentFile
)
$composePrefix = @(
    'docker', 'compose',
    '--project-name', $ProjectName,
    '--env-file', $resolvedEnvironmentFile,
    '--file', $composeFile
)
$commands = @(
    (($composePrefix + @('ps')) -join ' '),
    (($composePrefix + @('stop', 'redis')) -join ' '),
    (($composePrefix + @('start', 'redis')) -join ' ')
)

if ($DryRun) {
    return [pscustomobject]@{
        ProjectName = $ProjectName
        Commands = $commands
    }
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile -PathType Leaf)) {
    throw 'EnvironmentFile does not exist'
}
if ([string]::IsNullOrWhiteSpace($env:FINGUARD_DAY6_ADMIN_USERNAME) -or
        [string]::IsNullOrWhiteSpace(
            $env:FINGUARD_DAY6_ADMIN_PASSWORD
        )) {
    throw 'Day 6 drill credentials must be supplied through process environment variables'
}

function Invoke-Compose {
    param([string[]]$Arguments)
    $allArguments = $composePrefix[1..($composePrefix.Count - 1)] +
            $Arguments
    & $composePrefix[0] $allArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Login {
    $body = @{
        username = $env:FINGUARD_DAY6_ADMIN_USERNAME
        password = $env:FINGUARD_DAY6_ADMIN_PASSWORD
    } | ConvertTo-Json -Compress
    $response = Invoke-WebRequest `
        -UseBasicParsing `
        -Method Post `
        -Uri "$BaseUrl/api/auth/login" `
        -ContentType 'application/json' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
    if ($response.StatusCode -ne 200) {
        throw "Login returned HTTP $($response.StatusCode)"
    }
    return ($response.Content | ConvertFrom-Json).accessToken
}

function Invoke-AuthenticatedGet {
    param(
        [string]$Path,
        [string]$Token
    )
    $response = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$BaseUrl$Path" `
        -Headers @{ Authorization = "Bearer $Token" }
    return $response.StatusCode
}

function Get-HealthStatus {
    try {
        return (Invoke-WebRequest `
            -UseBasicParsing `
            -Uri "$BaseUrl/actuator/health" `
            -TimeoutSec 3).StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

$baselineToken = Invoke-Login
$baselineHealth = Get-HealthStatus
if ($baselineHealth -ne 200) {
    throw "Baseline health was HTTP $baselineHealth"
}
$baselineStatistics = Invoke-AuthenticatedGet `
    -Path '/api/statistics/overview' `
    -Token $baselineToken

$outageHealth = 0
$outageStatistics = 0
$outageLogin = 0
$recoveredHealth = 0
try {
    Invoke-Compose -Arguments @('stop', 'redis')

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(20)
    do {
        $outageHealth = Get-HealthStatus
        if ($outageHealth -ne 200) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    $outageToken = Invoke-Login
    $outageLogin = 200
    $outageStatistics = Invoke-AuthenticatedGet `
        -Path '/api/statistics/overview' `
        -Token $outageToken
} finally {
    Invoke-Compose -Arguments @('start', 'redis')
}

$redisDeadline = [DateTimeOffset]::UtcNow.AddSeconds(45)
do {
    $redisArguments = $composePrefix[1..($composePrefix.Count - 1)] + @(
        'exec', '-T', 'redis', 'sh', '-c',
        'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
    )
    & $composePrefix[0] $redisArguments *> $null
    if ($LASTEXITCODE -eq 0) {
        break
    }
    Start-Sleep -Seconds 1
} while ([DateTimeOffset]::UtcNow -lt $redisDeadline)
if ($LASTEXITCODE -ne 0) {
    throw 'Redis did not recover before the deadline'
}

$healthDeadline = [DateTimeOffset]::UtcNow.AddSeconds(45)
do {
    $recoveredHealth = Get-HealthStatus
    if ($recoveredHealth -eq 200) {
        break
    }
    Start-Sleep -Seconds 1
} while ([DateTimeOffset]::UtcNow -lt $healthDeadline)
if ($recoveredHealth -ne 200) {
    throw "Application health did not recover: HTTP $recoveredHealth"
}
$recoveryToken = Invoke-Login
$recoveredStatistics = Invoke-AuthenticatedGet `
    -Path '/api/statistics/overview' `
    -Token $recoveryToken

Write-Output ([pscustomobject]@{
    ProjectName = $ProjectName
    BaselineHealth = $baselineHealth
    BaselineStatistics = $baselineStatistics
    OutageHealth = $outageHealth
    OutageLogin = $outageLogin
    OutageStatistics = $outageStatistics
    RecoveredHealth = $recoveredHealth
    RecoveredStatistics = $recoveredStatistics
})
