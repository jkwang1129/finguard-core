$ErrorActionPreference='Stop'
$runner=Join-Path $PSScriptRoot '../invoke-console-acceptance.ps1'
$plan=& $runner -DryRun
if ($plan.ProjectName -ne 'finguard-console') {throw 'wrong project'}
$expected=@{App=28080;MySql=23306;RabbitMq=25672;RabbitMqManagement=25673;Redis=26379;Prometheus=29090;Grafana=23000}
foreach($key in $expected.Keys){if($plan.Ports.$key -ne $expected[$key]){throw "wrong port $key"}}
if($plan.ContainerPrefix -ne 'finguard-console' -or $plan.Services.Count -ne 6){throw 'wrong isolation'}
if($plan.SecretsPolicy -ne 'random temporary process-only'){throw 'secret policy'}
if(($plan.CleanupCommands -join ' ') -match 'finguard-day7|finguard-day6|--remove-orphans'){throw 'unsafe cleanup scope'}
if($plan.OwnershipCheck -ne 'compose labels plus captured IDs and absolute owned paths'){throw 'missing ownership'}
if($plan.ComposeFallback -ne 'docker-compose'){throw 'missing compose fallback'}
Write-Output 'PASS: runner isolation contract 7 assertions'
