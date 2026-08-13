# Week 6 Day 7 Final Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the current FinGuard Core revision can be rebuilt, run, exercised through its complete business and monitoring flow, cleaned safely, and handed off with evidence-backed documentation, interview notes, and resume statements.

**Architecture:** Day 7 adds no product capability. It introduces one guarded PowerShell acceptance entry point around the existing six-service Compose stack, then consolidates Day 1–Day 6 evidence into focused final documents whose claims link back to tests, runtime acceptance, CI, deployment, performance, and security records.

**Tech Stack:** Java 17, Maven, Spring Boot 3.5.16, MySQL 8.4, RabbitMQ 4.3, Redis 8.2, Docker Compose, Micrometer, Prometheus 3.7, Grafana 12.3, PowerShell 5.1+, GitHub Actions, Markdown.

## Global Constraints

- Keep Flyway at V11; do not edit V1–V11 or add a migration.
- Do not add an API, business state, dependency, runtime service, frontend, or infrastructure platform.
- Use only the fixed Compose project `finguard-day7` and Day 7-owned containers, network, data, and volumes for destructive cleanup.
- Never run `docker compose down -v` against the ordinary development stack or `compose.linux.yml` deployment.
- Generate JWT, database, RabbitMQ, Redis, Grafana, and bootstrap secrets at runtime; never accept secret values as command-line parameters or print them.
- Do not commit `.env`, JTL, scanner output, raw database exports, JWTs, passwords, host addresses, private keys, or registry credentials.
- Treat the Day 5 authenticated baseline and V11 `EXPLAIN ANALYZE` as valid historical evidence; treat the all-`401` optimization retest as invalid performance evidence.
- Every final number must be freshly measured on Day 7 or linked to a named Day 1–Day 6 evidence document or hosted run.
- A failed health check, non-`200` authenticated probe, residual resource, test failure, secret finding, or scope drift stops completion and commit.

---

### Task 1: Add a guarded and testable Day 7 acceptance entry point

**Files:**
- Create: `scripts/acceptance/invoke-week6-day7-acceptance.ps1`
- Create: `scripts/acceptance/week6-day7-functions.ps1`
- Create: `scripts/acceptance/tests/test-week6-day7-acceptance.ps1`
- Create: `sample-data/demo-import.csv`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: repository root, `docker-compose.yml`, current `Dockerfile`, the existing HTTP API, and Docker Compose.
- Produces: `Invoke-Week6Day7Acceptance -DryRun` planning output and, for a real run, a final object containing `ProjectName`, `ServicesHealthy`, `FlywayVersion`, `OpenApiPathCount`, `PrometheusTargetUp`, `BusinessFlowPassed`, `RestartPassed`, and `CleanupPassed`.

- [x] **Step 1: Write the failing acceptance-tool test**

Create `scripts/acceptance/tests/test-week6-day7-acceptance.ps1` with assertions equivalent to:

```powershell
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$runner = Join-Path $repo 'scripts\acceptance\invoke-week6-day7-acceptance.ps1'

if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw 'Day 7 acceptance runner is missing'
}

$plan = & $runner -DryRun
if ($plan.ProjectName -ne 'finguard-day7') { throw 'Unsafe project name' }
if (($plan.Services -join ',') -ne 'mysql,rabbitmq,redis,app,prometheus,grafana') {
    throw 'The plan must cover exactly six services'
}
if (($plan.Commands -join "`n") -notmatch '--project-name finguard-day7') {
    throw 'Every Compose operation must be scoped to finguard-day7'
}
if (($plan.Commands -join "`n") -match '(?i)(password|secret|token)=\S+') {
    throw 'Dry-run output contains a secret value'
}
if (($plan.CleanupCommands -join "`n") -notmatch 'down --volumes --remove-orphans') {
    throw 'The isolated acceptance project must define complete cleanup'
}
Write-Output 'WEEK6_DAY7_ACCEPTANCE_TOOL_TESTS=PASS'
```

- [x] **Step 2: Run the test and observe the expected failure**

Run:

```powershell
& scripts/acceptance/tests/test-week6-day7-acceptance.ps1
```

Expected: failure with `Day 7 acceptance runner is missing`.

- [x] **Step 3: Implement the minimal safe runner contract**

Create `scripts/acceptance/invoke-week6-day7-acceptance.ps1` with this public shape:

```powershell
[CmdletBinding()]
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$projectName = 'finguard-day7'
$services = @('mysql', 'rabbitmq', 'redis', 'app', 'prometheus', 'grafana')
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) (
    'finguard-day7-' + [Guid]::NewGuid().ToString('N'))

function New-RandomBase64([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose --project-name $projectName `
        --env-file $script:environmentFile `
        --file $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE"
    }
}
```

The runner must generate its environment file inside `$temporaryRoot`, record commands without secret values in `-DryRun`, use condition polling instead of fixed sleeps, and perform this exact real-run sequence inside `try/finally`:

1. validate Java 17, Maven, Docker, Compose, files, and absence of an existing `finguard-day7` project;
2. generate temporary credentials and bootstrap users;
3. run Compose config, build, and `up -d` for the six services;
4. poll six health states, Flyway V11, OpenAPI, Prometheus target, and Grafana;
5. execute the API/business checks defined in Task 3;
6. restart only `app`, then poll recovery and repeat login/database/Prometheus probes;
7. delete Day 7 business fixtures and inspect zero residual counts;
8. run `down --volumes --remove-orphans` for `finguard-day7` only;
9. remove `$temporaryRoot` and verify containers, network, volumes, temporary files, and port 8080 are absent.

- [x] **Step 4: Add deterministic demonstration CSV data**

Create `sample-data/demo-import.csv` as UTF-8 without credentials or environment-specific IDs:

```csv
account_no,external_transaction_no,direction,amount,transaction_time,description
DAY7-DEMO,DAY7-CSV-LARGE-001,EXPENSE,10000.00,2026-08-13 10:00:00,day7 large amount demonstration
DAY7-DEMO,DAY7-CSV-MATCH-001,INCOME,88.00,2026-08-13 10:05:00,day7 reconciliation demonstration
```

The runner must replace only the fixed date values in a temporary copy when the current business clock would reject them; it must never modify the committed sample.

- [x] **Step 5: Ignore only generated acceptance artifacts**

Add these lines to `.gitignore` if not already covered:

```gitignore
scripts/acceptance/results/
```

- [x] **Step 6: Run the tool test and inspect DryRun output**

Run:

```powershell
& scripts/acceptance/tests/test-week6-day7-acceptance.ps1
& scripts/acceptance/invoke-week6-day7-acceptance.ps1 -DryRun |
    Format-List
```

Expected: `WEEK6_DAY7_ACCEPTANCE_TOOL_TESTS=PASS`; the plan names exactly six services, every Compose command contains `--project-name finguard-day7`, and no generated secret value appears.

---

### Task 2: Establish the fresh Java, configuration, test, and image baseline

**Files:**
- Create: `docs/review/week6-review.md` only after the commands finish.

**Interfaces:**
- Consumes: Task 1 runner, Maven project, Compose files, workflow, and Dockerfile.
- Produces: fresh Day 7 environment versions, Maven totals, configuration results, and image facts for the final review.

- [x] **Step 1: Capture tool and Git facts without secrets**

Run:

```powershell
java -version
mvn -version
docker version
docker compose version
git status --short --branch
git log -7 --oneline --decorate
```

Expected: Java 17 for both `java` and Maven; Docker Engine and Compose respond; branch and ahead/behind state are recorded exactly.

- [x] **Step 2: Run the guarded configuration preflight**

Run:

```powershell
& scripts/acceptance/tests/test-week6-day7-acceptance.ps1
& scripts/acceptance/invoke-week6-day7-acceptance.ps1 -DryRun |
    Out-String -Width 240
```

Expected: both development and Linux Compose validation are present in the plan, Linux uses a full 40-character image SHA, and no secret value is rendered.

- [x] **Step 3: Run the complete Java 17 regression**

Run:

```powershell
mvn -B -ntp clean test
```

Expected: `BUILD SUCCESS`; record the exact tests run, failures, errors, and skipped values from the fresh output. Any failure or skip blocks Day 7 completion.

- [x] **Step 4: Build and inspect the application image**

Run through the acceptance runner's build phase, then verify the resulting Day 7 image reports Java 17, runs as UID/GID 10001, and does not contain Maven, source files, `.env`, or repository build directories.

- [x] **Step 5: Record only observed results**

Create `docs/review/week6-review.md` with the heading structure defined in Task 6, then add the `Day 7 fresh baseline` table with columns `Check`, `Command/evidence`, `Observed result`, and `Status`. Do not prefill historical counts as current results.

---

### Task 3: Run isolated six-service and complete business acceptance

**Files:**
- Modify: `scripts/acceptance/invoke-week6-day7-acceptance.ps1` only for defects exposed by its own test or real run.
- Modify: `docs/review/week6-review.md` with the sanitized result summary.

**Interfaces:**
- Consumes: Task 1 runner, `sample-data/demo-import.csv`, and Task 2 image.
- Produces: one acceptance result object with all boolean gates true and no residual Day 7 resource.

- [x] **Step 1: Start the isolated six-service stack and poll readiness**

Run:

```powershell
$day7Result = & scripts/acceptance/invoke-week6-day7-acceptance.ps1
$day7Result | Format-List
```

Required readiness facts:

- MySQL, RabbitMQ, Redis, app, Prometheus, and Grafana are `healthy`;
- Flyway latest successful version is `11`;
- `/actuator/health` is `200/UP`;
- `/v3/api-docs` contains exactly 18 paths;
- Prometheus reports the app target `UP`;
- Grafana health is successful;
- dependency ports are not exposed beyond the loopback bindings defined by development Compose.

- [x] **Step 2: Execute authentication and authorization gates**

The runner must send JSON using UTF-8 bytes and prove:

- temporary ADMIN and REVIEWER logins return `200` with non-empty JWTs;
- anonymous `GET /api/accounts` returns `401`;
- REVIEWER `POST /api/accounts` returns `403` with no database side effect;
- ADMIN `GET /api/audit-logs` returns `200`;
- REVIEWER `GET /api/audit-logs` returns `403`.

- [x] **Step 3: Build the business fixture through public APIs**

Use these existing contracts:

```json
{"accountNo":"DAY7-DEMO","accountName":"Day 7 Demo","accountType":"BANK"}
```

```json
{"accountId":1,"externalTransactionNo":"DAY7-CSV-MATCH-001","direction":"INCOME","amount":88.00,"transactionTime":"<current accepted local time>","description":"day7 manual match"}
```

The script must read the created `accountId` from the account response and substitute it into the transaction body; it must not assume ID `1`.

- [x] **Step 4: Execute asynchronous import and reconciliation**

Use `curl.exe -F "file=@<temporary-demo-csv>"` with the ADMIN Bearer token. Require initial upload `202`, poll `GET /api/import-jobs/{id}` to `SUCCESS`, then post `{"importJobId":<id>}` and require `202`; poll the reconciliation job to `COMPLETED`.

Cross-check:

- the imported transaction count matches the CSV accepted-row count;
- RabbitMQ import/reconciliation queues have no unacknowledged acceptance messages;
- reconciliation results contain the expected matched item and a large-amount risk path;
- repeated upload returns the original job with `duplicateFile=true`;
- repeated reconciliation returns the original job with `duplicateRequest=true`.

- [x] **Step 5: Execute review, audit, statistics, Redis, and metrics checks**

Query `GET /api/review-tasks?...ruleCode=LARGE_AMOUNT`, read its real `id` and `version`, then send as REVIEWER:

```json
{"decision":"CONFIRMED","version":0,"note":"week6 day7 verified"}
```

Substitute the returned version rather than assuming `0`. Require terminal `CONFIRMED`, one matching `REVIEW_CONFIRMED` audit row, updated statistics from MySQL, the rebuildable Redis statistics key, and increased import/reconciliation business metrics from Prometheus.

- [x] **Step 6: Verify restart recovery**

Restart only the Day 7 `app` service with Compose, poll health, repeat ADMIN login, query the created account/import/reconciliation/review facts, and confirm Prometheus returns the app target to `UP`. Do not restart or delete data volumes in this step.

- [x] **Step 7: Verify runner cleanup independently**

After the runner's `finally` block completes, run:

```powershell
docker ps -a --filter label=com.docker.compose.project=finguard-day7
docker volume ls --filter label=com.docker.compose.project=finguard-day7
docker network ls --filter label=com.docker.compose.project=finguard-day7
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

Expected: no Day 7 containers, volumes, network, temporary environment file, JWT, fixture, or listener remains. Existing non-Day-7 resources must match the preflight inventory.

---

### Task 4: Create the final engineering documentation set

**Files:**
- Create: `docs/ARCHITECTURE.md`
- Create: `docs/DATABASE.md`
- Create: `docs/API.md`
- Create: `docs/DEPLOYMENT.md`
- Create: `docs/MONITORING.md`
- Create: `docs/PERFORMANCE_REPORT.md`
- Create: `docs/SECURITY_TEST_REPORT.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: current source, migrations V1–V11, OpenAPI, Compose, and Day 1–Day 6 evidence documents.
- Produces: stable reader-facing entry documents that link to detailed evidence rather than duplicating it.

- [x] **Step 1: Write architecture and data documentation**

`docs/ARCHITECTURE.md` must contain: project purpose, modular-monolith boundary, module responsibility table, synchronous HTTP path, asynchronous Outbox/RabbitMQ path, MySQL/Redis/RabbitMQ truth boundaries, security boundary, monitoring path, and deployment topology.

`docs/DATABASE.md` must contain: V1–V11 migration index, an ER diagram covering users/roles, accounts/transactions, import, reconciliation, risk/review, audit, and Outbox tables; critical unique/FK/check/index invariants; money/time conventions; and the rule never to edit applied migrations.

- [x] **Step 2: Write API and error documentation**

`docs/API.md` must list all 18 OpenAPI paths grouped by authentication, accounts, transactions, import, reconciliation, review, audit, and statistics; include the ADMIN/REVIEWER/anonymous matrix, `202 + Location` asynchronous semantics, pagination defaults, idempotency responses, `401/403/404/409/429` examples, and a link to Swagger UI.

- [x] **Step 3: Write deployment and monitoring documentation**

`docs/DEPLOYMENT.md` must separate host Maven, development Compose, GitHub Actions/GHCR, and persistent Linux deployment; document full-SHA images, secret injection, loopback/private ports, start/status/logs/stop/rollback commands, retained volumes, and the distinction between application rollback and database rollback.

`docs/MONITORING.md` must document only `health` and `prometheus` Actuator exposure, the three low-cardinality business metrics, Prometheus scrape path, Grafana provisioning, target-UP troubleshooting, label prohibitions, and links to Day 3 acceptance.

- [x] **Step 4: Consolidate performance and security evidence**

`docs/PERFORMANCE_REPORT.md` must separate environment, dataset, JMeter, Prometheus, SQL/`EXPLAIN ANALYZE`, V11 index rationale, valid measurements, invalid all-`401` retest, limitations, and reproducible commands.

`docs/SECURITY_TEST_REPORT.md` must summarize source/dependency/image/config/secret/passive-Web coverage, RBAC/input checks, the one Low development-port fix, three fault drills, limitations, and links to the detailed Day 6 reports. It must not claim active penetration testing.

- [x] **Step 5: Turn README into the final entry point**

Keep README concise and add: one architecture diagram, one core-flow diagram, quick-start paths, demo link, documentation index, verified evidence table, current limitations, and a statement that this is a personal project rather than a production system. Replace stale “current progress Day 6” wording only after Day 7 acceptance is complete.

- [x] **Step 6: Check every Markdown link**

Run a repository-local link scan that parses Markdown destinations, ignores HTTP anchors, and fails when a referenced local file does not exist. Record the command and result in `docs/review/week6-review.md`.

---

### Task 5: Create demonstration, interview, and resume materials

**Files:**
- Create: `docs/DEMO.md`
- Create: `docs/INTERVIEW_NOTES.md`
- Create: `docs/RESUME.md`

**Interfaces:**
- Consumes: final APIs, Day 7 result, and all evidence documents.
- Produces: a 10–15 minute reproducible demo, project deep-dive notes, and 3–4 evidence-backed resume bullets.

- [x] **Step 1: Write the demonstration runbook**

`docs/DEMO.md` must include: prerequisites, safe temporary credentials, startup choice, Swagger login/Authorize, anonymous `401`, REVIEWER `403`, account/manual transaction setup, CSV upload, polling, reconciliation, review decision, audit/statistics, Prometheus/Grafana, cleanup, and expected status at every step. Use placeholders such as `<ADMIN_JWT>` only in documentation; never embed a real token.

- [x] **Step 2: Write interview notes around decisions, not memorized definitions**

`docs/INTERVIEW_NOTES.md` must cover: modular monolith choice, MySQL truth vs Redis cache, file SHA-256 vs business uniqueness, Outbox/Confirm/manual ACK, consumer idempotency, retries/DLQ, reconciliation determinism, risk-rule boundaries, optimistic locking, audit transaction boundaries, cache invalidation after commit, low-cardinality metrics, index evidence, immutable deployment, security/fault findings, and at least 20 questions with concise answers.

- [x] **Step 3: Write only defensible resume statements**

`docs/RESUME.md` must contain project title, role (`个人项目`), technology stack, 3–4 Chinese bullets, a 30-second introduction, a 2-minute introduction, evidence links for every number, and a `Do not claim` section excluding production users, billion-scale data, microservices, Kubernetes, unmeasured performance percentages, and real company production experience.

- [x] **Step 4: Cross-check claims against evidence**

For each numeric or outcome claim in README, `docs/INTERVIEW_NOTES.md`, and `docs/RESUME.md`, add an evidence mapping row in `docs/review/week6-review.md`. Remove any claim that cannot be mapped.

---

### Task 6: Write the Week 6 and six-week final review

**Files:**
- Modify: `docs/review/week6-review.md`
- Modify: `TASKS.md`

**Interfaces:**
- Consumes: Tasks 2–5 results and existing Week 1–Week 5 reviews.
- Produces: final acceptance record and canonical Day 7 completion state.

- [x] **Step 1: Verify and complete the review structure before filling final results**

Ensure the file created in Task 2 uses these exact headings:

```markdown
# Week 6 Final Review
## Scope and baseline
## Day 1–Day 7 delivery map
## Day 7 fresh baseline
## Automated regression
## Six-service runtime acceptance
## JWT/RBAC and business flow
## MySQL/RabbitMQ/Redis/monitoring evidence
## Deployment/performance/security/fault evidence
## Cleanup and resource isolation
## Documentation and resume claim audit
## Key decisions and trade-offs
## Problems encountered and root causes
## Known limitations
## Interview questions
## Six-week learning outcome and next steps
```

- [x] **Step 2: Fill the review only from observed evidence**

Record commands, actual totals/statuses, relevant commit/SHA/run links, and sanitized facts. Mark unavailable external evidence as unavailable; never change it to pass by inference.

- [x] **Step 3: Update TASKS only after all acceptance gates pass**

Change the Day 7 summary row and section status to `已完成`, check each completed checkbox, add the actual acceptance conclusion, and extend the Git milestone index with the final commit only after it exists.

- [x] **Step 4: Update the Week 6 route**

Change the future-route text so Week 6 is described as completed and point readers to `docs/review/week6-review.md`; do not leave Day 7 described as a future candidate.

---

### Task 7: Perform final cleanup, verification, commit, push, and hosted-CI closeout

**Files:**
- Modify only files listed by Tasks 1 and 4–6, plus a narrowly scoped tested defect fix if Task 3 exposed one.

**Interfaces:**
- Consumes: complete Day 7 working tree.
- Produces: one scoped commit, synchronized remote branch, green hosted CI, and clean worktree.

- [x] **Step 1: Re-run focused tooling tests and full Java regression**

Run:

```powershell
& scripts/acceptance/tests/test-week6-day7-acceptance.ps1
mvn -B -ntp clean test
```

Expected: tool tests pass; Maven reports zero failures, zero errors, and zero skipped tests.

- [x] **Step 2: Re-run safe configuration and security smoke**

Run the Day 7 DryRun/preflight, Compose config checks, documentation-link check, and existing `security/tests/test-security-tools.ps1`. Scan tracked and untracked candidate files for private keys, JWT-looking strings, password assignments, `.env` files, result directories, JTL, and scanner output; manually triage every match.

- [x] **Step 3: Verify cleanup and scope independently**

Require no `finguard-day7` containers/network/volumes, no Day 7 rows/messages/keys, no listener on 8080, and no temporary environment file. Compare non-Day-7 Docker resources to the preflight inventory and perform only read-only ECS status checks if current authorization and connectivity are available.

- [x] **Step 4: Inspect whitespace, diff, and generated files**

Run:

```powershell
git diff --check
git status --short
git diff --stat
git diff -- . ':(exclude)target' ':(exclude)performance/results' ':(exclude)security/results'
```

Expected: no whitespace errors, no generated/sensitive artifacts, no unrelated changes, and every Day 7 file maps to this plan.

- [x] **Step 5: Stage intentionally and inspect the staged patch**

Run `git add` with the explicit Day 7 file list, then:

```powershell
git diff --cached --check
git diff --cached --stat
git diff --cached
```

Expected: staged content is complete, scoped, readable, and contains no secret or generated output.

- [x] **Step 6: Commit the complete Day 7 milestone**

Run:

```powershell
git commit -m "docs: complete week 6 final acceptance and project handoff"
```

After the commit, update the TASKS Git milestone index only if that update was already staged with the correct final SHA strategy; otherwise use a follow-up documentation commit rather than amending hidden content.

- [x] **Step 7: Push and verify real hosted CI**

Run:

```powershell
git push origin main
```

Wait for the actual GitHub Actions run triggered by this push. Require its test, package, image assertions, and configured release/deployment verification jobs to finish successfully; record the real run URL and result in the final report rather than treating YAML presence as evidence.

- [x] **Step 8: Confirm final repository state**

Run:

```powershell
git status --short --branch
git log -5 --oneline --decorate
```

Expected: `main` is synchronized with `origin/main`, worktree is clean, and the Day 7/Week 6 final commits are visible.

## Plan Self-Review

- Scope coverage: the plan covers full Maven, Docker six-service, JWT/HTTP/MySQL/RabbitMQ/Redis/Prometheus/Grafana, cleanup, secrets, `git diff --check`, README, architecture, database/ER, API, deployment, monitoring, performance, security, fault review, demo, interview, resume, Week 6 review, commit, push, and hosted CI.
- Boundary check: no new product feature, migration, dependency, service, or unsupported production claim is planned.
- Safety check: destructive cleanup is fixed to `finguard-day7`; ordinary development and persistent ECS volumes are explicitly excluded.
- Placeholder check: runtime values are deliberately read from API responses or generated inside the runner; documentation token markers are explicitly non-secret examples, not unfinished requirements.
- Evidence check: the invalid Day 5 all-`401` retest is preserved as a limitation and cannot become a resume performance claim.
