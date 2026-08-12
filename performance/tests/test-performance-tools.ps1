$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$summarizer = Join-Path $repositoryRoot (
    'performance\scripts\summarize-jtl.ps1'
)
$successFixture = Join-Path $PSScriptRoot 'fixtures\success-sample.jtl'
$failingFixture = Join-Path $PSScriptRoot 'fixtures\failing-sample.jtl'
$jmxPath = Join-Path $repositoryRoot (
    'performance\jmeter\audit-log-query.jmx'
)
$runner = Join-Path $repositoryRoot (
    'performance\scripts\run-audit-log-test.ps1'
)
$jmeterCommand = Join-Path $repositoryRoot (
    'performance\.tools\apache-jmeter-5.6.3\bin\jmeter.bat'
)

$summary = & $summarizer -JtlPath $successFixture
if ($summary.SampleCount -ne 5) {
    throw "Expected 5 samples, got $($summary.SampleCount)"
}
if ($summary.P50Milliseconds -ne 300) {
    throw "Expected P50 300 ms, got $($summary.P50Milliseconds)"
}
if ($summary.P95Milliseconds -ne 500) {
    throw "Expected P95 500 ms, got $($summary.P95Milliseconds)"
}
if ($summary.P99Milliseconds -ne 500) {
    throw "Expected P99 500 ms, got $($summary.P99Milliseconds)"
}
if ($summary.ErrorRatePercent -ne 0) {
    throw "Expected 0% errors, got $($summary.ErrorRatePercent)"
}

$failureRejected = $false
try {
    & $summarizer -JtlPath $failingFixture | Out-Null
} catch {
    $failureRejected = $true
}
if (-not $failureRejected) {
    throw 'Expected the >1% error fixture to be rejected'
}

[xml]$jmx = Get-Content -Raw -Encoding utf8 -LiteralPath $jmxPath
if ($jmx.SelectNodes('//ResultCollector').Count -ne 0) {
    throw 'JMX must not contain GUI result collectors'
}
if ($jmx.SelectNodes('//HTTPSamplerProxy').Count -ne 1) {
    throw 'JMX must contain exactly one repeated HTTP sampler'
}
$headerValues = $jmx.SelectNodes(
    '//HeaderManager//elementProp/stringProp[@name="Header.value"]'
) | ForEach-Object { $_.'#text' }
if ($headerValues -notcontains 'Bearer ${authToken}') {
    throw 'JMX must reuse the environment-backed JWT variable'
}

$validationRoot = Join-Path $repositoryRoot (
    "performance\results\jmx-validation-$PID"
)
$validationJtl = Join-Path $validationRoot 'validation.jtl'
$validationLog = Join-Path $validationRoot 'validation.log'
New-Item -ItemType Directory -Force -Path $validationRoot | Out-Null
$previousToken = $env:FINGUARD_PERF_JWT
try {
    $env:FINGUARD_PERF_JWT = 'invalid-validation-token'
    $validationArguments = @(
        '-n', '-t', $jmxPath,
        '-l', $validationJtl,
        '-j', $validationLog,
        '-Jprotocol=http',
        '-Jhost=127.0.0.1',
        '-Jport=1',
        '-Jthreads=1',
        '-JrampSeconds=1',
        '-JdurationSeconds=1'
    )
    & $jmeterCommand @validationArguments | Out-Null
} finally {
    if ($null -eq $previousToken) {
        Remove-Item Env:FINGUARD_PERF_JWT -ErrorAction SilentlyContinue
    } else {
        $env:FINGUARD_PERF_JWT = $previousToken
    }
}
if (-not (Test-Path -LiteralPath $validationJtl -PathType Leaf)) {
    throw 'JMeter could not load and execute the committed JMX'
}
Remove-Item -Recurse -Force -LiteralPath $validationRoot

$missingTokenRejected = $false
try {
    & $runner -BaseUrl 'http://127.0.0.1:1' -Token '' | Out-Null
} catch {
    $missingTokenRejected = $true
}
if (-not $missingTokenRejected) {
    throw 'Runner must reject an empty JWT before invoking JMeter'
}

Write-Output 'PERFORMANCE_TOOL_TESTS=PASS'
