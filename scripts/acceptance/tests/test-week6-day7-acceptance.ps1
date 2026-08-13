$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..\..')
)
$runner = Join-Path $repositoryRoot (
    'scripts\acceptance\invoke-week6-day7-acceptance.ps1'
)
$functions = Join-Path $repositoryRoot (
    'scripts\acceptance\week6-day7-functions.ps1'
)

if (-not (Test-Path -LiteralPath $functions -PathType Leaf)) {
    throw 'Day 7 acceptance functions are missing'
}
. $functions
$healthBytes = [byte[]](123, 34, 115, 116, 97, 116, 117, 115, 34, 58, 34, 85, 80, 34, 125)
$decodedHealth = ConvertFrom-FinguardUtf8Json -Content $healthBytes
if ($decodedHealth.status -ne 'UP') {
    throw 'UTF-8 byte-array JSON responses must decode to objects'
}

$curlConfig = Join-Path $env:TEMP (
    'finguard-day7-curl-test-{0}.cfg' -f [Guid]::NewGuid().ToString('N')
)
$fakeToken = 'test-token-must-not-appear-in-process-arguments'
try {
    $curlArguments = New-FinguardCurlUploadConfig `
        -Path $curlConfig `
        -Token $fakeToken `
        -CsvPath 'C:\tmp\demo.csv' `
        -OutputPath 'C:\tmp\response.json' `
        -Uri 'http://127.0.0.1:18080/api/import-jobs'
    if (($curlArguments -join ' ') -match [regex]::Escape($fakeToken)) {
        throw 'JWT-like values must not appear in curl process arguments'
    }
    if ((Get-Content -LiteralPath $curlConfig -Raw) -notmatch
            [regex]::Escape($fakeToken)) {
        throw 'The temporary curl config must carry the authorization header'
    }
} finally {
    Remove-Item -LiteralPath $curlConfig -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw 'Day 7 acceptance runner is missing'
}
$runnerText = Get-Content -LiteralPath $runner -Raw
if ($runnerText -match "docker image inspect 'finguard-core:day7'") {
    throw 'Missing-image cleanup checks must not emit a native stderr failure'
}

$plan = & $runner -DryRun
if ($plan.ProjectName -ne 'finguard-day7') {
    throw 'Day 7 acceptance must use the fixed isolated project name'
}
if ($plan.ContainerPrefix -ne 'finguard-day7') {
    throw 'Day 7 containers must use the isolated prefix'
}

$expectedServices = @(
    'mysql',
    'rabbitmq',
    'redis',
    'app',
    'prometheus',
    'grafana'
)
if (($plan.Services -join ',') -ne ($expectedServices -join ',')) {
    throw 'The plan must cover exactly the six application services'
}

$expectedPorts = [ordered]@{
    App = 18080
    MySql = 13306
    RabbitMq = 15674
    RabbitMqManagement = 15673
    Redis = 16379
    Prometheus = 19090
    Grafana = 13000
}
foreach ($entry in $expectedPorts.GetEnumerator()) {
    if ($plan.Ports.($entry.Key) -ne $entry.Value) {
        throw "Unexpected isolated port for $($entry.Key)"
    }
}

$commands = $plan.Commands -join "`n"
if ($commands -notmatch '--project-name finguard-day7') {
    throw 'Every Compose operation must be scoped to finguard-day7'
}
if ($commands -notmatch 'docker-compose.yml' -or
        $commands -notmatch 'compose.linux.yml') {
    throw 'DryRun must include development and Linux Compose validation'
}
if ($commands -match '(?i)(password|secret|token)=\S+') {
    throw 'DryRun output contains a secret value'
}

$cleanupCommands = $plan.CleanupCommands -join "`n"
if ($cleanupCommands -notmatch '--project-name finguard-day7') {
    throw 'Cleanup must be scoped to the isolated project'
}
if ($cleanupCommands -notmatch 'down --volumes --remove-orphans') {
    throw 'The isolated acceptance project must define complete cleanup'
}
if ($cleanupCommands -notmatch 'docker image rm finguard-core:day7') {
    throw 'Cleanup must remove the Day 7 application image'
}
if ($cleanupCommands -match 'compose\.linux\.yml') {
    throw 'Cleanup must never target the retained Linux deployment'
}

$preflight = & $runner -PreflightOnly
if ($preflight.MavenJavaVersion -notmatch '^17\.') {
    throw 'Day 7 must run Maven with Java 17'
}
if ($preflight.MutatedDockerState) {
    throw 'Preflight-only mode must not mutate Docker state'
}

Write-Output 'WEEK6_DAY7_ACCEPTANCE_TOOL_TESTS=PASS'
