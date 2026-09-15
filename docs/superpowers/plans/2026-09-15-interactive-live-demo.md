# FinGuard Core Interactive Live Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small same-origin browser demo that drives FinGuard Core's real API workflow and displays backend state changes step by step.

**Architecture:** Serve one dependency-free HTML page from Spring Boot at `/demo/index.html`. The page uses existing JWT-protected APIs, holds role tokens in memory, and creates uniquely named demo records in the current database after explicit acknowledgement. Only the demo page's GET path is public; business API permissions and business rules stay unchanged.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, Spring MVC static resources, JUnit 5, MockMvc, plain HTML/CSS/JavaScript, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-15-interactive-live-demo-design.md`

## Global Constraints

- Preserve Java 17 and add no frontend or backend dependency.
- Add no business endpoint, database table, migration, or risk-rule behavior.
- Permit only GET access to `/demo/**`; keep API authorization unchanged.
- Keep credentials out of source; clear password fields after login and hold JWTs only in page memory.
- Require acknowledgement before writing; the demo data is persistent and has no cleanup API.
- Do not automatically retry POST or PATCH calls.
- Poll asynchronous jobs every second for at most 90 seconds; show real responses only.
- Risk thresholds remain service startup configuration; the page adjusts transaction inputs only.
- Run write-flow browser checks only against an isolated disposable Compose project.

---

## File map

- Create `src/main/resources/static/demo/index.html`: all demo markup, responsive CSS, API client, in-memory state, and workflow controls.
- Modify `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`: allow anonymous GET for `/demo/**` only.
- Create `src/test/java/com/finguard/core/DemoStaticResourceSecurityIntegrationTest.java`: prove the static demo page is public while anonymous business API access remains protected.
- Modify `README.md`: make the live demo discoverable and link to its run instructions.
- Modify `docs/DEMO.md`: document the page URL, disposable-service requirements, ADMIN/REVIEWER login, persistent rows, and role switching.

## Task 1: Prove and open the static demo route

**Files:**
- Create: `src/test/java/com/finguard/core/DemoStaticResourceSecurityIntegrationTest.java`
- Create: `src/main/resources/static/demo/index.html`
- Modify: `src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java`

**Interfaces:**
- `GET /demo/index.html` returns `200` and `text/html` without authentication.
- `GET /api/accounts` remains `401 AUTHENTICATION_REQUIRED` without authentication.

- [x] **Step 1: Write the failing MockMvc test**

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoStaticResourceSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesDemoPageAnonymously() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML
                ))
                .andExpect(content().string(containsString("FinGuard Core")));
    }

    @Test
    void demoPageDoesNotRelaxBusinessApiAuthentication() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }
}
```

- [x] **Step 2: Run the focused test and observe the expected failure**

Run: `mvn -B -ntp -Dtest=DemoStaticResourceSecurityIntegrationTest test`

Expected: the static resource test fails because `/demo/index.html` is not public and does not exist yet.

- [x] **Step 3: Allow only the demo GET path and add an HTML shell**

Add `.requestMatchers(HttpMethod.GET, "/demo/**").permitAll()` before `.anyRequest().authenticated()` in `SecurityConfiguration`. Create the static page with a document title and visible `FinGuard Core` heading; do not add scripts that call APIs in this step.

- [x] **Step 4: Run the focused test and confirm both assertions pass**

Run: `mvn -B -ntp -Dtest=DemoStaticResourceSecurityIntegrationTest test`

Expected: anonymous page GET is `200`; anonymous account API GET is still `401` with `AUTHENTICATION_REQUIRED`.

## Task 2: Build the responsive shell, safe API client, and login state

**Files:**
- Modify: `src/main/resources/static/demo/index.html`
- Modify: `src/test/java/com/finguard/core/DemoStaticResourceSecurityIntegrationTest.java`

**Interfaces:**
- Define `request(path, options)` to send same-origin requests and throw `DemoApiError(status, code, message)` on non-2xx responses.
- Define `loginAs(role, username, password)` to call `POST /api/auth/login` and put the returned `accessToken` in the role's in-memory state.
- Define `render()` to update visible state using DOM text nodes/textContent.

- [x] **Step 1: Expand the static-resource test to assert the workflow shell exists**

Add a second assertion to `servesDemoPageAnonymously()`:

```java
.andExpect(content().string(allOf(
        containsString("ADMIN 登录"),
        containsString("REVIEWER 登录"),
        containsString("流程进度"),
        containsString("我已了解演示会写入当前数据库")
)));
```

Import `org.hamcrest.Matchers.allOf` and run the focused test first; it must fail because these labels do not exist yet.

- [x] **Step 2: Add semantic layout and responsive styles**

Build a narrow-screen-friendly page with a header/status badge, seven-stage rail, active stage panel, parameter panel, and event feed. Use `<label>` with associated form controls, native buttons, visible focus styles, and text for the data-retention warning.

- [x] **Step 3: Define the in-memory workflow state**

```javascript
const demoState = {
  adminToken: null,
  reviewerToken: null,
  runId: null,
  step: 0,
  account: null,
  manualTransaction: null,
  importJob: null,
  reconciliationJob: null,
  results: [],
  reviewTasks: [],
  events: [],
  busy: false
};
```

Keep transaction inputs in form controls. Do not copy passwords or tokens into `demoState.events`, DOM text, URLs, browser storage, or console output.

- [x] **Step 4: Implement `request(path, options)` and safe error rendering**

Use this request helper shape so JSON, multipart, and unauthenticated login requests share one boundary:

```javascript
class DemoApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request(path, {method = "GET", token = null, json, form} = {}) {
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (json !== undefined) headers.set("Content-Type", "application/json");
  const response = await fetch(path, {
    method,
    headers,
    credentials: "omit",
    body: form ?? (json === undefined ? undefined : JSON.stringify(json))
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new DemoApiError(
      response.status,
      payload?.code ?? `HTTP_${response.status}`,
      payload?.message ?? "Request failed"
    );
  }
  return payload;
}
```

Render `code` and `message` with `textContent`; never render stack traces, headers, or response bodies wholesale.

- [x] **Step 5: Implement role-specific login and clear passwords on success**

Call `POST /api/auth/login` with this input-derived JSON body:

```javascript
json: {
  username: usernameInput.value,
  password: passwordInput.value
}
```

Store only the returned token in memory, clear the matching password input on successful login, show the active role without displaying the token, and keep ADMIN/REVIEWER login actions separate.

- [x] **Step 6: Re-run the focused test and inspect the source for persistence leaks**

Run: `mvn -B -ntp -Dtest=DemoStaticResourceSecurityIntegrationTest test`

Inspect the static page and confirm it contains no `localStorage`, `sessionStorage`, token rendering, credential logging, or untrusted API values assigned to `innerHTML`.

## Task 3: Create demo data, generate CSV, and observe import states

**Files:**
- Modify: `src/main/resources/static/demo/index.html`

**Interfaces:**
- `toApiDateTime(date)` returns `YYYY-MM-DDTHH:mm:ss`; `toCsvDateTime(date)` returns `YYYY-MM-DD HH:mm:ss`.
- `buildCsv(runId, values)` returns a CSV `Blob` with a unique filename and two rows: one matching candidate and one adjustable large-amount candidate.
- `pollUntil(path, token, terminalStatuses, onStatus, timeoutMs = 90000)` makes GET requests only and reports distinct statuses actually returned.
- `createDemoData()` performs account and MANUAL transaction POSTs once each, preserving IDs from successful responses.
- `uploadCsv()` sends one multipart POST and then polls the returned import job ID.

- [x] **Step 1: Add scenario inputs and a write acknowledgement gate**

Provide editable manual/CSV match amounts, an integer CSV time offset limited to -4 through +4 days, and an editable risk-row amount. Default the manual timestamp to ten days before the current local time so a positive four-day CSV offset still passes the importer's five-minute future check. Disable all write buttons until the user acknowledges that rows persist in the current database. Add a `新建演示批次` action that creates a new run ID and clears per-run IDs/results while preserving the two in-memory login tokens; the separate clear-session action removes those tokens too.

- [x] **Step 2: Add unique run identifiers and create the account**

Generate a fresh alphanumeric `runId` for each run. Submit `POST /api/accounts` with only `accountNo`, `accountName`, and `accountType: "BANK"`; use the returned account ID. Use visible `DEMO-` prefixes for traceability.

- [x] **Step 3: Create the matching MANUAL transaction**

Submit one `POST /api/transactions` with the returned `accountId`, a run-specific `externalTransactionNo`, `direction: "INCOME"`, the configured manual amount, a local timestamp, and a short description. Persist the returned ID in memory and never issue a second POST automatically.

- [x] **Step 4: Generate the CSV from current controls**

Generate the matching row with the same account number and external transaction number as the MANUAL transaction, editable CSV amount, and a time offset from the manual timestamp. Generate a second `EXPENSE` row with a unique external transaction number and the editable risk amount. Escape CSV fields and include the required header names from `sample-data/demo-import.csv`.

- [x] **Step 5: Upload one file and show actual import transitions**

Append the generated `File` to `FormData` as `file`, send one `POST /api/import-jobs`, and store the returned job ID. Poll only with GET using this bounded loop:

```javascript
async function pollUntil(path, token, terminalStatuses, onStatus, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  let lastStatus = null;
  while (Date.now() < deadline) {
    const job = await request(path, {token});
    if (job.status !== lastStatus) {
      onStatus(job);
      lastStatus = job.status;
    }
    if (terminalStatuses.includes(job.status)) return job;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error("Polling timed out; last server state is still visible");
}
```

Poll `GET /api/import-jobs/{id}` every second until `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED`, with a 90-second limit. Record only endpoint, HTTP status, and distinct observed job statuses in the event feed.

- [x] **Step 6: Run the focused test and inspect the source for duplicate-write behavior**

Run: `mvn -B -ntp -Dtest=DemoStaticResourceSecurityIntegrationTest test`

Confirm each `POST` is reachable from one explicit stage action, buttons are disabled while in flight, and timeout offers a GET-only manual poll rather than repeating the upload.

## Task 4: Add reconciliation, review, audit, and statistics stages

**Files:**
- Modify: `src/main/resources/static/demo/index.html`

**Interfaces:**
- `createReconciliation()` sends `{ "importJobId": id }` once, polls its returned job ID, then fetches the first 100 result records.
- `loadRunReviewTasks()` queries `GET /api/review-tasks?status=PENDING&size=100` and displays pending tasks whose `csvTransactionId` appears in the current reconciliation results.
- `decideReview(task, decision)` sends `{ "decision": decision, "version": task.version, "note": "FinGuard live demo review" }` with the REVIEWER token.
- `loadRunSummary()` uses the ADMIN token to query the latest 100 audit records and statistics; display only audit entries whose import, reconciliation, or review IDs belong to this run.

- [x] **Step 1: Require successful import before creating reconciliation**

Enable the reconciliation action only after an import terminal state of `SUCCESS` or `PARTIAL_SUCCESS`. Send one `POST /api/reconciliation-jobs` with the import ID, then poll the returned ID every second until `COMPLETED` or `FAILED` for no more than 90 seconds.

- [x] **Step 2: Render the actual reconciliation result records**

Request `/api/reconciliation-jobs/{id}/results?page=1&size=100`. Render `resultType`, `matchMethod`, and `reasonCode` as text. Explain the selected inputs beside the response: exact values and time yield `MATCHED`; a same-key amount mismatch yields `SUSPICIOUS`; a same-key time offset beyond three days yields `SUSPICIOUS`. Do not compute or substitute the server result in JavaScript.

- [x] **Step 3: Load only review tasks tied to the current result IDs**

After `COMPLETED`, query pending tasks with size 100 and select tasks whose `csvTransactionId` is present in the current result records. Display the returned task status, rule/reason code, and version, and let the user choose `CONFIRMED` or `IGNORED`.

- [x] **Step 4: Submit one versioned decision as REVIEWER**

Require a REVIEWER token. PATCH `/api/review-tasks/{id}/decision` with the selected decision and current version. Display the returned terminal status and incremented version. On `409 REVIEW_VERSION_CONFLICT`, stop and ask the user to reload the task; do not retry the PATCH.

- [x] **Step 5: Show ADMIN audit and aggregate statistics**

Use the ADMIN token for `GET /api/audit-logs?page=1&size=100` and `GET /api/statistics/overview`. Filter audit entries client-side by the current import, reconciliation, and review IDs; label statistics as aggregate for the connected database rather than data for only the demo run.

- [x] **Step 6: Add clear-session, safe progress, and bounded polling behavior**

Clear-session drops tokens, run IDs, IDs, and event data from page memory but does not claim to remove database rows. On 401/403/409, validation errors, network failures, or polling timeout, stop the dependent stages and show a concise message using `textContent`. Keep the last accepted server IDs visible so the user can inspect them through Swagger.

## Task 5: Document discovery, setup, and database effects

**Files:**
- Modify: `README.md`
- Modify: `docs/DEMO.md`

- [x] **Step 1: Add a README link to the live page and setup guide**

Add the demo link near the existing demo documentation entry and point to `http://127.0.0.1:8080/demo/index.html` after the six-service stack is healthy.

- [x] **Step 2: Document the required role credentials and real API behavior**

Explain that the user must provision ADMIN and REVIEWER credentials through the existing bootstrap configuration or existing auth setup, that the page performs real writes, and that the user switches roles with separate logins.

- [x] **Step 3: Document persistence and supported parameter controls**

State that demo accounts, transactions, import jobs, reconciliation results, review decisions, and audit rows persist. Instruct the user to use a disposable/local database. Explain that changing the current risk threshold requires an application restart; the page only varies transaction inputs.

- [x] **Step 4: Verify links and formatting**

Run `git diff --check`. Confirm the README link targets the page and `docs/DEMO.md` still documents the existing Swagger/acceptance-script demo.

## Task 6: Verify isolated live behavior and close the change

**Files:**
- Verify: `src/test/java/com/finguard/core/DemoStaticResourceSecurityIntegrationTest.java`
- Verify: `src/main/resources/static/demo/index.html`
- Verify: `README.md`, `docs/DEMO.md`

- [x] **Step 1: Run the focused static/security integration test**

Run: `mvn -B -ntp -Dtest=DemoStaticResourceSecurityIntegrationTest test`

Expected: public demo page is `200`; protected anonymous account API stays `401`.
Final result: 4 tests passed, 0 failures/errors/skips.

- [x] **Step 2: Run the full Java regression suite**

Run: `mvn -B -ntp clean test`

Expected: `BUILD SUCCESS`, no new failures, errors, or skipped tests.
Final result after the last source change: 383 tests passed, 0 failures/errors/skips; `BUILD SUCCESS`.

- [x] **Step 3: Start a temporary isolated six-service Compose project**

Create the temporary env file outside the repository without printing secrets. Keep the same PowerShell session open through setup, browser validation, and teardown so the generated path/project values and bootstrap passwords remain in memory. Use distinct random values for the database/broker/cache/Grafana passwords, separate bootstrap passwords, and a 32-byte JWT secret:

```powershell
function New-RandomBase64([int]$ByteCount) {
    $randomBytes = New-Object byte[] $ByteCount
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $randomGenerator.GetBytes($randomBytes) }
    finally { $randomGenerator.Dispose() }
    return [Convert]::ToBase64String($randomBytes)
}

$demoProjectName = "finguard-live-demo-" + [Guid]::NewGuid().ToString("N").Substring(0, 8)
$demoEnvPath = Join-Path $env:TEMP ($demoProjectName + ".env")
$demoMysqlRootPassword = New-RandomBase64 24
$demoMysqlPassword = New-RandomBase64 24
$demoRabbitPassword = New-RandomBase64 24
$demoRedisPassword = New-RandomBase64 24
$demoGrafanaPassword = New-RandomBase64 24
$demoAdminPassword = New-RandomBase64 24
$demoReviewerPassword = New-RandomBase64 24
$demoJwtSecret = New-RandomBase64 32
$demoEnvLines = @(
    "MYSQL_ROOT_PASSWORD=$demoMysqlRootPassword"
    "MYSQL_DATABASE=finguard_live_demo"
    "MYSQL_USER=finguard"
    "MYSQL_PASSWORD=$demoMysqlPassword"
    "MYSQL_PORT=23306"
    "RABBITMQ_USERNAME=finguard"
    "RABBITMQ_PASSWORD=$demoRabbitPassword"
    "RABBITMQ_PORT=25672"
    "RABBITMQ_MANAGEMENT_PORT=25673"
    "REDIS_PASSWORD=$demoRedisPassword"
    "REDIS_PORT=26379"
    "JWT_SECRET_BASE64=$demoJwtSecret"
    "FINGUARD_APP_PORT=28080"
    "FINGUARD_CONTAINER_PREFIX=$demoProjectName"
    "FINGUARD_APP_IMAGE=finguard-core:$demoProjectName"
    "PROMETHEUS_PORT=29090"
    "GRAFANA_PORT=23000"
    "GRAFANA_ADMIN_USER=admin"
    "GRAFANA_ADMIN_PASSWORD=$demoGrafanaPassword"
    "FINGUARD_AUTH_BOOTSTRAP_ENABLED=true"
    "FINGUARD_AUTH_BOOTSTRAP_ADMIN_USERNAME=demo-admin"
    "FINGUARD_AUTH_BOOTSTRAP_ADMIN_PASSWORD=$demoAdminPassword"
    "FINGUARD_AUTH_BOOTSTRAP_REVIEWER_USERNAME=demo-reviewer"
    "FINGUARD_AUTH_BOOTSTRAP_REVIEWER_PASSWORD=$demoReviewerPassword"
    "FINGUARD_RISK_LARGE_AMOUNT_THRESHOLD=10000.00"
)
[IO.File]::WriteAllLines($demoEnvPath, $demoEnvLines, [Text.UTF8Encoding]::new($false))
try {
    docker compose --project-name $demoProjectName --env-file $demoEnvPath up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw "Compose startup failed" }
    Write-Output "Isolated app is ready at http://127.0.0.1:28080/demo/index.html"
    [void](Read-Host "Complete browser steps 4-6, then press Enter for automatic cleanup")
}
finally {
    try {
        docker compose --project-name $demoProjectName --env-file $demoEnvPath down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { throw "Compose cleanup failed" }
        $demoImage = docker image ls --quiet "finguard-core:$demoProjectName"
        if ($demoImage) { docker image rm "finguard-core:$demoProjectName" }
    }
    finally {
        Remove-Item -LiteralPath $demoEnvPath
    }
}
```

Run this block in a persistent interactive PowerShell terminal. Use `$demoAdminPassword` and `$demoReviewerPassword` only in the isolated browser session through variable-fed browser automation; never print the env file or passwords. The `finally` block tears down the unique project and removes the exact image/env file after browser steps 4-6 or a failure. Check the selected host ports are free before starting. Do not use the normal development Compose project or its `.env`.
Observed: isolated project `finguard-live-demo-228ef310` reached healthy state on its assigned ports. The normal development Compose project was not modified.

- [x] **Step 4: Complete a real browser run against the isolated app**

Verify successful ADMIN login, unique account and MANUAL transaction creation, import accepted and terminal response, reconciliation completed with actual result records, REVIEWER versioned decision, and ADMIN audit/statistics. Confirm the event feed shows API/status changes without credentials or tokens.
Observed in the real browser: account and MANUAL transaction created (201); CSV accepted (202) and reached `SUCCESS`; reconciliation completed with actual `MATCHED/EXACT` and large-risk result records; REVIEWER decision returned 200 and version 1; ADMIN audit/statistics returned 200 and the current run showed 3 audit entries.

- [x] **Step 5: Exercise adjustable reconciliation and risk inputs**

Use `新建演示批次` without clearing the two role tokens to start three additional uniquely identified runs: equal amount with zero-day offset returns the exact match; changed amount with the same business key returns `SUSPICIOUS/AMOUNT_MISMATCH`; equal amount with a four-day offset returns `SUSPICIOUS/TIME_OUT_OF_RANGE`. Set the risk row to `10000.00` and confirm the real server creates a `LARGE_AMOUNT` pending review task under the acceptance threshold.
Observed: three additional real runs returned `MATCHED/EXACT`, `SUSPICIOUS/AMOUNT_MISMATCH`, and `SUSPICIOUS/TIME_OUT_OF_RANGE`; the `10000.00` risk row produced a pending `LARGE_AMOUNT` review task. A final fresh default run also returned `MATCHED/EXACT` and a pending large-amount task.

- [x] **Step 6: Verify failure and credential boundaries**

Confirm unacknowledged writes are disabled; wrong-role or invalid credentials show API errors; refresh clears in-memory tokens; no page source stores tokens/passwords or logs request data; job timeout issues only GET; a version conflict does not repeat the PATCH.
Observed: unacknowledged writes stayed disabled; invalid login rendered HTTP 401 `INVALID_CREDENTIALS`; refresh cleared tokens and password fields; browser storage remained empty; bounded polling and conflict simulation each issued only GET polling / one PATCH respectively. A mocked import-poll 401 made exactly one GET, preserved HTTP status and API code, hid timeout requery, and kept dependent writes disabled. Static scan found no storage, credential logging, HTML injection, or dynamic-evaluation sinks.

- [x] **Step 7: Tear down only the isolated Compose project and verify cleanup**

Press Enter in the persistent terminal from Step 3 to run its `finally` cleanup. Verify the project named by `$demoProjectName` has no remaining containers, networks, or volumes, and confirm the exact image tag and temporary env file were removed. Leave normal development services and volumes untouched.
Observed: the exact isolated project containers, networks, and volumes were removed, as were its tagged image and temporary env file. Normal development services remained untouched; no Playwright browser session or generated artifact remains.

- [x] **Step 8: Run final repository checks**

Run `git diff --check`, inspect `git diff --stat` and the full diff, confirm implementation changes are limited to the five file-map paths and documentation changes are limited to the approved spec/plan, check that no secret or generated demo data entered the repository, and verify `git status --short --branch`.
Additional verification: responsive layouts at 320, 390, 720, 721, 1024, and 1280 pixels had no horizontal overflow; final whole-branch review passed with no findings. Final repository scope, whitespace, generated-file, and secret checks are recorded before commit.

- [x] **Step 9: Commit the verified feature**

```powershell
git add README.md docs/DEMO.md docs/superpowers/specs/2026-09-15-interactive-live-demo-design.md docs/superpowers/plans/2026-09-15-interactive-live-demo.md src/main/java/com/finguard/core/auth/config/SecurityConfiguration.java src/main/resources/static/demo/index.html src/test/java/com/finguard/core/DemoStaticResourceSecurityIntegrationTest.java
git commit -m "feat: add interactive live FinGuard demo"
```

Completed after the verified Task 6 acceptance and final staged-diff review.

---

## Self-review

- **Spec coverage:** static hosting, GET-only security, actual API workflow, editable transaction inputs, asynchronous polling, role-specific review, safe token handling, persistent-data acknowledgement, docs, full Maven verification, isolated live browser validation, and cleanup are represented above.
- **Detail scan:** each task names exact files, API routes/contracts, test commands, and expected outcomes. `$demoEnvPath` and `$demoProjectName` are initialized by the PowerShell setup step and passed unchanged to the corresponding Compose commands.
- **Type consistency:** the page uses existing JSON contracts: login `accessToken`; account `id`; transaction `accountId`, `externalTransactionNo`, `direction`, `amount`, `transactionTime`; import/reconciliation `id` and `status`; review `id`, `version`, and `status`; paginated results under `records`.
- **Scope:** all planned source changes serve one static demo flow. No new service, dependency, API, migration, or cleanup mechanism is introduced.
