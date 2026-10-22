<#
.SYNOPSIS
    Start local MySQL/RabbitMQ/Redis dependencies and the Java 17 application.
.DESCRIPTION
    Reads .env in the repository root. Configure demo accounts explicitly there,
    or in process environment variables. No credentials are generated or printed.
    Use -DryRun to validate configuration without starting containers or the app.
#>
[CmdletBinding()]
param([switch]$SkipDependencies, [switch]$DepsOnly, [switch]$DryRun)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-demo-common.ps1')
if ($SkipDependencies -and $DepsOnly) { throw 'Choose either -SkipDependencies or -DepsOnly.' }
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$settings = Read-DemoEnvironment (Join-Path $repoRoot '.env')
foreach ($key in @('MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'RABBITMQ_PASSWORD', 'REDIS_PASSWORD', 'JWT_SECRET_BASE64', 'GRAFANA_ADMIN_PASSWORD')) {
    $value = Get-DemoSetting $settings $key
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like 'replace-with-*') {
        throw "Configure $key in .env or the process environment first. See docs/DEMO_OPERATING_GUIDE.md."
    }
}
$compose = $null
if (-not $SkipDependencies) { $compose = Get-DemoCompose -RequireWait }
$javaCommand = $null
$mavenCommand = $null
$bootstrap = (Get-DemoSetting $settings 'FINGUARD_AUTH_BOOTSTRAP_ENABLED') -eq 'true'
if (-not $DepsOnly) {
    if ($env:JAVA_HOME) {
        $javaCommand = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $javaCommand)) { $javaCommand = Join-Path $env:JAVA_HOME 'bin/java' }
        if (-not (Test-Path -LiteralPath $javaCommand)) { throw 'JAVA_HOME must point to an installed JDK 17.' }
    }
    else {
        $java = Get-Command java -ErrorAction SilentlyContinue
        if (-not $java) { throw 'Install JDK 17 and configure JAVA_HOME or PATH.' }
        $javaCommand = $java.Source
    }
    # Windows PowerShell 5.1 represents native stderr as error records, even on exit 0.
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $versionLines = & $javaCommand -version 2>&1
        $versionExitCode = $LASTEXITCODE
        $javaVersion = ($versionLines | ForEach-Object { $_.ToString() }) -join "`n"
    }
    finally { $ErrorActionPreference = $previous }
    if ($versionExitCode -ne 0 -or $javaVersion -notmatch 'version "17[.\"]') {
        throw 'This project requires Java 17. Configure JAVA_HOME or PATH before starting.'
    }
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $maven) { $maven = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($maven) { $mavenCommand = $maven.Source }
    elseif ($env:MAVEN_HOME) {
        $mavenCommand = Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'
        if (-not (Test-Path -LiteralPath $mavenCommand)) { $mavenCommand = Join-Path $env:MAVEN_HOME 'bin/mvn' }
    }
    if (-not $mavenCommand -or -not (Test-Path -LiteralPath $mavenCommand)) {
        throw 'Install Maven 3.9+ and add it to PATH or configure MAVEN_HOME.'
    }
    if ($bootstrap) {
        foreach ($key in @('FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME', 'FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD', 'FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME', 'FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD')) {
            if ([string]::IsNullOrWhiteSpace((Get-DemoSetting $settings $key))) { throw "Configure $key before enabling demo accounts." }
        }
    }
}
$plan = [pscustomobject]@{
    RepoRoot = $repoRoot
    StartDependencies = -not $SkipDependencies
    StartApplication = -not $DepsOnly
    ComposeCommand = $compose
    JavaCommand = $javaCommand
    MavenCommand = $mavenCommand
    BootstrapEnabled = $bootstrap
}
if ($DryRun) { return $plan }
Push-Location $repoRoot
try {
    if (-not $SkipDependencies) {
        $composeArgs = @($compose.Prefix) + @('up', '-d', '--wait', 'mysql', 'rabbitmq', 'redis')
        & $compose.Command @composeArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    if ($DepsOnly) { Write-Host 'Dependencies started. Application has not been started.'; return }
    Write-Host 'Starting application. Console: http://127.0.0.1:8080/demo/index.html#guided (default port)'
    Write-Host 'Ctrl+C stops the app; stop-demo.ps1 stops its dependencies and preserves volumes.'
    & $mavenCommand spring-boot:run
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally { Pop-Location }
