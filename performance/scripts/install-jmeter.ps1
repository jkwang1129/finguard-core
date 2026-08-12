[CmdletBinding()]
param(
    [string]$ToolsDirectory = (
        Join-Path $PSScriptRoot '..\.tools'
    ),
    [string]$Version = '5.6.3'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$toolsRoot = [System.IO.Path]::GetFullPath($ToolsDirectory)
$distributionName = "apache-jmeter-$Version"
$installDirectory = Join-Path $toolsRoot $distributionName
$jmeterCommand = Join-Path $installDirectory 'bin\jmeter.bat'

if (Test-Path -LiteralPath $jmeterCommand -PathType Leaf) {
    & $jmeterCommand --version | Select-Object -First 3
    Write-Output "JMETER_COMMAND=$jmeterCommand"
    exit 0
}

New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null

$baseUrl = 'https://dlcdn.apache.org/jmeter/binaries'
$archiveName = "$distributionName.zip"
$archiveUrl = "$baseUrl/$archiveName"
$hashUrl = "$archiveUrl.sha512"
$archivePath = Join-Path $toolsRoot $archiveName
$partialDirectory = Join-Path $toolsRoot "$distributionName.partial-$PID"

try {
    Invoke-WebRequest -UseBasicParsing -Uri $archiveUrl -OutFile $archivePath
    $hashText = (Invoke-WebRequest -UseBasicParsing -Uri $hashUrl).Content
    $expectedHash = ($hashText -split '\s+')[0].Trim().ToUpperInvariant()
    $actualHash = (Get-FileHash -Algorithm SHA512 -LiteralPath $archivePath).Hash
    if ($actualHash -ne $expectedHash) {
        throw "Apache JMeter SHA-512 verification failed"
    }

    if (Test-Path -LiteralPath $partialDirectory) {
        $resolvedPartial = [System.IO.Path]::GetFullPath($partialDirectory)
        if (-not $resolvedPartial.StartsWith(
            $toolsRoot + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase
        )) {
            throw "Refusing to remove a partial directory outside the tools root"
        }
        Remove-Item -Recurse -Force -LiteralPath $resolvedPartial
    }

    New-Item -ItemType Directory -Path $partialDirectory | Out-Null
    Expand-Archive -LiteralPath $archivePath -DestinationPath $partialDirectory
    $expandedDirectory = Join-Path $partialDirectory $distributionName
    if (-not (Test-Path -LiteralPath $expandedDirectory -PathType Container)) {
        throw "Apache JMeter archive did not contain $distributionName"
    }
    Move-Item -LiteralPath $expandedDirectory -Destination $installDirectory
} finally {
    if (Test-Path -LiteralPath $archivePath) {
        Remove-Item -Force -LiteralPath $archivePath
    }
    if (Test-Path -LiteralPath $partialDirectory) {
        $resolvedPartial = [System.IO.Path]::GetFullPath($partialDirectory)
        if ($resolvedPartial.StartsWith(
            $toolsRoot + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase
        )) {
            Remove-Item -Recurse -Force -LiteralPath $resolvedPartial
        }
    }
}

& $jmeterCommand --version | Select-Object -First 3
Write-Output "JMETER_COMMAND=$jmeterCommand"
