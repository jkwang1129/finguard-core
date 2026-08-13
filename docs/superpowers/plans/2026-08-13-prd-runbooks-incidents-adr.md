# PRD, Runbooks, Incidents, and ADR Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add evidence-backed product requirements, operational runbooks, fault-drill incident records, and architecture decision records, then expose them through the repository README.

**Architecture:** Keep `PROJECT_BRIEF.md`, source, tests, and existing acceptance records as facts. New documents are stable, focused entry points that summarize one concern and link to detailed evidence instead of moving or duplicating historical design/review files.

**Tech Stack:** Markdown, PowerShell 5.1, Git, existing FinGuard Core Java 17/Docker Compose/Linux scripts and evidence.

## Global Constraints

- Do not modify Java, Flyway migrations, Compose, CI, deployment scripts, or runtime behavior.
- Do not move or delete files under `docs/design/` or `docs/review/`.
- Do not include passwords, JWTs, private keys, host addresses, registry credentials, or raw temporary results.
- Describe the three dependency events as isolated fault drills, not production incidents.
- Every implementation/performance/security claim must link to current source, test, or named acceptance evidence.
- Do not claim production SLA, high availability, Exactly Once, zero vulnerabilities, or database rollback.
- Do not push to a remote as part of this plan.

---

### Task 1: Add the product requirements document

**Files:**
- Create: `docs/PRD.md`
- Read: `PROJECT_BRIEF.md`
- Read: `TASKS.md`
- Read: `docs/API.md`
- Read: `docs/DATABASE.md`
- Read: `docs/review/week6-review.md`

**Interfaces:**
- Consumes: canonical product scope, implemented API/database contracts, and final acceptance evidence.
- Produces: one product-facing source with stable requirement identifiers referenced by later documents.

- [x] **Step 1: Create the PRD with product framing and boundaries**

Write these sections: document status, product positioning, target users, user problems, goals, non-goals, and known limitations. State that FinGuard Core is a personal Java backend engineering project and modular monolith, not a production financial system.

- [x] **Step 2: Define the end-to-end journey and functional requirements**

Cover login/RBAC, account and transaction management, CSV hash deduplication and asynchronous import, reconciliation idempotency, risk/review workflow, audit/statistics, and monitoring. Use identifiers `FR-01` through `FR-08`; give each an observable acceptance statement without inventing new endpoints.

- [x] **Step 3: Define non-functional requirements and evidence**

Use identifiers `NFR-01` through `NFR-07` for correctness/idempotency, security, reliability, performance, observability, deployment/recovery, and testability. Link to `ARCHITECTURE.md`, `DATABASE.md`, `API.md`, `PERFORMANCE_REPORT.md`, `SECURITY_TEST_REPORT.md`, `MONITORING.md`, and `review/week6-review.md`.

- [x] **Step 4: Verify PRD scope and links**

Run:

```powershell
rg -n "^## |FR-0[1-8]|NFR-0[1-7]|范围外|已知限制" docs/PRD.md
```

Expected: every required section and all 15 requirement identifiers appear; no feature outside the approved specification appears.

---

### Task 2: Add operational runbooks

**Files:**
- Create: `docs/runbooks/README.md`
- Create: `docs/runbooks/local-acceptance.md`
- Create: `docs/runbooks/linux-deployment-and-rollback.md`
- Create: `docs/runbooks/dependency-incident-response.md`
- Read: `docs/DEPLOYMENT.md`
- Read: `docs/MONITORING.md`
- Read: `scripts/acceptance/invoke-week6-day7-acceptance.ps1`
- Read: `scripts/linux/preflight.sh`
- Read: `scripts/linux/deploy.sh`
- Read: `scripts/linux/status.sh`
- Read: `scripts/linux/logs.sh`
- Read: `scripts/linux/stop.sh`
- Read: `scripts/linux/rollback.sh`

**Interfaces:**
- Consumes: currently executable acceptance/deployment scripts and verified operational boundaries.
- Produces: safe operator entry points referenced by incident records.

- [x] **Step 1: Create the runbook index**

Explain audience, prerequisites, secret handling, document selection, command conventions, and destructive-action boundaries. Link the three runbooks, deployment guide, monitoring guide, and final review.

- [x] **Step 2: Create the local acceptance runbook**

Document Java 17/Maven/Docker/Compose prerequisites; DryRun, PreflightOnly, and full acceptance commands; the seven result gates; fixed `finguard-day7` isolation; and independent checks for zero containers, volumes, network, image, ports, and temporary secrets. Explicitly prohibit running `down -v` on the normal development stack.

- [x] **Step 3: Create the Linux deployment and rollback runbook**

Document preflight, immutable full-SHA image configuration, deploy, status, bounded logs, safe stop/start, upgrade, rollback, and post-action validation using the exact existing scripts. State that rollback preserves five named volumes and does not reverse Flyway or business data.

- [x] **Step 4: Create the dependency incident-response runbook**

Use a common sequence: assess impact, read health/metrics and bounded logs, identify Redis/RabbitMQ/MySQL, contain only scoped resources, recover, verify API/database/queue/cache/monitoring, clean temporary artifacts, and link the matching incident record. Include escalation conditions for data-integrity uncertainty, exhausted DLQ processing, migration mismatch, or missing rollback evidence.

- [x] **Step 5: Verify safety language and script references**

Run:

```powershell
rg -n "finguard-day7|down -v|完整.*SHA|rollback.sh|Flyway|有限|bounded|升级|恢复" docs/runbooks
```

Expected: isolation and rollback boundaries are explicit; every named script exists in the repository.

---

### Task 3: Add isolated fault-drill incident records

**Files:**
- Create: `docs/incidents/README.md`
- Create: `docs/incidents/redis-outage.md`
- Create: `docs/incidents/rabbitmq-retry-dlq.md`
- Create: `docs/incidents/mysql-misconfiguration.md`
- Read: `docs/review/week6-day6-fault-drills.md`
- Read: `scripts/drills/invoke-redis-outage-drill.ps1`
- Read: `scripts/drills/invoke-messaging-retry-drill.ps1`
- Read: `scripts/drills/invoke-misconfiguration-drill.ps1`

**Interfaces:**
- Consumes: three verified Day 6 drill timelines and evidence.
- Produces: normalized incident-style learning records linked from the response runbook.

- [x] **Step 1: Create the incident index and common interpretation**

State that all records are controlled, isolated exercises from 2026-08-12. Provide a table with system, injected condition, observed behavior, recovery, and record link. Link the source fault-drill review and dependency response runbook.

- [x] **Step 2: Create the Redis outage record**

Include nature/date, boundary, symptoms, detection, timeline, root cause, expected MySQL fallback, login/upload rate-limit fail-open consequence, recovery, verification, cleanup, prevention, and source evidence.

- [x] **Step 3: Create the RabbitMQ retry/DLQ record**

Include the competing-consumer pitfall, controlled single-consumer correction, retry-attempt header reaching 2, terminal `FAILED`, no duplicate reconciliation results, queue cleanup, and prevention checks. Do not claim Exactly Once.

- [x] **Step 4: Create the MySQL misconfiguration record**

Include one-off app with `MYSQL_PORT=1`, fast Spring startup failure, unchanged healthy application/dependencies, removal of only the faulty container, restored health, and configuration preflight prevention.

- [x] **Step 5: Verify normalized structure and drill labeling**

Run:

```powershell
$records = Get-ChildItem docs/incidents -File -Filter '*.md' | Where-Object Name -ne 'README.md'
foreach ($record in $records) {
  foreach ($heading in @('## 性质与边界','## 影响与现象','## 时间线','## 根因','## 恢复与验证','## 预防','## 证据')) {
    if (-not (Select-String -LiteralPath $record.FullName -SimpleMatch $heading -Quiet)) {
      throw "$($record.Name) missing $heading"
    }
  }
  if (-not (Select-String -LiteralPath $record.FullName -Pattern '隔离.*演练|故障演练' -Quiet)) {
    throw "$($record.Name) is not labeled as a drill"
  }
}
"INCIDENT_STRUCTURE=PASS records=$($records.Count)"
```

Expected: `INCIDENT_STRUCTURE=PASS records=3`.

---

### Task 4: Add architecture decision records

**Files:**
- Create: `docs/adr/README.md`
- Create: `docs/adr/0001-modular-monolith.md`
- Create: `docs/adr/0002-mysql-truth-and-outbox.md`
- Create: `docs/adr/0003-rbac-duty-separation.md`
- Create: `docs/adr/0004-redis-fail-open.md`
- Create: `docs/adr/0005-immutable-image-deployment.md`
- Read: `docs/ARCHITECTURE.md`
- Read: relevant records under `docs/design/` and `docs/review/`

**Interfaces:**
- Consumes: already implemented architectural choices and their evidence.
- Produces: a numbered decision history with a stable ADR template and index.

- [x] **Step 1: Create the ADR index and lifecycle rules**

Define status values `Proposed`, `Accepted`, `Deprecated`, and `Superseded`; state that these five records are `Accepted`; require a new ADR for a decision reversal; and index all five records.

- [x] **Step 2: Record the modular-monolith decision**

Explain bounded packages and shared deployment, transaction/learning benefits, coupling/scaling costs, and why evidence does not justify microservices or Kubernetes.

- [x] **Step 3: Record MySQL truth and Outbox**

Explain the database/message dual-write window, transactional Outbox decision, publisher confirm/manual ACK, consumer idempotency and uniqueness fallback, at-least-once consequence, and rejected direct dual-write/Exactly Once claims.

- [x] **Step 4: Record RBAC duty separation**

Explain anonymous login, shared reads, ADMIN business writes, REVIEWER-only review decision, 401/403 distinction, audit consequence, and why one all-powerful role was rejected.

- [x] **Step 5: Record Redis fail-open behavior**

Explain MySQL fallback for statistics, temporary loss of rate limiting during Redis failure, health/metric visibility, accepted availability/security trade-off, and why Redis cannot become a source of truth.

- [x] **Step 6: Record immutable-image deployment**

Explain full 40-character SHA tags, CI-gated GHCR publishing, fixed Linux topology, preserved volumes, rollback state, database boundary, and rejection of mutable `latest`.

- [x] **Step 7: Verify ADR structure**

Run:

```powershell
$records = Get-ChildItem docs/adr -File -Filter '[0-9][0-9][0-9][0-9]-*.md'
foreach ($record in $records) {
  foreach ($heading in @('## 状态','## 背景','## 决策','## 理由','## 后果','## 替代方案','## 证据')) {
    if (-not (Select-String -LiteralPath $record.FullName -SimpleMatch $heading -Quiet)) {
      throw "$($record.Name) missing $heading"
    }
  }
}
"ADR_STRUCTURE=PASS records=$($records.Count)"
```

Expected: `ADR_STRUCTURE=PASS records=5`.

---

### Task 5: Expose the new documentation and verify the complete change

**Files:**
- Modify: `README.md`
- Verify: `docs/PRD.md`
- Verify: `docs/runbooks/*.md`
- Verify: `docs/incidents/*.md`
- Verify: `docs/adr/*.md`
- Verify: `docs/superpowers/plans/2026-08-13-prd-runbooks-incidents-adr.md`

**Interfaces:**
- Consumes: Tasks 1 through 4.
- Produces: discoverable documentation with repository-wide structural, link, secret, and scope evidence.

- [x] **Step 1: Add README documentation-index entries**

Add direct entries for `docs/PRD.md`, `docs/runbooks/README.md`, `docs/incidents/README.md`, and `docs/adr/README.md`. Preserve existing links to architecture, database, API, demo, deployment, monitoring, performance, security, interview, resume, and final review.

- [x] **Step 2: Verify expected files and content counts**

Run:

```powershell
$expected = @(
  'docs/PRD.md',
  'docs/runbooks/README.md','docs/runbooks/local-acceptance.md','docs/runbooks/linux-deployment-and-rollback.md','docs/runbooks/dependency-incident-response.md',
  'docs/incidents/README.md','docs/incidents/redis-outage.md','docs/incidents/rabbitmq-retry-dlq.md','docs/incidents/mysql-misconfiguration.md',
  'docs/adr/README.md','docs/adr/0001-modular-monolith.md','docs/adr/0002-mysql-truth-and-outbox.md','docs/adr/0003-rbac-duty-separation.md','docs/adr/0004-redis-fail-open.md','docs/adr/0005-immutable-image-deployment.md'
)
foreach ($path in $expected) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing $path" }
  if ((Get-Item -LiteralPath $path).Length -eq 0) { throw "Empty $path" }
}
"EXPECTED_DOCS=PASS files=$($expected.Count)"
```

Expected: `EXPECTED_DOCS=PASS files=15`.

- [x] **Step 3: Verify every repository Markdown relative link**

Enumerate only tracked and non-ignored candidate Markdown files with `git ls-files --cached --others --exclude-standard -- '*.md'`. Parse Markdown links, skip `http`, `https`, `mailto`, and same-file anchors, resolve each remaining target relative to the source file, and fail with source/target pairs when any path is absent.

Expected: zero broken relative links.

- [x] **Step 4: Scan candidate files for secrets and unsupported claims**

Scan new and modified files for PEM private-key headers, JWT-shaped strings, literal secret/password/token assignments, IPv4 addresses, and the unsupported phrases `生产 SLA`, `Exactly Once`, `零漏洞`, and `高可用`. Manually accept only explicit negations such as “不声称 Exactly Once”; remove concrete secret-like values.

- [x] **Step 5: Inspect Git scope and whitespace**

Run:

```powershell
git diff --check
git status --short
git diff --stat
git diff -- README.md docs/PRD.md docs/runbooks docs/incidents docs/adr docs/superpowers/plans/2026-08-13-prd-runbooks-incidents-adr.md
```

Expected: only the plan, README, PRD, runbooks, incident records, and ADR files are changed; no generated or runtime file appears.

- [x] **Step 6: Leave the verified implementation ready for user-directed Git closeout**

Do not push. Report the new files, validation commands/results, current branch state, and whether an implementation commit was created. If the user has not explicitly requested an implementation commit, leave the verified documentation changes uncommitted.

## Plan Self-Review

- Specification coverage: Tasks 1–5 cover all 15 new files, README discoverability, evidence reuse, scope boundaries, structure checks, links, secrets, and Git review.
- Placeholder scan: every task names exact files, headings, commands, and expected results.
- Interface consistency: runbooks link to incidents; incidents link to the response runbook; README links to the four directory entry points; all paths match the approved design.
- Proportional verification: runtime code is unchanged, so the plan intentionally omits another 379-test Maven run and requires documentation-specific gates instead.
