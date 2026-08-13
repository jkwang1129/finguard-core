param(
    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [string]$ImageReference,

    [string]$OutputDirectory = 'security/results/static',

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$toolImage = 'aquasec/trivy:0.72.0'
$resolvedRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
if (-not (Test-Path -LiteralPath $resolvedRoot -PathType Container)) {
    throw "Repository root does not exist: $resolvedRoot"
}
if ([string]::IsNullOrWhiteSpace($ImageReference)) {
    throw 'ImageReference must not be empty'
}

$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath(
        (Join-Path $resolvedRoot $OutputDirectory)
    )
}
$cacheDirectory = Join-Path $resolvedRoot 'security\.cache\trivy'
$repositoryReport = Join-Path $resolvedOutput 'repository.json'
$imageReport = Join-Path $resolvedOutput 'image.json'
$imageArchive = Join-Path $resolvedOutput 'application-image.tar'

$filesystemCommand = @(
    'docker', 'run', '--rm',
    '--mount', "type=bind,source=$resolvedRoot,target=/workspace,readonly",
    '--mount', "type=bind,source=$resolvedOutput,target=/results",
    '--mount', "type=bind,source=$cacheDirectory,target=/cache",
    $toolImage,
    'fs', '--cache-dir', '/cache',
    '--timeout', '30m',
    '--offline-scan', '--skip-version-check',
    '--skip-dirs', '/workspace/.git',
    '--skip-dirs', '/workspace/target',
    '--skip-dirs', '/workspace/performance/.tools',
    '--skip-dirs', '/workspace/performance/results',
    '--skip-dirs', '/workspace/security/.cache',
    '--skip-dirs', '/workspace/security/results',
    '--scanners', 'vuln,misconfig,secret',
    '--format', 'json', '--output', '/results/repository.json',
    '--exit-code', '0', '/workspace'
)
$imageCommand = @(
    'docker', 'run', '--rm',
    '--mount', "type=bind,source=$resolvedOutput,target=/results",
    '--mount', "type=bind,source=$cacheDirectory,target=/cache",
    $toolImage,
    'image', '--cache-dir', '/cache',
    '--timeout', '30m',
    '--offline-scan', '--skip-version-check',
    '--skip-files', '**/*.jar',
    '--scanners', 'vuln,misconfig,secret',
    '--format', 'json', '--output', '/results/image.json',
    '--exit-code', '0', '--input', '/results/application-image.tar'
)

if ($DryRun) {
    return [pscustomobject]@{
        ToolImage = $toolImage
        Commands = @(
            ($filesystemCommand -join ' '),
            ($imageCommand -join ' ')
        )
        OutputDirectory = $resolvedOutput
    }
}

New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
New-Item -ItemType Directory -Force -Path $cacheDirectory | Out-Null

try {
    & $filesystemCommand[0] $filesystemCommand[1..($filesystemCommand.Count - 1)]
    if ($LASTEXITCODE -ne 0) {
        throw "Trivy repository scan failed with exit code $LASTEXITCODE"
    }

    & docker image inspect $ImageReference *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Application image was not found locally: $ImageReference"
    }
    & docker save --output $imageArchive $ImageReference
    if ($LASTEXITCODE -ne 0) {
        throw "docker save failed with exit code $LASTEXITCODE"
    }

    & $imageCommand[0] $imageCommand[1..($imageCommand.Count - 1)]
    if ($LASTEXITCODE -ne 0) {
        throw "Trivy image scan failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item -LiteralPath $imageArchive -Force -ErrorAction SilentlyContinue
}

foreach ($report in @($repositoryReport, $imageReport)) {
    if (-not (Test-Path -LiteralPath $report -PathType Leaf)) {
        throw "Expected report was not created: $report"
    }
}

Write-Output ([pscustomobject]@{
    ToolImage = $toolImage
    RepositoryReport = $repositoryReport
    ImageReport = $imageReport
})
