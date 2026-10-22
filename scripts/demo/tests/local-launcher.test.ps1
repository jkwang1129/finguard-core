$ErrorActionPreference = 'Stop'
$source = Split-Path -Parent $PSScriptRoot
$sandbox = Join-Path ([IO.Path]::GetTempPath()) ('finguard-launcher-' + [guid]::NewGuid().ToString('N'))
$demo = Join-Path $sandbox 'scripts/demo'
New-Item -ItemType Directory -Path $demo -Force | Out-Null
Copy-Item (Join-Path $source 'start-demo.ps1'), (Join-Path $source 'stop-demo.ps1') $demo
Copy-Item (Join-Path $source 'local-demo-common.ps1') $demo
$fixture = Get-Content (Join-Path $source '../../.env.example') -Raw
$fixture.Replace('replace-with-', 'fixture-only-') | Set-Content (Join-Path $sandbox '.env')
# Called scripts have their own script scopes; share only this test double's state.
$global:FinGuardLauncherCalls = @()
$global:FinGuardLauncherPluginUnavailable = $false
$global:FinGuardLauncherStandaloneWait = $true
function global:docker {
    $global:FinGuardLauncherCalls += ($args -join ' ')
    $global:LASTEXITCODE = 0
    Set-Variable -Name LASTEXITCODE -Scope 1 -Value 0
    if ($args[0] -eq 'compose' -and $args[1] -eq 'version') {
        if ($global:FinGuardLauncherPluginUnavailable) {
            $global:LASTEXITCODE = 1
            Set-Variable -Name LASTEXITCODE -Scope 1 -Value 1
        }
        return
    }
    if ($args -contains '--help') { return '--wait  Wait for services to be healthy' }
    if ($args -contains 'down') {
        $global:LASTEXITCODE = 17
        Set-Variable -Name LASTEXITCODE -Scope 1 -Value 17
    }
}
function global:docker-compose {
    $global:FinGuardLauncherCalls += ('standalone ' + ($args -join ' '))
    $global:LASTEXITCODE = 0
    Set-Variable -Name LASTEXITCODE -Scope 1 -Value 0
    if (($args -contains '--help') -and $global:FinGuardLauncherStandaloneWait) { return '--wait  Wait for services to be healthy' }
}
$originalDirectory = Get-Location
try {
    # A plan must resolve paths without starting dependencies or setting demo credentials.
    $before = $env:FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD
    $plan = & (Join-Path $demo 'start-demo.ps1') -DepsOnly -DryRun
    if ($plan.RepoRoot -ne $sandbox -or $plan.StartApplication -ne $false -or $plan.StartDependencies -ne $true) {
        throw 'DryRun did not return the dependency-only plan'
    }
    if ($global:FinGuardLauncherCalls | Where-Object { $_ -match '\bup -d\b|\b(down|stop)\b' }) { throw 'DryRun changed containers' }
    if ($env:FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD -ne $before) { throw 'Launcher changed account credentials' }
    if ($plan.BootstrapEnabled) { throw 'Disabled bootstrap was enabled implicitly' }

    # Java emits its version on stderr; this must also work in Windows PowerShell 5.1.
    $plan = & (Join-Path $demo 'start-demo.ps1') -SkipDependencies -DryRun
    if (-not $plan.StartApplication -or $plan.StartDependencies -or -not $plan.JavaCommand -or -not $plan.MavenCommand) {
        throw 'Application preflight did not resolve the installed Java 17 and Maven'
    }

    $global:FinGuardLauncherPluginUnavailable = $true
    $global:FinGuardLauncherStandaloneWait = $false
    try {
        & (Join-Path $demo 'start-demo.ps1') -DepsOnly -DryRun | Out-Null
        throw 'Unsupported Compose must fail preflight'
    }
    catch { if ($_.Exception.Message -notmatch '--wait') { throw } }
    $global:FinGuardLauncherStandaloneWait = $true
    $plan = & (Join-Path $demo 'start-demo.ps1') -DepsOnly -DryRun
    if ($plan.ComposeCommand.Command -ne 'docker-compose') { throw 'Capable standalone fallback was not selected' }
    $global:FinGuardLauncherPluginUnavailable = $false

    $global:FinGuardLauncherCalls = @()
    $plan = & (Join-Path $demo 'stop-demo.ps1') -StopOnly -DryRun
    if ($plan.Action -ne 'stop' -or -not $plan.PreserveVolumes) { throw 'Incorrect stop-only plan' }
    if ($global:FinGuardLauncherCalls | Where-Object { $_ -match '\bup -d\b|\b(down|stop)\b' }) { throw 'Stop DryRun changed containers' }

    # A failed native stop must propagate its failure, rather than claim success.
    $result = & (Join-Path $demo 'stop-demo.ps1') *>&1
    if ($LASTEXITCODE -ne 17) { throw 'Stop did not propagate the Compose failure' }
    if (($result | Out-String) -match '已停止|Stopped successfully') { throw 'Failed stop claimed success' }
    if (($global:FinGuardLauncherCalls -join ' ') -match '--volumes|\s-v\b') { throw 'Stop requested volume removal' }
    Write-Output 'PASS: local launcher dry-run, credentials, volume preservation, and exit handling'
}
finally {
    Set-Location $originalDirectory
    Remove-Item Function:\docker
    Remove-Item Function:\docker-compose
    Remove-Variable FinGuardLauncherCalls,FinGuardLauncherPluginUnavailable,FinGuardLauncherStandaloneWait -Scope Global
    # No production containers or user files are touched; the temporary fixture is retained.
}
# CI shells propagate LASTEXITCODE; the deliberately failed native call was asserted above.
exit 0
