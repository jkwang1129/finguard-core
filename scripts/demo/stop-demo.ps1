<#
.SYNOPSIS
    Stop this repository's local Compose dependencies, preserving data volumes.
.DESCRIPTION
    Stop the foreground Java application with Ctrl+C separately.
    -StopOnly retains containers as well as volumes. -DryRun changes nothing.
#>
[CmdletBinding()]
param([switch]$StopOnly, [switch]$DryRun)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-demo-common.ps1')
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Get-DemoCompose
$action = if ($StopOnly) { 'stop' } else { 'down' }
if ($DryRun) {
    return [pscustomobject]@{ RepoRoot = $repoRoot; Action = $action; PreserveVolumes = $true; ComposeCommand = $compose }
}
Push-Location $repoRoot
try {
    $composeArgs = @($compose.Prefix) + @($action)
    & $compose.Command @composeArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'Stopped successfully. Data volumes were preserved; Java processes were not stopped.'
}
finally { Pop-Location }
