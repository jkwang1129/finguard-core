# Week 6 Day 6 Security And Fault Drills Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a source-backed security report and three repeatable, safely isolated fault drills without changing FinGuard business semantics or retained data.

**Architecture:** Versioned PowerShell wrappers run repository/image/passive-web security checks and controlled Compose drills. Disruptive operations target a dedicated `finguard-day6` project; canonical reports retain only sanitized summaries, while raw scanner output and temporary secrets remain ignored local artifacts.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Maven, MySQL 8.4, RabbitMQ 4.3.4, Redis 8.2.8, Docker Compose, Trivy, OWASP ZAP Baseline, PowerShell.

## Global Constraints

- Never print or commit passwords, JWTs, private keys, host addresses, registry tokens, raw scanner reports, or temporary environment files.
- Never delete or reuse ordinary development or retained Linux volumes; disruptive work uses project `finguard-day6`.
- ZAP is passive baseline only; no active scan against ECS or another network target.
- Do not edit Flyway V1 through V11 or add Day 7 deliverables.
- Validate scanner findings against source and effective controls before assigning severity.

---

### Task 1: Freeze the Day 6 contract

**Files:**
- Create: `docs/design/week6-day6-security-and-fault-drills-design.md`
- Create: `docs/superpowers/plans/2026-08-12-week6-day6-security-and-fault-drills.md`
- Modify: `TASKS.md`

- [x] Record current Git, test, migration, container, security and deployment baselines.
- [x] Lock the isolated-environment and no-active-ECS-scan boundaries.
- [x] Define completion and stop gates for findings, recovery, cleanup and scope.

### Task 2: Add tested security tooling

**Files:**
- Create: `security/README.md`
- Create: `security/zap-baseline.conf`
- Create: `security/scripts/run-static-security-checks.ps1`
- Create: `security/scripts/run-zap-baseline.ps1`
- Create: `security/tests/test-security-tools.ps1`
- Modify: `.gitignore`

- [x] Write failing PowerShell tests for required parameters, dry-run command boundaries, ignored output paths and secret-safe console output.
- [x] Run the tests and confirm RED because the scripts do not exist.
- [x] Implement minimal wrappers for pinned Trivy and ZAP Docker images.
- [x] Run the tests and confirm GREEN, then execute the available checks.

### Task 3: Run the Redis outage drill

**Files:**
- Create: `scripts/drills/invoke-redis-outage-drill.ps1`
- Modify: `security/tests/test-security-tools.ps1`

- [x] Add a failing dry-run contract test that proves only the Day 6 Redis service may be stopped and started.
- [x] Implement the guarded drill with preflight, baseline, outage probes, recovery polling and cleanup verification.
- [x] Run it against the isolated project and record health, statistics fallback, fail-open behavior and recovery evidence.

### Task 4: Run the RabbitMQ retry and DLQ drill

**Files:**
- Create: `scripts/drills/invoke-messaging-retry-drill.ps1`
- Modify: `security/tests/test-security-tools.ps1`

- [x] Add a failing test for the exact focused Maven selector and output redaction.
- [x] Implement a wrapper around the real integration paths for one transient recovery and one retry-exhausted DLQ result.
- [x] Verify retry timing, headers, terminal state, no duplicate writes and queue cleanup.

### Task 5: Run the bad dependency configuration drill

**Files:**
- Create: `scripts/drills/invoke-misconfiguration-drill.ps1`
- Modify: `security/tests/test-security-tools.ps1`

- [x] Add a failing test proving the drill creates only a one-off app container and never edits `.env.linux` or invokes volume deletion.
- [x] Implement wrong-MySQL-port startup, bounded diagnostics, removal and healthy replacement verification.
- [x] Execute the drill in the isolated environment and record the symptom-to-recovery chain.

### Task 6: Validate and report security results

**Files:**
- Create: `docs/review/week6-day6-security-report.md`
- Create: `docs/review/week6-day6-fault-drills.md`

- [x] Build a source-backed threat map for authentication, authorization, files, persistence, secrets, deployment and availability.
- [x] Validate each static/dynamic finding once against real code and controls.
- [x] Record tool versions, target SHA/image, coverage, findings, false positives, limitations and remediation status.
- [x] Record all three drills as phenomenon, hypothesis, evidence, root cause, recovery and prevention.

### Task 7: Apply only validated fixes and regress

**Files:**
- Modify only files required by a confirmed in-scope finding.
- Test the affected component before implementation.

- [x] For each confirmed defect, write and observe a failing regression test.
- [x] Implement the minimal correction and rerun focused tests.
- [x] Run the complete Java 17 Maven suite and security/API smoke.

### Task 8: Accept, clean and commit

**Files:**
- Modify: `TASKS.md`
- Modify: `README.md`
- Modify: `docs/review/week6-day6-security-report.md`
- Modify: `docs/review/week6-day6-fault-drills.md`

- [x] Remove Day 6 containers, network, volumes, messages, rows, result files, temporary tokens and environment files.
- [x] Confirm ordinary development containers/data and retained deployment artifacts were not changed.
- [x] Run `git diff --check`, secret scan, scope review and fresh full regression.
- [x] Commit the complete scoped milestone only after every acceptance item passes.
