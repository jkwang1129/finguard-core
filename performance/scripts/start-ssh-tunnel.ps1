[CmdletBinding()]
param(
    [string]$Target = $env:FINGUARD_PERF_SSH_TARGET
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$target = $Target
if ([string]::IsNullOrWhiteSpace($target)) {
    throw "FINGUARD_PERF_SSH_TARGET must be configured for the interactive SSH tunnel."
}

$transcriptPath = Join-Path $PSScriptRoot "..\results\ssh-tunnel-transcript.txt"
$transcriptDirectory = Split-Path -Parent $transcriptPath
New-Item -ItemType Directory -Force -Path $transcriptDirectory | Out-Null

Start-Transcript -Path $transcriptPath -Force | Out-Null
try {
    $sshArguments = @(
        "-N",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-L", "18086:127.0.0.1:8080",
        "-L", "19096:127.0.0.1:9090",
        "-L", "13006:127.0.0.1:3000",
        "-L", "13317:127.0.0.1:3306",
        $target
    )

    & ssh.exe @sshArguments
    if ($LASTEXITCODE -ne 0) {
        throw "SSH tunnel exited with code $LASTEXITCODE."
    }
}
finally {
    Stop-Transcript | Out-Null
}
