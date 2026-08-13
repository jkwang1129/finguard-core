$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$staticRunner = Join-Path $repositoryRoot (
    'security\scripts\run-static-security-checks.ps1'
)
$zapRunner = Join-Path $repositoryRoot (
    'security\scripts\run-zap-baseline.ps1'
)
$redisDrill = Join-Path $repositoryRoot (
    'scripts\drills\invoke-redis-outage-drill.ps1'
)
$messagingDrill = Join-Path $repositoryRoot (
    'scripts\drills\invoke-messaging-retry-drill.ps1'
)
$misconfigurationDrill = Join-Path $repositoryRoot (
    'scripts\drills\invoke-misconfiguration-drill.ps1'
)

$staticPlan = & $staticRunner `
    -RepositoryRoot $repositoryRoot `
    -ImageReference 'finguard-core:day6-test' `
    -OutputDirectory 'security/results/static-test' `
    -DryRun

if ($staticPlan.ToolImage -ne 'aquasec/trivy:0.72.0') {
    throw "Unexpected Trivy image: $($staticPlan.ToolImage)"
}
if ($staticPlan.Commands.Count -ne 2) {
    throw 'Static runner must plan one filesystem and one image scan'
}
$imageScanText = $staticPlan.Commands[1]
if ($imageScanText -notmatch '--skip-files \*\*/\*\.jar') {
    throw 'Image scan must avoid downloading a second Java advisory database'
}
$staticText = $staticPlan.Commands -join "`n"
if ($staticText -notmatch '--scanners vuln,misconfig,secret') {
    throw 'Static scan must cover vulnerabilities, misconfiguration and secrets'
}
if ($staticText -notmatch '--timeout 30m') {
    throw 'Static scans must tolerate a slow first-time vulnerability DB download'
}
if ($staticText -notmatch '--offline-scan' -or
        $staticText -notmatch '--skip-version-check') {
    throw 'Static scans must not depend on remote package metadata resolution'
}
foreach ($skipDirectory in @(
        '/workspace/.git',
        '/workspace/target',
        '/workspace/performance/.tools',
        '/workspace/performance/results',
        '/workspace/security/.cache',
        '/workspace/security/results')) {
    if ($staticText -notmatch [regex]::Escape(
            "--skip-dirs $skipDirectory")) {
        throw "Static scan must skip generated directory: $skipDirectory"
    }
}
if ($staticText -match 'JWT_SECRET_BASE64|MYSQL_PASSWORD|REDIS_PASSWORD') {
    throw 'Static scan plan must not contain secret environment names or values'
}

$zapPlan = & $zapRunner `
    -TargetUrl 'http://host.docker.internal:18080' `
    -OutputDirectory 'security/results/zap-test' `
    -DryRun

if ($zapPlan.ToolImage -ne 'ghcr.io/zaproxy/zaproxy:2.17.0') {
    throw "Unexpected ZAP image: $($zapPlan.ToolImage)"
}
if ($zapPlan.Command -notmatch 'zap-baseline.py') {
    throw 'ZAP runner must use the passive baseline script'
}
if ($zapPlan.Command -match 'zap-full-scan|zap-api-scan') {
    throw 'ZAP runner must not use active packaged scans'
}
$zapPolicy = Get-Content -LiteralPath (
    Join-Path $repositoryRoot 'security\zap-baseline.conf'
)
foreach ($line in $zapPolicy) {
    if (-not [string]::IsNullOrWhiteSpace($line) -and
            -not $line.TrimStart().StartsWith('#') -and
            $line.Split("`t").Count -lt 3) {
        throw 'ZAP policy rules must have at least three tab-separated fields'
    }
}

$gitIgnore = Get-Content -LiteralPath (
    Join-Path $repositoryRoot '.gitignore'
) -Raw -Encoding utf8
if ($gitIgnore -notmatch '(?m)^security/results/$') {
    throw 'Raw security results must be ignored by Git'
}

$developmentCompose = Get-Content -LiteralPath (
    Join-Path $repositoryRoot 'docker-compose.yml'
) -Raw -Encoding utf8
if ($developmentCompose -notmatch
        '127\.0\.0\.1:\$\{MYSQL_PORT:-3306\}:3306') {
    throw 'Development MySQL must bind to loopback only'
}

$redisPlan = & $redisDrill `
    -EnvironmentFile 'C:\temporary\day6.env' `
    -DryRun
$redisText = $redisPlan.Commands -join "`n"
if ($redisPlan.ProjectName -ne 'finguard-day6') {
    throw 'Redis drill must be locked to the finguard-day6 project'
}
if ($redisText -notmatch 'stop redis' -or
        $redisText -notmatch 'start redis') {
    throw 'Redis drill must stop and recover only Redis'
}
if ($redisText -match 'down|--volumes|-v') {
    throw 'Redis drill must never delete Compose resources or volumes'
}

$messagingPlan = & $messagingDrill `
    -EnvironmentFile 'C:\temporary\day6.env' `
    -DryRun
if ($messagingPlan.Selector -notmatch
        'shouldDelayOneTransientFailureThenRecover') {
    throw 'Messaging drill must verify transient recovery'
}
if ($messagingPlan.Selector -notmatch
        'shouldExhaustTwoRetriesThenFailAndDeadLetter') {
    throw 'Messaging drill must verify retry exhaustion and DLQ'
}
if (($messagingPlan.Command -join ' ') -match
        'PASSWORD|SECRET|TOKEN') {
    throw 'Messaging drill command must not expose credentials'
}
$messagingText = $messagingPlan.Commands -join "`n"
if ($messagingPlan.ProjectName -ne 'finguard-day6' -or
        $messagingText -notmatch 'stop app' -or
        $messagingText -notmatch 'start app') {
    throw 'Messaging drill must isolate and restore the competing app consumer'
}
if ($messagingText -match 'down|--volumes|-v') {
    throw 'Messaging drill must never delete Compose resources or volumes'
}

$misconfigurationPlan = & $misconfigurationDrill `
    -EnvironmentFile 'C:\temporary\day6.env' `
    -DryRun
$misconfigurationText = $misconfigurationPlan.Commands -join "`n"
if ($misconfigurationPlan.ProjectName -ne 'finguard-day6') {
    throw 'Misconfiguration drill must be locked to finguard-day6'
}
if ($misconfigurationText -notmatch '--no-deps' -or
        $misconfigurationText -notmatch 'MYSQL_PORT=1') {
    throw 'Misconfiguration drill must use a one-off bad-port app'
}
if ($misconfigurationText -match
        '\.env\.linux|down|--volumes|\s-v\s') {
    throw 'Misconfiguration drill must not alter retained configuration or volumes'
}
if ($misconfigurationPlan.Commands[0] -notmatch
        'docker container inspect') {
    throw 'Misconfiguration drill must check before removing a stale container'
}

Write-Output 'SECURITY_TOOL_TESTS=PASS'
