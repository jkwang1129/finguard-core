[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Token,

    [ValidateRange(1, 200)]
    [int]$Threads = 1,

    [ValidateRange(1, 600)]
    [int]$RampSeconds = 1,

    [ValidateRange(1, 3600)]
    [int]$DurationSeconds = 10,

    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$RunName = 'smoke',

    [string]$JMeterCommand = (
        Join-Path $PSScriptRoot (
            '..\.tools\apache-jmeter-5.6.3\bin\jmeter.bat'
        )
    ),

    [string]$ResultRoot = (
        Join-Path $PSScriptRoot '..\results'
    )
)

$ErrorActionPreference = 'Stop'

$uri = $null
if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$uri)) {
    throw 'BaseUrl must be an absolute HTTP or HTTPS URL'
}
if ($uri.Scheme -notin @('http', 'https')) {
    throw 'BaseUrl must use HTTP or HTTPS'
}
if ($uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment) {
    throw 'BaseUrl must not include a path, query, or fragment'
}
if (-not (Test-Path -LiteralPath $JMeterCommand -PathType Leaf)) {
    throw "JMeter command not found: $JMeterCommand"
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultDirectory = [System.IO.Path]::GetFullPath(
    (Join-Path $ResultRoot "$timestamp-$RunName")
)
if (Test-Path -LiteralPath $resultDirectory) {
    throw "Result directory already exists: $resultDirectory"
}
New-Item -ItemType Directory -Path $resultDirectory | Out-Null

$jmxPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\jmeter\audit-log-query.jmx')
)
$jtlPath = Join-Path $resultDirectory 'results.jtl'
$jmeterLogPath = Join-Path $resultDirectory 'jmeter.log'
$htmlPath = Join-Path $resultDirectory 'html'
$summaryPath = Join-Path $resultDirectory 'summary.md'

$previousToken = $env:FINGUARD_PERF_JWT
try {
    $env:FINGUARD_PERF_JWT = $Token
    $arguments = @(
        '-n',
        '-t', $jmxPath,
        '-l', $jtlPath,
        '-j', $jmeterLogPath,
        '-e',
        '-o', $htmlPath,
        "-Jprotocol=$($uri.Scheme)",
        "-Jhost=$($uri.Host)",
        "-Jport=$($uri.Port)",
        "-Jthreads=$Threads",
        "-JrampSeconds=$RampSeconds",
        "-JdurationSeconds=$DurationSeconds"
    )
    & $JMeterCommand @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "JMeter failed with exit code $LASTEXITCODE"
    }
    if (-not (Test-Path -LiteralPath $jtlPath -PathType Leaf)) {
        throw 'JMeter did not create a JTL result file; inspect jmeter.log'
    }
} finally {
    if ($null -eq $previousToken) {
        Remove-Item Env:FINGUARD_PERF_JWT -ErrorAction SilentlyContinue
    } else {
        $env:FINGUARD_PERF_JWT = $previousToken
    }
}

$summary = & (Join-Path $PSScriptRoot 'summarize-jtl.ps1') `
    -JtlPath $jtlPath `
    -MarkdownPath $summaryPath
$summary | Add-Member -NotePropertyName ResultDirectory `
    -NotePropertyValue $resultDirectory
return $summary
