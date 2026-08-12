[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Seed', 'Explain', 'Cleanup')]
    [string]$Mode,

    [string]$ComposeFile = 'docker-compose.yml',
    [string]$ProjectName = '',
    [string]$MySqlService = 'mysql'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..')
)
$sqlFileByMode = @{
    Seed = 'performance\sql\seed-audit-log-performance-data.sql'
    Explain = 'performance\sql\explain-audit-log-query.sql'
    Cleanup = 'performance\sql\cleanup-audit-log-performance-data.sql'
}
$sqlPath = Join-Path $repositoryRoot $sqlFileByMode[$Mode]
if (-not (Test-Path -LiteralPath $sqlPath -PathType Leaf)) {
    throw "SQL file not found for mode $Mode"
}

$composeArguments = @('--file', $ComposeFile)
if (-not [string]::IsNullOrWhiteSpace($ProjectName)) {
    $composeArguments += @('--project-name', $ProjectName)
}
$composeArguments += @(
    'exec', '-T', $MySqlService,
    'sh', '-c',
    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --batch --raw -uroot "$MYSQL_DATABASE"'
)

$sql = Get-Content -Raw -Encoding utf8 -LiteralPath $sqlPath
$sql | & docker compose @composeArguments
if ($LASTEXITCODE -ne 0) {
    throw "MySQL $Mode script failed with exit code $LASTEXITCODE"
}
