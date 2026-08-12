# Week 6 Day 5 Performance Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure one real FinGuard query on the retained Linux deployment, make at most one evidence-backed optimization, and prove the result with repeatable JMeter, Prometheus, MySQL, regression, and cleanup evidence.

**Architecture:** A parameterized JMeter CLI plan drives authenticated, read-only audit-log pagination through an SSH tunnel. Versioned SQL scripts create and remove a 50,000-row synthetic fixture, while JMeter dashboards, Prometheus queries, and `EXPLAIN ANALYZE` provide independent evidence. If all three point to the default audit sort path, additive Flyway V11 supplies the minimal index.

**Tech Stack:** Java 17, Spring Boot 3.5.16, MyBatis-Plus, MySQL 8.4, Flyway, Apache JMeter, Docker Compose, Prometheus, PowerShell, Bash/SSH.

## Global Constraints

- Run load only in CLI mode and never commit JTL, HTML reports, JWTs, passwords, SSH host details, or JMeter binaries.
- Preserve Day 4 named volumes and existing business rows; delete only the `D5PERF_` synthetic fixture.
- Do not expose new public ports; reach the ECS application through the existing SSH boundary.
- Stop escalation on >1% errors, non-UP health, restart/OOM, sustained CPU saturation, sustained Hikari pending, or low disk.
- Implement at most one optimization and only after JMeter, Prometheus, and `EXPLAIN ANALYZE` agree.
- Never edit V1 through V10; any schema change is additive V11.
- Do not include Day 6 security testing or fault injection.

---

### Task 1: Freeze the Day 5 contract and toolchain

**Files:**
- Create: `docs/design/week6-day5-performance-testing-design.md`
- Create: `performance/scripts/install-jmeter.ps1`
- Create: `performance/README.md`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: Day 4 Linux deployment and the existing `/api/audit-logs` ADMIN route.
- Produces: a checksum-verified `JMETER_HOME` outside Git and documented CLI commands.

- [ ] **Step 1: Record the exact official JMeter release URL and SHA-512 URL in the installer.**
- [ ] **Step 2: Make the installer download to a caller-supplied cache, verify SHA-512, extract atomically, and print only the executable path/version.**
- [ ] **Step 3: Add `.gitignore` rules for `performance/.tools/`, `performance/results/`, `*.jtl`, and temporary token/property files.**
- [ ] **Step 4: Run the installer twice and verify the second run is idempotent.**

### Task 2: Build repeatable synthetic data and query evidence

**Files:**
- Create: `performance/sql/seed-audit-log-performance-data.sql`
- Create: `performance/sql/explain-audit-log-query.sql`
- Create: `performance/sql/cleanup-audit-log-performance-data.sql`
- Create: `performance/scripts/invoke-performance-sql.ps1`

**Interfaces:**
- Consumes: MySQL container name/Compose project, existing ADMIN user ID, and V4/V10 constraints.
- Produces: exactly 50,000 `D5PERF_` jobs and audit rows plus before/after `EXPLAIN ANALYZE` output.

- [ ] **Step 1: Write seed SQL using a five-digit cross join and `D5PERF_` prefix; make duplicate execution abort before adding rows.**
- [ ] **Step 2: Write cleanup SQL that verifies its target prefix and deletes child audit rows before parent import jobs.**
- [ ] **Step 3: Write the explain script for the exact count and page SQL issued by the endpoint.**
- [ ] **Step 4: Execute seed, count, explain, cleanup, and residual-count checks in an isolated local Compose project.**

### Task 3: Build and validate the JMeter scenario

**Files:**
- Create: `performance/jmeter/audit-log-query.jmx`
- Create: `performance/scripts/run-audit-log-test.ps1`
- Create: `performance/scripts/summarize-jtl.ps1`
- Modify: `performance/README.md`

**Interfaces:**
- Consumes: `baseUrl`, in-memory ADMIN JWT, `threads`, `rampSeconds`, `durationSeconds`, and JMeter executable.
- Produces: timestamped JTL/HTML artifacts and a secret-free CSV/Markdown summary with sample count, throughput, errors, P50/P90/P95/P99/max.

- [ ] **Step 1: Create a CLI-safe JMX with HTTP defaults, Bearer header, 100–300 ms think time, status/body assertions, and no GUI listeners.**
- [ ] **Step 2: Write the runner to reject missing parameters, create a fresh results directory, invoke `jmeter -n -t ... -l ... -e -o ...`, and avoid echoing the token.**
- [ ] **Step 3: Write the summarizer to parse JTL CSV with literal percentile calculations and fail when errors exceed 1%.**
- [ ] **Step 4: Run a one-thread local smoke and inspect representative JTL/report output for leaked Authorization values.**

### Task 4: Capture the optimization-before baseline

**Files:**
- Create: `docs/review/week6-day5-performance-acceptance.md`

**Interfaces:**
- Consumes: retained ECS, SSH tunnel, 50,000-row fixture, JMeter levels, Prometheus API, and MySQL explain script.
- Produces: immutable before-run summaries and a written optimization decision.

- [ ] **Step 1: Record Git SHA/image digest, ECS/JMeter/Java versions, resources, service health, fixture counts, and baseline `EXPLAIN ANALYZE`.**
- [ ] **Step 2: Run smoke, warm-up, 10-, 25-, and 50-thread levels with stop-gate checks between levels.**
- [ ] **Step 3: Export JMeter summaries and Prometheus snapshots for HTTP/JVM/CPU/Hikari evidence.**
- [ ] **Step 4: Decide whether the V11 index gate is satisfied; record absolute measurements and caveats before editing production code.**

### Task 5: Implement the single evidence-backed optimization with TDD

**Files:**
- Modify: `src/test/java/com/finguard/core/DatabaseBaselineIntegrationTest.java`
- Create: `src/main/resources/db/migration/V11__add_audit_log_default_pagination_index.sql`

**Interfaces:**
- Consumes: before-run evidence proving the unfiltered audit page needs the default sort index.
- Produces: Flyway version 11 and `idx_audit_logs_created_id(created_at DESC, id DESC)` without changing API behavior.

- [ ] **Step 1: Add a database integration assertion for Flyway V11, exact index columns/order, and the unfiltered audit query plan.**
- [ ] **Step 2: Run the focused test and confirm RED because V11/index is absent.**
- [ ] **Step 3: Add the minimal V11 migration; do not edit V10.**
- [ ] **Step 4: Run the focused test and confirm GREEN, then run all migration/index-related tests.**

### Task 6: Re-test under identical conditions

**Files:**
- Modify: `docs/review/week6-day5-performance-acceptance.md`

**Interfaces:**
- Consumes: same ECS, data, JMX, load levels, tunnel, and rebuilt immutable application image containing V11.
- Produces: before/after table, calculation method, query-plan comparison, and regression evidence.

- [ ] **Step 1: Run complete Maven tests, build/publish the exact-SHA image through the existing workflow, and deploy it without deleting volumes.**
- [ ] **Step 2: Confirm Flyway V11 and the new index, then rerun `EXPLAIN ANALYZE`.**
- [ ] **Step 3: Repeat the same warm-up and 10/25/50-thread sequence and collect the same JMeter/Prometheus fields.**
- [ ] **Step 4: Calculate percentage changes as `(before - after) / before * 100` for latency and `(after - before) / before * 100` for throughput; report absolute values beside every percentage.**

### Task 7: Full acceptance, cleanup, documentation, and commit

**Files:**
- Modify: `TASKS.md`
- Modify: `README.md`
- Modify: `docs/review/week6-day5-performance-acceptance.md`

**Interfaces:**
- Consumes: all Day 5 artifacts and evidence.
- Produces: a clean, committed Day 5 milestone with no test data or secrets.

- [ ] **Step 1: Run `mvn -B -ntp clean test`, focused security/API smoke, Compose config checks, and fresh health/OpenAPI/JWT/RBAC/Prometheus smoke.**
- [ ] **Step 2: Remove only `D5PERF_` rows and local result/tool/token artifacts; verify zero fixture rows and unchanged Day 4 persistent data/volumes.**
- [ ] **Step 3: Update TASKS/README with measured values, limitations, exact files, learning points, and Day 6 boundary.**
- [ ] **Step 4: Run secret scan, `git diff --check`, scoped diff review, and verify port/tunnel/process cleanup.**
- [ ] **Step 5: Commit the scoped Day 5 changes with `perf: measure and optimize audit pagination`.**
