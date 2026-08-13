param(
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [string]$EnvironmentFile,

    [ValidateSet('finguard-day6')]
    [string]$ProjectName = 'finguard-day6',

    [string]$BaseUrl = 'http://127.0.0.1:18080',

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $PSScriptRoot '..\..')
    )
} else {
    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
}
if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
    throw 'RepositoryRoot does not exist'
}
$composeFile = Join-Path $RepositoryRoot 'docker-compose.yml'
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
    (($composePrefix + @('stop', 'app')) -join ' '),
    (($composePrefix + @('start', 'app')) -join ' ')
)

$selector = 'com.finguard.core.messaging.consumer.reconciliation.' +
        'AsyncReconciliationConsumerIntegrationTest#' +
        'shouldDelayOneTransientFailureThenRecover+' +
        'shouldExhaustTwoRetriesThenFailAndDeadLetter'
$command = @(
    'mvn', '-B', '-ntp', "-Dtest=$selector", 'test'
)

if ($DryRun) {
    return [pscustomobject]@{
        ProjectName = $ProjectName
        Selector = $selector
        Command = $command
        Commands = $commands
    }
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile -PathType Leaf)) {
    throw 'EnvironmentFile does not exist'
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

function Wait-ForApplicationHealth {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(90)
    do {
        try {
            $status = (Invoke-WebRequest `
                -UseBasicParsing `
                -Uri "$BaseUrl/actuator/health" `
                -TimeoutSec 3).StatusCode
            if ($status -eq 200) {
                return
            }
        } catch {
            # The app may still be starting; retry until the bounded deadline.
        }
        Start-Sleep -Seconds 1
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw 'Application did not become healthy after the messaging drill'
}

Push-Location $RepositoryRoot
try {
    Invoke-Compose -Arguments @('stop', 'app')
    $started = [DateTimeOffset]::UtcNow
    & $command[0] $command[1..($command.Count - 1)]
    if ($LASTEXITCODE -ne 0) {
        throw "Messaging drill tests failed with exit code $LASTEXITCODE"
    }
    $elapsed = [DateTimeOffset]::UtcNow - $started
} finally {
    Invoke-Compose -Arguments @('start', 'app')
    Wait-ForApplicationHealth
    Pop-Location
}

$reportPath = Join-Path $RepositoryRoot (
    'target\surefire-reports\TEST-com.finguard.core.messaging.' +
    'consumer.reconciliation.' +
    'AsyncReconciliationConsumerIntegrationTest.xml'
)
if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw 'Messaging drill Surefire report was not created'
}
[xml]$report = Get-Content -LiteralPath $reportPath -Encoding utf8 -Raw
$suite = $report.testsuite
if ([int]$suite.tests -ne 2 -or
        [int]$suite.failures -ne 0 -or
        [int]$suite.errors -ne 0 -or
        [int]$suite.skipped -ne 0) {
    throw 'Messaging drill did not produce two clean test results'
}

Write-Output ([pscustomobject]@{
    ProjectName = $ProjectName
    Tests = [int]$suite.tests
    Failures = [int]$suite.failures
    Errors = [int]$suite.errors
    Skipped = [int]$suite.skipped
    ElapsedSeconds = [math]::Round($elapsed.TotalSeconds, 3)
    RetryAttempt = 2
    FailureCode = 'RETRY_EXHAUSTED'
    Cleanup = 'test fixture purged queues and database rows'
})
