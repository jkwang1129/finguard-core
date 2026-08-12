[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$JtlPath,

    [double]$MaximumErrorRatePercent = 1.0,
    [string]$MarkdownPath = ''
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $JtlPath -PathType Leaf)) {
    throw "JTL file not found: $JtlPath"
}

$samples = @(Import-Csv -LiteralPath $JtlPath)
if ($samples.Count -eq 0) {
    throw 'JTL file contains no samples'
}

$elapsedValues = @(
    $samples |
        ForEach-Object { [long]$_.elapsed } |
        Sort-Object
)

function Get-NearestRankPercentile(
    [long[]]$SortedValues,
    [double]$Percentile
) {
    $rank = [Math]::Ceiling(($Percentile / 100.0) * $SortedValues.Count)
    $index = [Math]::Max(0, [Math]::Min($SortedValues.Count - 1, $rank - 1))
    return $SortedValues[$index]
}

$errorCount = @(
    $samples | Where-Object {
        -not [string]::Equals(
            $_.success,
            'true',
            [System.StringComparison]::OrdinalIgnoreCase
        )
    }
).Count
$errorRate = [Math]::Round(
    ($errorCount * 100.0) / $samples.Count,
    4
)

$startMilliseconds = (
    $samples | Measure-Object -Property timeStamp -Minimum
).Minimum -as [long]
$endMilliseconds = (
    $samples |
        ForEach-Object { [long]$_.timeStamp + [long]$_.elapsed } |
        Measure-Object -Maximum
).Maximum -as [long]
$durationSeconds = [Math]::Max(
    0.001,
    ($endMilliseconds - $startMilliseconds) / 1000.0
)

$summary = [pscustomobject][ordered]@{
    SampleCount = $samples.Count
    ErrorCount = $errorCount
    ErrorRatePercent = $errorRate
    ThroughputPerSecond = [Math]::Round(
        $samples.Count / $durationSeconds,
        3
    )
    AverageMilliseconds = [Math]::Round(
        ($elapsedValues | Measure-Object -Average).Average,
        3
    )
    P50Milliseconds = Get-NearestRankPercentile $elapsedValues 50
    P90Milliseconds = Get-NearestRankPercentile $elapsedValues 90
    P95Milliseconds = Get-NearestRankPercentile $elapsedValues 95
    P99Milliseconds = Get-NearestRankPercentile $elapsedValues 99
    MaximumMilliseconds = ($elapsedValues | Measure-Object -Maximum).Maximum
    DurationSeconds = [Math]::Round($durationSeconds, 3)
}

if (-not [string]::IsNullOrWhiteSpace($MarkdownPath)) {
    $markdown = @(
        '| Metric | Value |',
        '|---|---:|',
        "| Samples | $($summary.SampleCount) |",
        "| Errors | $($summary.ErrorCount) |",
        "| Error rate | $($summary.ErrorRatePercent)% |",
        "| Throughput | $($summary.ThroughputPerSecond) req/s |",
        "| Average | $($summary.AverageMilliseconds) ms |",
        "| P50 | $($summary.P50Milliseconds) ms |",
        "| P90 | $($summary.P90Milliseconds) ms |",
        "| P95 | $($summary.P95Milliseconds) ms |",
        "| P99 | $($summary.P99Milliseconds) ms |",
        "| Maximum | $($summary.MaximumMilliseconds) ms |",
        "| Measured duration | $($summary.DurationSeconds) s |"
    )
    [System.IO.File]::WriteAllLines(
        [System.IO.Path]::GetFullPath($MarkdownPath),
        $markdown,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

if ($summary.ErrorRatePercent -gt $MaximumErrorRatePercent) {
    throw "JMeter error rate $($summary.ErrorRatePercent)% exceeds $MaximumErrorRatePercent%"
}

return $summary
