param(
    [Parameter(Mandatory = $true)]
    [string]$TargetUrl,

    [string]$OutputDirectory = 'security/results/zap',

    [ValidateRange(1, 5)]
    [int]$SpiderMinutes = 1,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$toolImage = 'ghcr.io/zaproxy/zaproxy:2.17.0'
$targetUri = $null
if (-not [System.Uri]::TryCreate(
        $TargetUrl,
        [System.UriKind]::Absolute,
        [ref]$targetUri
    )) {
    throw 'TargetUrl must be an absolute HTTP or HTTPS URL'
}
if ($targetUri.Scheme -notin @('http', 'https')) {
    throw 'TargetUrl must use HTTP or HTTPS'
}

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot $OutputDirectory)
    )
}
$policyPath = Join-Path $repositoryRoot 'security\zap-baseline.conf'
$command = @(
    'docker', 'run', '--rm',
    '--mount', "type=bind,source=$resolvedOutput,target=/zap/wrk",
    '--mount', "type=bind,source=$policyPath,target=/zap/policy.conf,readonly",
    $toolImage,
    'zap-baseline.py', '-t', $TargetUrl,
    '-m', $SpiderMinutes, '-T', 5,
    '-c', '/zap/policy.conf',
    '-J', 'zap-report.json',
    '-w', 'zap-report.md',
    '-r', 'zap-report.html'
)

if ($DryRun) {
    return [pscustomobject]@{
        ToolImage = $toolImage
        Command = $command -join ' '
        OutputDirectory = $resolvedOutput
    }
}

New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
& $command[0] $command[1..($command.Count - 1)]
$zapExitCode = $LASTEXITCODE
if ($zapExitCode -notin @(0, 2)) {
    throw "ZAP Baseline failed with exit code $zapExitCode"
}
$jsonReport = Join-Path $resolvedOutput 'zap-report.json'
if (-not (Test-Path -LiteralPath $jsonReport -PathType Leaf)) {
    throw 'ZAP Baseline did not create zap-report.json'
}

Write-Output ([pscustomobject]@{
    ToolImage = $toolImage
    ExitCode = $zapExitCode
    JsonReport = $jsonReport
})
