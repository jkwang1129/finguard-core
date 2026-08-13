[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'

$projectName = 'finguard-day7'
$containerPrefix = 'finguard-day7'
$services = @(
    'mysql',
    'rabbitmq',
    'redis',
    'app',
    'prometheus',
    'grafana'
)
$ports = [pscustomobject][ordered]@{
    App = 18080
    MySql = 13306
    RabbitMq = 15674
    RabbitMqManagement = 15673
    Redis = 16379
    Prometheus = 19090
    Grafana = 13000
}
$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'
$linuxComposeFile = Join-Path $repositoryRoot 'compose.linux.yml'
$functionsFile = Join-Path $PSScriptRoot 'week6-day7-functions.ps1'
if (-not (Test-Path -LiteralPath $functionsFile -PathType Leaf)) {
    throw "Day 7 acceptance functions are missing: $functionsFile"
}
. $functionsFile

$composePrefix = @(
    'docker compose',
    '--project-name finguard-day7',
    '--env-file <temporary-env>',
    ('--file "{0}"' -f $composeFile)
) -join ' '
$linuxComposePrefix = @(
    'docker compose',
    '--project-name finguard-day7',
    '--env-file <temporary-linux-env>',
    ('--file "{0}"' -f $linuxComposeFile)
) -join ' '

$plan = [pscustomobject]@{
    ProjectName = $projectName
    ContainerPrefix = $containerPrefix
    Services = $services
    Ports = $ports
    Commands = @(
        "$composePrefix config --quiet",
        "$linuxComposePrefix config --quiet",
        "$composePrefix build app",
        "$composePrefix up -d $($services -join ' ')",
        "$composePrefix restart app"
    )
    CleanupCommands = @(
        "$composePrefix down --volumes --remove-orphans",
        'docker image rm finguard-core:day7'
    )
}

if ($DryRun) {
    return $plan
}

function Get-MavenJavaVersion {
    $versionOutput = (& mvn -version 2>&1 | Out-String)
    $match = [regex]::Match(
        $versionOutput,
        '(?m)^Java version:\s*([^,\r\n]+)'
    )
    if (-not $match.Success) {
        throw 'Maven did not report its Java runtime version'
    }
    return $match.Groups[1].Value.Trim()
}

if ($PreflightOnly) {
    foreach ($commandName in @('mvn', 'docker', 'curl.exe')) {
        if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
            throw "Required command is unavailable: $commandName"
        }
    }
    $mavenJavaVersion = Get-MavenJavaVersion
    if ($mavenJavaVersion -notmatch '^17\.') {
        throw "Day 7 requires Maven Java 17: $mavenJavaVersion"
    }
    & docker version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is unavailable'
    }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose is unavailable'
    }
    return [pscustomobject]@{
        MavenJavaVersion = $mavenJavaVersion
        DockerAvailable = $true
        ComposeAvailable = $true
        MutatedDockerState = $false
    }
}

function New-RandomBase64([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function New-RandomPassword([string]$Prefix) {
    return $Prefix + '-' + (New-RandomBase64 18).Replace('=', 'A')
}

function Write-EnvironmentFile(
        [string]$Path,
        [hashtable]$Values) {
    $lines = foreach ($key in $Values.Keys) {
        '{0}={1}' -f $key, $Values[$key]
    }
    [System.IO.File]::WriteAllLines(
        $Path,
        $lines,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose `
        --project-name $projectName `
        --env-file $script:environmentFile `
        --file $composeFile `
        @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE"
    }
}

function Invoke-ComposeText([string[]]$Arguments) {
    $output = & docker compose `
        --project-name $projectName `
        --env-file $script:environmentFile `
        --file $composeFile `
        @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Wait-Until(
        [scriptblock]$Condition,
        [int]$TimeoutSeconds,
        [string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            if (& $Condition) {
                return
            }
        } catch {
            # The dependency may still be starting. Keep polling until timeout.
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Description"
}

function Invoke-JsonRequest(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [string]$BearerToken) {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
        $headers.Authorization = "Bearer $BearerToken"
    }
    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) {
            $Body
        } else {
            $Body | ConvertTo-Json -Depth 10 -Compress
        }
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    $response = Invoke-WebRequest @parameters
    $payload = ConvertFrom-FinguardUtf8Json -Content $response.Content
    return [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        Headers = $response.Headers
        Body = $payload
    }
}

function Get-HttpStatus(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [string]$BearerToken) {
    try {
        return (Invoke-JsonRequest $Method $Uri $Body $BearerToken).StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function Assert-Equal(
        [object]$Actual,
        [object]$Expected,
        [string]$Description) {
    if ($Actual -ne $Expected) {
        throw "$Description expected '$Expected' but was '$Actual'"
    }
}

function Get-MySqlScalar([string]$Sql) {
    $output = Invoke-ComposeText @(
        'exec', '-T',
        '-e', "MYSQL_PWD=$script:mysqlPassword",
        'mysql',
        'mysql',
        '--batch', '--skip-column-names',
        '-u', 'finguard',
        '-D', 'finguard',
        '-e', $Sql
    )
    return ($output | Select-Object -Last 1).Trim()
}

function Get-ContainerHealth([string]$Service) {
    $containerId = (
        Invoke-ComposeText @('ps', '-q', $Service) |
            Select-Object -First 1
    )
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        return ''
    }
    $health = & docker inspect `
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' `
        $containerId
    if ($LASTEXITCODE -ne 0) {
        return ''
    }
    return $health.Trim()
}

function Wait-ForJob(
        [string]$Uri,
        [string]$BearerToken,
        [string[]]$TerminalStatuses,
        [int]$TimeoutSeconds) {
    $result = $null
    Wait-Until {
        $script:jobResponse = Invoke-JsonRequest `
            'GET' $Uri $null $BearerToken
        $script:jobResponse.StatusCode -eq 200 -and
            $TerminalStatuses -contains $script:jobResponse.Body.status
    } $TimeoutSeconds "terminal job state at $Uri"
    $result = $script:jobResponse
    Remove-Variable -Name jobResponse -Scope Script -ErrorAction SilentlyContinue
    return $result
}

function New-CurrentDemoCsv(
        [string]$SourcePath,
        [string]$DestinationPath) {
    $lines = [System.IO.File]::ReadAllLines($SourcePath)
    $baseTime = (Get-Date).AddMinutes(-10)
    $lines[1] = $lines[1] -replace '2026-08-13 10:00:00', (
        $baseTime.ToString('yyyy-MM-dd HH:mm:ss')
    )
    $lines[2] = $lines[2] -replace '2026-08-13 10:05:00', (
        $baseTime.AddMinutes(1).ToString('yyyy-MM-dd HH:mm:ss')
    )
    [System.IO.File]::WriteAllLines(
        $DestinationPath,
        $lines,
        (New-Object System.Text.UTF8Encoding($false))
    )
    return $baseTime
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'finguard-day7-' + [Guid]::NewGuid().ToString('N')
)
$environmentFile = Join-Path $temporaryRoot 'day7.env'
$linuxEnvironmentFile = Join-Path $temporaryRoot 'day7-linux.env'
$temporaryCsv = Join-Path $temporaryRoot 'demo-import.csv'
$outputFile = Join-Path $temporaryRoot 'upload-response.json'
$script:mysqlPassword = New-RandomPassword 'MySql'
$rootPassword = New-RandomPassword 'Root'
$rabbitPassword = New-RandomPassword 'Rabbit'
$redisPassword = New-RandomPassword 'Redis'
$grafanaPassword = New-RandomPassword 'Grafana'
$adminPassword = New-RandomPassword 'Admin'
$reviewerPassword = New-RandomPassword 'Reviewer'
$jwtSecret = New-RandomBase64 48
$adminUsername = 'day7-admin'
$reviewerUsername = 'day7-reviewer'
$baseUri = 'http://127.0.0.1:{0}' -f $ports.App
$cleanupAttempted = $false
$cleanupPassed = $false
$businessFlowPassed = $false
$restartPassed = $false
$servicesHealthy = $false
$flywayVersion = $null
$openApiPathCount = $null
$prometheusTargetUp = $false
$preexistingContainers = @(
    & docker ps -a --format '{{.Names}}'
)
$result = $null

try {
    New-Item -ItemType Directory -Force -Path $temporaryRoot | Out-Null
    foreach ($requiredFile in @(
            $composeFile,
            $linuxComposeFile,
            (Join-Path $repositoryRoot 'Dockerfile'),
            (Join-Path $repositoryRoot 'sample-data\demo-import.csv'))) {
        if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
            throw "Required Day 7 file is missing: $requiredFile"
        }
    }
    foreach ($commandName in @('mvn', 'docker', 'curl.exe')) {
        if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
            throw "Required command is unavailable: $commandName"
        }
    }
    $mavenJavaVersion = Get-MavenJavaVersion
    if ($mavenJavaVersion -notmatch '^17\.') {
        throw "Day 7 requires Maven Java 17: $mavenJavaVersion"
    }
    & docker version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is unavailable'
    }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose is unavailable'
    }
    $existingDay7 = @(
        & docker ps -a `
            --filter "label=com.docker.compose.project=$projectName" `
            --format '{{.Names}}'
    )
    if ($existingDay7.Count -gt 0) {
        throw 'A finguard-day7 Compose project already exists'
    }
    foreach ($port in @(
            $ports.App,
            $ports.MySql,
            $ports.RabbitMq,
            $ports.RabbitMqManagement,
            $ports.Redis,
            $ports.Prometheus,
            $ports.Grafana)) {
        if (Get-NetTCPConnection `
                -LocalPort $port `
                -State Listen `
                -ErrorAction SilentlyContinue) {
            throw "Isolated Day 7 port is already in use: $port"
        }
    }

    $environmentValues = [ordered]@{
        FINGUARD_APP_IMAGE = 'finguard-core:day7'
        FINGUARD_CONTAINER_PREFIX = $containerPrefix
        FINGUARD_APP_PORT = $ports.App
        MYSQL_PORT = $ports.MySql
        MYSQL_ROOT_PASSWORD = $rootPassword
        MYSQL_DATABASE = 'finguard'
        MYSQL_USER = 'finguard'
        MYSQL_PASSWORD = $script:mysqlPassword
        RABBITMQ_PORT = $ports.RabbitMq
        RABBITMQ_MANAGEMENT_PORT = $ports.RabbitMqManagement
        RABBITMQ_USERNAME = 'finguard'
        RABBITMQ_PASSWORD = $rabbitPassword
        RABBITMQ_VIRTUAL_HOST = '/'
        REDIS_PORT = $ports.Redis
        REDIS_PASSWORD = $redisPassword
        JWT_SECRET_BASE64 = $jwtSecret
        PROMETHEUS_PORT = $ports.Prometheus
        GRAFANA_PORT = $ports.Grafana
        GRAFANA_ADMIN_USER = 'admin'
        GRAFANA_ADMIN_PASSWORD = $grafanaPassword
        FINGUARD_AUTH_BOOTSTRAP_ENABLED = 'true'
        FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME = $adminUsername
        FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD = $adminPassword
        FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME = $reviewerUsername
        FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD = $reviewerPassword
    }
    Write-EnvironmentFile $environmentFile $environmentValues
    $linuxValues = [ordered]@{}
    foreach ($key in $environmentValues.Keys) {
        $linuxValues[$key] = $environmentValues[$key]
    }
    $linuxValues.FINGUARD_APP_IMAGE = (
        'ghcr.io/jkwang1129/finguard-core:' + ('0' * 40)
    )
    $linuxValues.FINGUARD_PROJECT_NAME = $projectName
    Write-EnvironmentFile $linuxEnvironmentFile $linuxValues

    Invoke-Compose @('config', '--quiet')
    & docker compose `
        --project-name $projectName `
        --env-file $linuxEnvironmentFile `
        --file $linuxComposeFile `
        config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'Linux Compose configuration is invalid'
    }

    Invoke-Compose @('build', 'app')
    Invoke-Compose @('up', '-d')
    foreach ($service in $services) {
        Wait-Until {
            (Get-ContainerHealth $service) -eq 'healthy'
        } 240 "$service health"
    }
    $servicesHealthy = $true

    $flywayVersion = Get-MySqlScalar (
        'SELECT version FROM flyway_schema_history ' +
        'WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;'
    )
    Assert-Equal $flywayVersion '11' 'Flyway version'

    $health = Invoke-JsonRequest 'GET' "$baseUri/actuator/health" $null ''
    Assert-Equal $health.StatusCode 200 'Application health HTTP status'
    if ($health.Body.status -ne 'UP') {
        $bodyType = if ($null -eq $health.Body) {
            '<null>'
        } else {
            $health.Body.GetType().FullName
        }
        $safeBody = $health.Body | ConvertTo-Json -Depth 5 -Compress
        throw "Application health payload was not parsed: type=$bodyType body=$safeBody"
    }
    $openApi = Invoke-JsonRequest 'GET' "$baseUri/v3/api-docs" $null ''
    $openApiPathCount = @($openApi.Body.paths.PSObject.Properties).Count
    Assert-Equal $openApiPathCount 18 'OpenAPI path count'

    Wait-Until {
        $targets = Invoke-JsonRequest `
            'GET' `
            ('http://127.0.0.1:{0}/api/v1/targets' -f $ports.Prometheus) `
            $null ''
        @($targets.Body.data.activeTargets | Where-Object {
            $_.health -eq 'up'
        }).Count -ge 1
    } 60 'Prometheus application target UP'
    $prometheusTargetUp = $true
    $grafanaHealth = Invoke-JsonRequest `
        'GET' `
        ('http://127.0.0.1:{0}/api/health' -f $ports.Grafana) `
        $null ''
    Assert-Equal $grafanaHealth.StatusCode 200 'Grafana health status'

    $adminLogin = Invoke-JsonRequest 'POST' "$baseUri/api/auth/login" @{
        username = $adminUsername
        password = $adminPassword
    } ''
    $reviewerLogin = Invoke-JsonRequest 'POST' "$baseUri/api/auth/login" @{
        username = $reviewerUsername
        password = $reviewerPassword
    } ''
    $adminToken = $adminLogin.Body.accessToken
    $reviewerToken = $reviewerLogin.Body.accessToken
    if ([string]::IsNullOrWhiteSpace($adminToken) -or
            [string]::IsNullOrWhiteSpace($reviewerToken)) {
        throw 'Bootstrap login did not return both JWTs'
    }
    Assert-Equal `
        (Get-HttpStatus 'GET' "$baseUri/api/accounts" $null '') `
        401 `
        'Anonymous account query'
    $accountBody = @{
        accountNo = 'DAY7-DEMO'
        accountName = 'Day 7 Demo'
        accountType = 'BANK'
    }
    $accountsBefore = Get-MySqlScalar (
        "SELECT COUNT(*) FROM accounts WHERE account_no='DAY7-DEMO';"
    )
    Assert-Equal `
        (Get-HttpStatus 'POST' "$baseUri/api/accounts" $accountBody $reviewerToken) `
        403 `
        'Reviewer account creation'
    $accountsAfterDenied = Get-MySqlScalar (
        "SELECT COUNT(*) FROM accounts WHERE account_no='DAY7-DEMO';"
    )
    Assert-Equal $accountsAfterDenied $accountsBefore 'Denied write side effect'
    Assert-Equal `
        (Get-HttpStatus 'GET' "$baseUri/api/audit-logs" $null $adminToken) `
        200 `
        'Admin audit query'
    Assert-Equal `
        (Get-HttpStatus 'GET' "$baseUri/api/audit-logs" $null $reviewerToken) `
        403 `
        'Reviewer audit query'

    $createdAccount = Invoke-JsonRequest `
        'POST' "$baseUri/api/accounts" $accountBody $adminToken
    Assert-Equal $createdAccount.StatusCode 201 'Account creation'
    $accountId = [long]$createdAccount.Body.id
    $baseTime = New-CurrentDemoCsv `
        (Join-Path $repositoryRoot 'sample-data\demo-import.csv') `
        $temporaryCsv
    $manualTransaction = Invoke-JsonRequest `
        'POST' "$baseUri/api/transactions" @{
            accountId = $accountId
            externalTransactionNo = 'DAY7-CSV-MATCH-001'
            direction = 'INCOME'
            amount = 88.00
            transactionTime = $baseTime.AddMinutes(1).ToString(
                'yyyy-MM-ddTHH:mm:ss'
            )
            description = 'day7 manual match'
        } $adminToken
    Assert-Equal $manualTransaction.StatusCode 201 'Manual transaction creation'

    $uploadConfig = Join-Path $temporaryRoot 'upload-curl.cfg'
    $uploadArguments = New-FinguardCurlUploadConfig `
        -Path $uploadConfig `
        -Token $adminToken `
        -CsvPath $temporaryCsv `
        -OutputPath $outputFile `
        -Uri "$baseUri/api/import-jobs"
    $uploadStatus = & curl.exe @uploadArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'CSV upload transport failed'
    }
    Assert-Equal ([int]$uploadStatus) 202 'CSV upload status'
    $uploadResponse = Get-Content -Raw -LiteralPath $outputFile |
        ConvertFrom-Json
    $importJobId = [long]$uploadResponse.id
    $importJob = Wait-ForJob `
        "$baseUri/api/import-jobs/$importJobId" `
        $adminToken `
        @('SUCCESS', 'PARTIAL_SUCCESS', 'FAILED') `
        120
    Assert-Equal $importJob.Body.status 'SUCCESS' 'Import terminal state'
    Assert-Equal $importJob.Body.successRows 2 'Imported row count'

    $duplicateOutput = Join-Path $temporaryRoot 'duplicate-upload.json'
    $duplicateConfig = Join-Path $temporaryRoot 'duplicate-upload-curl.cfg'
    $duplicateArguments = New-FinguardCurlUploadConfig `
        -Path $duplicateConfig `
        -Token $adminToken `
        -CsvPath $temporaryCsv `
        -OutputPath $duplicateOutput `
        -Uri "$baseUri/api/import-jobs"
    $duplicateStatus = & curl.exe @duplicateArguments
    Assert-Equal ([int]$duplicateStatus) 200 'Duplicate upload status'
    $duplicateUpload = Get-Content -Raw -LiteralPath $duplicateOutput |
        ConvertFrom-Json
    Assert-Equal $duplicateUpload.duplicateFile $true 'Duplicate file flag'
    Assert-Equal ([long]$duplicateUpload.id) $importJobId 'Duplicate import job'

    $reconciliationAccepted = Invoke-JsonRequest `
        'POST' "$baseUri/api/reconciliation-jobs" @{
            importJobId = $importJobId
        } $adminToken
    Assert-Equal $reconciliationAccepted.StatusCode 202 'Reconciliation acceptance'
    $reconciliationJobId = [long]$reconciliationAccepted.Body.id
    $reconciliationJob = Wait-ForJob `
        "$baseUri/api/reconciliation-jobs/$reconciliationJobId" `
        $adminToken `
        @('COMPLETED', 'FAILED') `
        120
    Assert-Equal `
        $reconciliationJob.Body.status `
        'COMPLETED' `
        'Reconciliation terminal state'
    if ([int]$reconciliationJob.Body.matchedCount -lt 1) {
        throw 'Reconciliation did not produce the expected match'
    }
    $duplicateReconciliation = Invoke-JsonRequest `
        'POST' "$baseUri/api/reconciliation-jobs" @{
            importJobId = $importJobId
        } $adminToken
    Assert-Equal `
        $duplicateReconciliation.StatusCode `
        200 `
        'Duplicate reconciliation status'
    Assert-Equal `
        $duplicateReconciliation.Body.duplicateRequest `
        $true `
        'Duplicate reconciliation flag'

    $reviewQuery = Invoke-JsonRequest `
        'GET' `
        "$baseUri/api/review-tasks?page=1&size=20&status=PENDING&sourceType=RISK_HIT&ruleCode=LARGE_AMOUNT" `
        $null `
        $reviewerToken
    $reviewRecord = @($reviewQuery.Body.records | Where-Object {
        $_.ruleCode -eq 'LARGE_AMOUNT'
    } | Select-Object -First 1)
    if ($reviewRecord.Count -ne 1) {
        throw 'Expected one pending LARGE_AMOUNT review task'
    }
    $reviewTaskId = [long]$reviewRecord[0].id
    $reviewVersion = [int]$reviewRecord[0].version
    $reviewDecision = Invoke-JsonRequest `
        'PATCH' "$baseUri/api/review-tasks/$reviewTaskId/decision" @{
            decision = 'CONFIRMED'
            version = $reviewVersion
            note = 'week6 day7 verified'
        } $reviewerToken
    Assert-Equal $reviewDecision.Body.status 'CONFIRMED' 'Review terminal state'

    $auditCount = Get-MySqlScalar (
        'SELECT COUNT(*) FROM audit_logs ' +
        "WHERE action_code='REVIEW_CONFIRMED' " +
        "AND review_task_id=$reviewTaskId;"
    )
    Assert-Equal $auditCount '1' 'Review audit count'
    $statistics = Invoke-JsonRequest `
        'GET' "$baseUri/api/statistics/overview" $null $adminToken
    Assert-Equal $statistics.StatusCode 200 'Statistics query'
    $redisKey = Invoke-ComposeText @(
        'exec', '-T',
        '-e', "REDISCLI_AUTH=$redisPassword",
        'redis',
        'redis-cli',
        'EXISTS', 'finguard:statistics:overview:v1'
    )
    Assert-Equal ($redisKey | Select-Object -Last 1).Trim() '1' 'Redis statistics key'

    $metrics = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$baseUri/actuator/prometheus"
    foreach ($metricName in @(
            'finguard_import_jobs_completed_total',
            'finguard_reconciliation_processing_seconds_count')) {
        if ($metrics.Content -notmatch [regex]::Escape($metricName)) {
            throw "Expected metric is absent: $metricName"
        }
    }
    $queueOutput = Invoke-ComposeText @(
        'exec', '-T', 'rabbitmq',
        'rabbitmqctl', 'list_queues', '-q',
        'name', 'messages_unacknowledged'
    )
    $busyQueues = @($queueOutput | Where-Object {
        $_ -match '\s+[1-9][0-9]*$'
    })
    Assert-Equal $busyQueues.Count 0 'RabbitMQ unacknowledged queues'
    $businessFlowPassed = $true

    Invoke-Compose @('restart', 'app')
    Wait-Until {
        (Get-ContainerHealth 'app') -eq 'healthy'
    } 180 'application restart health'
    $restartLogin = Invoke-JsonRequest 'POST' "$baseUri/api/auth/login" @{
        username = $adminUsername
        password = $adminPassword
    } ''
    $restartToken = $restartLogin.Body.accessToken
    Assert-Equal `
        (Get-HttpStatus 'GET' "$baseUri/api/accounts/$accountId" $null $restartToken) `
        200 `
        'Account after restart'
    Assert-Equal `
        (Get-HttpStatus 'GET' "$baseUri/api/import-jobs/$importJobId" $null $restartToken) `
        200 `
        'Import after restart'
    Wait-Until {
        $targets = Invoke-JsonRequest `
            'GET' `
            ('http://127.0.0.1:{0}/api/v1/targets' -f $ports.Prometheus) `
            $null ''
        @($targets.Body.data.activeTargets | Where-Object {
            $_.health -eq 'up'
        }).Count -ge 1
    } 60 'Prometheus target after restart'
    $restartPassed = $true

    $result = [pscustomobject]@{
        ProjectName = $projectName
        ServicesHealthy = $servicesHealthy
        FlywayVersion = [int]$flywayVersion
        OpenApiPathCount = $openApiPathCount
        PrometheusTargetUp = $prometheusTargetUp
        BusinessFlowPassed = $businessFlowPassed
        RestartPassed = $restartPassed
        CleanupPassed = $false
        AccountId = $accountId
        ImportJobId = $importJobId
        ReconciliationJobId = $reconciliationJobId
        ReviewTaskId = $reviewTaskId
    }
} finally {
    $cleanupAttempted = $true
    if (Test-Path -LiteralPath $environmentFile -PathType Leaf) {
        try {
            Invoke-Compose @('down', '--volumes', '--remove-orphans')
        } catch {
            Write-Warning 'Day 7 Compose cleanup returned an error'
        }
    }
    $day7ImageIds = @(
        & docker image ls `
            --quiet `
            --filter 'reference=finguard-core:day7'
    )
    if ($day7ImageIds.Count -gt 0) {
        & docker image rm 'finguard-core:day7' | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'Day 7 image cleanup returned an error'
        }
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force `
            -ErrorAction SilentlyContinue
    }
    $remainingContainers = @(
        & docker ps -a `
            --filter "label=com.docker.compose.project=$projectName" `
            --format '{{.Names}}'
    )
    $remainingVolumes = @(
        & docker volume ls `
            --filter "label=com.docker.compose.project=$projectName" `
            --format '{{.Name}}'
    )
    $remainingNetworks = @(
        & docker network ls `
            --filter "label=com.docker.compose.project=$projectName" `
            --format '{{.Name}}'
    )
    $unexpectedRemoved = @($preexistingContainers | Where-Object {
        $_ -notin @(& docker ps -a --format '{{.Names}}')
    })
    $remainingDay7ImageIds = @(
        & docker image ls `
            --quiet `
            --filter 'reference=finguard-core:day7'
    )
    $day7ImageRemains = $remainingDay7ImageIds.Count -gt 0
    $cleanupPassed = (
        $remainingContainers.Count -eq 0 -and
        $remainingVolumes.Count -eq 0 -and
        $remainingNetworks.Count -eq 0 -and
        $unexpectedRemoved.Count -eq 0 -and
        -not $day7ImageRemains -and
        -not (Test-Path -LiteralPath $temporaryRoot)
    )
    if ($cleanupAttempted -and -not $cleanupPassed) {
        Write-Warning 'Independent Day 7 cleanup verification failed'
    }
    if ($null -ne $result) {
        $result.CleanupPassed = $cleanupPassed
    }
}

if ($null -ne $result) {
    return $result
}
