[CmdletBinding()]
param([switch]$DryRun,[switch]$PreflightOnly,[string]$Cases='business,scenarios,reliability,accessibility',[switch]$UseCachedRuntime,[ValidateSet('','chrome','msedge')][string]$BrowserChannel='',[switch]$EngineeringOnly)
$ErrorActionPreference='Stop'
$project='finguard-console'
$services=@('mysql','rabbitmq','redis','app','prometheus','grafana')
$ports=[ordered]@{App=28080;MySql=23306;RabbitMq=25672;RabbitMqManagement=25673;Redis=26379;Prometheus=29090;Grafana=23000}
if($EngineeringOnly){$project='finguard-day6';$ports=[ordered]@{App=18080;MySql=13306;RabbitMq=15674;RabbitMqManagement=15673;Redis=16379;Prometheus=19090;Grafana=13000}}
$imageTag=if($EngineeringOnly){'finguard-core:console-drill'}else{'finguard-core:console'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$plan=[pscustomobject]@{ProjectName=$project;ContainerPrefix=$project;Services=$services;Ports=[pscustomobject]$ports;SecretsPolicy='random temporary process-only';OwnershipCheck='compose labels plus captured IDs and absolute owned paths';ComposeFallback='docker-compose';CleanupCommands=@("label-checked $project down --volumes","captured $imageTag image ID removal");Commands=@('compose config --quiet','compose build app','compose up -d --wait','node browser-acceptance.mjs','rule-disabled and high-threshold variants')}
if($DryRun){return $plan}
foreach($command in @('docker','node','mvn.cmd')){if(!(Get-Command $command -ErrorAction SilentlyContinue)){throw "Missing command $command"}}
& docker version *> $null
if($LASTEXITCODE -ne 0){throw 'Docker Engine unavailable'}
& docker compose version *> $null
if($LASTEXITCODE -eq 0){$composeExecutable='docker';$composeStart=@('compose')}elseif(Get-Command docker-compose -ErrorAction SilentlyContinue){$composeExecutable='docker-compose';$composeStart=@()}else{throw 'Compose unavailable'}
$containers=@(& docker ps -a --format '{{.Names}}')
foreach($service in $services){if($containers -contains "$project-$service"){throw "Container conflict: $project-$service"}}
if(@(& docker ps -aq --no-trunc --filter "label=com.docker.compose.project=$project").Count -gt 0){throw 'Project already has containers'}
if(@(& docker volume ls -q --filter "label=com.docker.compose.project=$project").Count -gt 0){throw 'Project already has volumes'}
if(@(& docker network ls -q --filter "label=com.docker.compose.project=$project").Count -gt 0){throw 'Project already has networks'}
if((& docker image ls -q $imageTag)){throw 'Image tag already exists'}
foreach($port in $ports.Values){$listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$port);try{$listener.Start()}catch{throw "Port conflict: $port"}finally{$listener.Stop()}}
if($PreflightOnly){return [pscustomobject]@{Status='PASS';Plan=$plan;Compose=$composeExecutable}}
$runId=[Guid]::NewGuid().ToString('N')
$resultsRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'results'))
$owned=[IO.Path]::GetFullPath((Join-Path $resultsRoot $runId))
if(!$owned.StartsWith($resultsRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid owned path'}
New-Item -ItemType Directory -Path $owned -Force | Out-Null
$secretPath=Join-Path $owned 'runtime.env';$overridePath=Join-Path $owned 'variant.yml';$imageId=$null;$started=$false;$capturedIds=@();$previous=@{}
function Secret { $bytes=New-Object byte[] 36;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()};return [Convert]::ToBase64String($bytes) }
function Compose([string[]]$Arguments){& $composeExecutable @composeStart --project-name $project --env-file $secretPath --file (Join-Path $repo 'docker-compose.yml') --file $overridePath @Arguments; if($LASTEXITCODE -ne 0){throw "Compose failed ($LASTEXITCODE)"}}
function Variant([hashtable]$config){$content=@{services=@{app=@{environment=$config}}}|ConvertTo-Json -Depth 5;[IO.File]::WriteAllText($overridePath,$content,[Text.UTF8Encoding]::new($false))}
function Verify-Ownership {
 foreach($id in @(& docker ps -aq --no-trunc --filter "label=com.docker.compose.project=$project")){
  $info=(& docker inspect $id | ConvertFrom-Json)[0]
  if($info.Config.Labels.'com.docker.compose.project' -ne $project -or $services -notcontains $info.Config.Labels.'com.docker.compose.service' -or !$info.Name.StartsWith("/$project-")){throw 'Container ownership mismatch; cleanup refused'}
  if($capturedIds.Count -gt 0 -and $capturedIds -notcontains $info.Id){throw 'Unexpected container ID; cleanup refused'}
 }
 foreach($name in @(& docker volume ls -q --filter "label=com.docker.compose.project=$project")){
  $info=(& docker volume inspect $name | ConvertFrom-Json)[0];if($info.Labels.'com.docker.compose.project' -ne $project -or !$name.StartsWith($project+'_')){throw 'Volume ownership mismatch; cleanup refused'}
 }
}
function Capture { $script:capturedIds=@(& docker ps -aq --no-trunc --filter "label=com.docker.compose.project=$project");Verify-Ownership }
function Browser([string]$variant,[string]$groups){$dir=Join-Path $owned $variant;New-Item -ItemType Directory -Path $dir -Force|Out-Null;$browserArgs=@();if($BrowserChannel){$browserArgs=@('--browser-channel',$BrowserChannel)};& node (Join-Path $PSScriptRoot 'browser-acceptance.mjs') --base-url 'http://127.0.0.1:28080' --cases $groups --variant $variant --evidence-dir $dir @browserArgs;if($LASTEXITCODE -ne 0){throw "Browser acceptance failed: $variant"}}
try {
 $adminPassword=Secret;$reviewerPassword=Secret
 $values=[ordered]@{FINGUARD_CONTAINER_PREFIX=$project;FINGUARD_APP_IMAGE=$imageTag;FINGUARD_APP_PORT=$ports.App;MYSQL_PORT=$ports.MySql;RABBITMQ_PORT=$ports.RabbitMq;RABBITMQ_MANAGEMENT_PORT=$ports.RabbitMqManagement;REDIS_PORT=$ports.Redis;PROMETHEUS_PORT=$ports.Prometheus;GRAFANA_PORT=$ports.Grafana;MYSQL_DATABASE='finguard_console';MYSQL_USER='console';MYSQL_ROOT_PASSWORD=(Secret);MYSQL_PASSWORD=(Secret);RABBITMQ_USERNAME='console';RABBITMQ_PASSWORD=(Secret);REDIS_PASSWORD=(Secret);JWT_SECRET_BASE64=(Secret);GRAFANA_ADMIN_PASSWORD=(Secret);FINGUARD_AUTH_BOOTSTRAP_ENABLED='true';FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME='console-admin';FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD=$adminPassword;FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME='console-reviewer';FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD=$reviewerPassword;FINGUARD_RATE_LIMIT_LOGIN_LIMIT=1000;FINGUARD_RATE_LIMIT_UPLOAD_LIMIT=1000;FINGUARD_RISK_LARGE_AMOUNT_ENABLED='true';FINGUARD_RISK_LARGE_AMOUNT_THRESHOLD='10000.00';FINGUARD_RISK_POSSIBLE_DUPLICATE_ENABLED='true';FINGUARD_RISK_POSSIBLE_DUPLICATE_WINDOW='5m';FINGUARD_RISK_FREQUENT_TRANSACTION_ENABLED='true';FINGUARD_RISK_FREQUENT_TRANSACTION_WINDOW='10m';FINGUARD_RISK_FREQUENT_TRANSACTION_THRESHOLD_COUNT=5}
 [IO.File]::WriteAllLines($secretPath,@($values.GetEnumerator()|ForEach-Object{"$($_.Key)=$($_.Value)"}),[Text.UTF8Encoding]::new($false))
 foreach($entry in @{FINGUARD_CONSOLE_ADMIN_USERNAME='console-admin';FINGUARD_CONSOLE_ADMIN_PASSWORD=$adminPassword;FINGUARD_CONSOLE_REVIEWER_USERNAME='console-reviewer';FINGUARD_CONSOLE_REVIEWER_PASSWORD=$reviewerPassword}.GetEnumerator()){$previous[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process');[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
 Variant @{}
 Compose @('config','--quiet')
 if($UseCachedRuntime){
  # Test-only packaging path: pin a locally cached JRE digest, then copy the current host-built JAR.
  $version=& mvn.cmd -version | Out-String;if($version -notmatch 'Java version: 17\.'){throw 'Cached packaging requires Maven Java 17'}
  Push-Location $repo;try{& mvn.cmd -B -ntp -DskipTests package;if($LASTEXITCODE -ne 0){throw 'Host packaging failed'}}finally{Pop-Location}
  $base=(& docker image inspect eclipse-temurin:17-jre-ubi9-minimal --format '{{index .RepoDigests 0}}').Trim();if($base -notmatch '^eclipse-temurin@sha256:[a-f0-9]{64}$'){throw 'Cached runtime digest unavailable'}
  $buildPath=Join-Path $owned 'runtime';New-Item -ItemType Directory -Path $buildPath|Out-Null
  $jar=Get-ChildItem (Join-Path $repo 'target/finguard-core-*.jar');if(@($jar).Count -ne 1){throw 'Expected one current application JAR'};Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $buildPath 'app.jar')
  $dockerfile=@("FROM $base",'RUN useradd --uid 10001 --user-group --home-dir /opt/finguard --shell /sbin/nologin finguard && mkdir -p /opt/finguard && chown -R finguard:finguard /opt/finguard','WORKDIR /opt/finguard','COPY --chown=finguard:finguard app.jar ./app.jar','USER finguard','EXPOSE 8080','ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/urandom","-jar","/opt/finguard/app.jar"]')
  [IO.File]::WriteAllLines((Join-Path $buildPath 'Dockerfile'),$dockerfile,[Text.UTF8Encoding]::new($false))
  & docker build --pull=false --tag $imageTag $buildPath;if($LASTEXITCODE -ne 0){throw 'Cached runtime image build failed'}
  Remove-Item -LiteralPath (Join-Path $buildPath 'app.jar') -Force
 }else{Compose @('build','app')}
 $imageId=(& docker image inspect $imageTag --format '{{.Id}}').Trim()
 $started=$true;Compose (@('up','-d','--wait','--wait-timeout','180')+$services);Capture
 $health=@(& docker ps --filter "label=com.docker.compose.project=$project" --format '{{.Names}} {{.Status}}')
 if($health.Count -ne 6 -or @($health|Where-Object{$_ -notmatch '\(healthy\)'}).Count -gt 0){throw 'Six healthy services required'}
 if($EngineeringOnly){
  foreach($entry in @{FINGUARD_DAY6_ADMIN_USERNAME='console-admin';FINGUARD_DAY6_ADMIN_PASSWORD=$adminPassword}.GetEnumerator()){$previous[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process');[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
  Verify-Ownership
  $redisReport=& (Join-Path $repo 'scripts/drills/invoke-redis-outage-drill.ps1') -EnvironmentFile $secretPath -ProjectName finguard-day6 -BaseUrl 'http://127.0.0.1:18080'
  $redisReport | Where-Object {$_.PSObject.Properties.Name -contains 'BaselineHealth'} | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $owned 'redis-drill.json')
  Verify-Ownership
  $messagingReport=& (Join-Path $repo 'scripts/drills/invoke-messaging-retry-drill.ps1') -EnvironmentFile $secretPath -ProjectName finguard-day6 -BaseUrl 'http://127.0.0.1:18080'
  $messagingReport | Where-Object {$_.PSObject.Properties.Name -contains 'Tests'} | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $owned 'messaging-drill.json')
 }else{Browser 'baseline' $Cases}
 foreach($variant in $(if($EngineeringOnly){@()}else{@('rules-disabled','high-threshold')})){
  Verify-Ownership
  if($variant -eq 'rules-disabled'){Variant @{FINGUARD_RISK_LARGE_AMOUNT_ENABLED='false';FINGUARD_RISK_POSSIBLE_DUPLICATE_ENABLED='false';FINGUARD_RISK_FREQUENT_TRANSACTION_ENABLED='false'}}else{Variant @{FINGUARD_RISK_LARGE_AMOUNT_ENABLED='true';FINGUARD_RISK_LARGE_AMOUNT_THRESHOLD='50000.00';FINGUARD_RISK_POSSIBLE_DUPLICATE_ENABLED='true';FINGUARD_RISK_FREQUENT_TRANSACTION_ENABLED='true'}}
  Compose @('up','-d','--wait','--wait-timeout','180','--force-recreate','app');$capturedIds=@();Capture;Browser $variant 'scenarios'
 }
 $summary=[ordered]@{project=$project;observedAt=(Get-Date).ToUniversalTime().ToString('o');sixServices=$health;imageId=$imageId;packaging=$(if($UseCachedRuntime){'host Java17 JAR plus pinned cached JRE digest'}else{'repository Dockerfile'});browserChannel=$BrowserChannel;configurationSource='generated temporary env plus explicit variant override';result='PASS';evidenceDirectory=$owned}
 $summary|ConvertTo-Json -Depth 5|Set-Content -Encoding utf8 (Join-Path $owned 'summary.json')
 Write-Output ([pscustomobject]$summary)
} finally {
 try{if($started){Verify-Ownership;Compose @('down','--volumes')}}finally{
  foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
  if($imageId){$current=(& docker image inspect $imageTag --format '{{.Id}}' 2>$null);if($current -eq $imageId){& docker image rm $imageTag | Out-Null}}
  foreach($path in @($secretPath,$overridePath)){$resolved=[IO.Path]::GetFullPath($path);if(!$resolved.StartsWith($owned+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Temporary file path ownership mismatch'};if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Force}}
 }
 if($started -and @(& docker ps -aq --no-trunc --filter "label=com.docker.compose.project=$project").Count -gt 0){throw 'Cleanup left containers'}
 if($started -and @(& docker volume ls -q --filter "label=com.docker.compose.project=$project").Count -gt 0){throw 'Cleanup left volumes'}
 Write-Output 'Owned console resources and temporary credentials cleaned.'
}
