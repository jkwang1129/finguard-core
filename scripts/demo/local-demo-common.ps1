# Shared discovery for the local demo launchers. No processes are started on import.
function Get-DemoCompose([switch]$RequireWait) {
    foreach ($candidate in @(
        @{ Command = 'docker'; Prefix = @('compose') },
        @{ Command = 'docker-compose'; Prefix = @() }
    )) {
        if (-not (Get-Command $candidate.Command -ErrorAction SilentlyContinue)) { continue }
        $previous = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $versionArgs = @($candidate.Prefix) + @('version')
            & $candidate.Command @versionArgs *> $null
            if ($LASTEXITCODE -ne 0) { continue }
            if ($RequireWait) {
                $helpArgs = @($candidate.Prefix) + @('up', '--help')
                $helpText = (& $candidate.Command @helpArgs 2>&1 | ForEach-Object { $_.ToString() }) -join "`n"
                if ($LASTEXITCODE -ne 0 -or $helpText -notmatch '(?m)(^|\s)--wait(\s|=|$)') { continue }
            }
            return [pscustomobject]$candidate
        }
        finally { $ErrorActionPreference = $previous }
    }
    if ($RequireWait) { throw 'Compose with up --wait is required. Upgrade Docker Compose (plugin or standalone command), then retry.' }
    throw 'Docker Compose is unavailable. Install Docker Desktop/Compose and retry.'
}

function Read-DemoEnvironment([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw 'Copy .env.example to .env and configure local credentials first.' }
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $settings[$matches[1]] = $matches[2].Trim()
        }
    }
    return $settings
}

function Get-DemoSetting([hashtable]$Settings, [string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($null -ne $value) { return $value }
    return $Settings[$Name]
}
