param(
    [Parameter(Mandatory = $true)]
    [string]$EnvironmentFile,

    [ValidateSet('finguard-day6')]
    [string]$ProjectName = 'finguard-day6',

    [string]$BaseUrl = 'http://127.0.0.1:18080',

    [string]$OutputDirectory = 'security/results/drills',

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
$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot $OutputDirectory)
    )
}
$containerName = 'finguard-day6-misconfigured-app'
$composePrefix = @(
    'docker', 'compose',
    '--project-name', $ProjectName,
    '--env-file', $resolvedEnvironmentFile,
    '--file', $composeFile
)
$badRun = $composePrefix + @(
    'run', '--no-deps', '--name', $containerName,
    '-e', 'MYSQL_PORT=1', 'app'
)
$commands = @(
    "docker container inspect $containerName",
    "docker container rm --force $containerName if it exists",
    ($badRun -join ' '),
    "docker inspect --format STATE_AND_EXIT_ONLY $containerName",
    "docker logs --tail 80 $containerName",
    "GET $BaseUrl/actuator/health"
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
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$diagnosticLog = Join-Path $resolvedOutput 'misconfigured-app.log'

function Remove-DrillContainerIfPresent {
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & docker container inspect $containerName *> $null
        $containerExists = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($containerExists) {
        & docker container rm --force $containerName *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not remove the Day 6 misconfiguration container'
        }
    }
}

Remove-DrillContainerIfPresent
try {
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $badRun[0] $badRun[1..($badRun.Count - 1)] *> $diagnosticLog
        $badExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($badExitCode -eq 0) {
        throw 'The intentionally misconfigured application unexpectedly started'
    }

    $state = & docker inspect `
        --format '{{.State.Status}}|{{.State.ExitCode}}' `
        $containerName
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($state)) {
        throw 'Could not inspect the failed one-off application container'
    }

    $logText = Get-Content -LiteralPath $diagnosticLog -Raw -Encoding utf8
    $rootCauseObserved = $logText -match (
        'Communications link failure|Connection refused|' +
        'Could not connect|Unable to obtain connection'
    )
    if (-not $rootCauseObserved) {
        throw 'Failed application log did not show a database connectivity root cause'
    }
} finally {
    Remove-DrillContainerIfPresent
}

$health = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "$BaseUrl/actuator/health" `
    -TimeoutSec 5
if ($health.StatusCode -ne 200) {
    throw "Healthy application probe returned HTTP $($health.StatusCode)"
}

Write-Output ([pscustomobject]@{
    ProjectName = $ProjectName
    FailedContainerState = $state
    BadExitCode = $badExitCode
    RootCause = 'invalid MySQL port prevented database connectivity'
    RecoveryHealth = $health.StatusCode
    DiagnosticLog = $diagnosticLog
})
