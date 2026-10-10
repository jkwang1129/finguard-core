# FinGuard Core Interactive Live Demo Design

**Status:** Design approved; implementation verified
**Date:** 2026-09-15

## Goal

Add a small, clickable browser demo that shows FinGuard Core's actual transaction-import, reconciliation, review, and audit workflow. The user can adjust business inputs, execute one step at a time, and see state changes returned by the running Spring Boot service.

## Non-goals

- Do not add or change business APIs, database tables, migrations, dependencies, or risk-rule behavior.
- Do not simulate server outcomes or claim that local UI state proves a backend state transition.
- Do not add a general-purpose administration console, account cleanup endpoint, or support for production use.
- Do not expose or change the effective risk threshold through the demo. It remains a server startup configuration value.

## Options and decision

1. **Spring-served same-origin static page (chosen):** add a dependency-free page to Spring Boot's static resources and call the existing APIs. This avoids CORS and a second server, and keeps the demo close to the real system.
2. **Separate frontend development server:** allows a larger frontend structure, but adds a runtime/build tool and CORS configuration for a small demo.
3. **Backend orchestration endpoint:** could wrap the sequence, but expands the secured business API and would obscure the individual stages this demo should teach.

The chosen page is served at `/demo/index.html`. A narrow GET security rule will allow the static demo path; existing API authorization remains unchanged.

## User experience

Use a compact operations-console layout: a vertical stage rail, an active stage workspace, a parameter panel, and a chronological event feed. Each stage has a single-step action. An optional action may advance to the next observed backend state, but it must poll and display actual responses rather than invent intermediate results. Disable duplicate clicks while a request is in flight.

The guided stages are:

1. Sign in as ADMIN.
2. Configure the run's matching and risk transaction values.
3. Create a uniquely named demo account and matching MANUAL transaction.
4. Generate and upload a run-specific CSV; poll and display import states until a terminal result.
5. Create an asynchronous reconciliation job; poll it and show its result types, methods, and reason codes.
6. Sign in as REVIEWER, load the pending task, and submit `CONFIRMED` or `IGNORED` with the returned version.
7. Sign in as ADMIN and show the audit entries and statistics returned for the run.

The parameter panel controls the manual/CSV matching amount, CSV time offset, and high-risk CSV amount. With the same account and external transaction number, the UI can demonstrate exact amount/time matching, a strong-key amount mismatch, and the fixed three-day time tolerance. It can also vary the high-risk amount and show whether the running server creates a risk hit. Because the risk threshold is configured at service startup and has no update API, the UI must not label an editable value as the server threshold or promise a hit for a particular amount.

The event feed records the stage, endpoint, HTTP status, and returned job/task state. It must not display credentials, JWTs, request authorization headers, or full response bodies.

## API flow

Use the existing routes documented in `docs/API.md`:

- `POST /api/auth/login`
- `POST /api/accounts`
- `POST /api/transactions`
- `POST /api/import-jobs`, then `GET /api/import-jobs/{id}`
- `POST /api/reconciliation-jobs`, then `GET /api/reconciliation-jobs/{id}` and `GET /api/reconciliation-jobs/{id}/results`
- `GET /api/review-tasks`, `PATCH /api/review-tasks/{id}/decision`
- `GET /api/audit-logs` and `GET /api/statistics/overview`

Create one account per run. Use a fresh random run suffix in its account number, transaction numbers, CSV content, and filename so database uniqueness and file-hash idempotency do not collide with prior demo runs. Build the CSV in the browser from the editable scenario values. The account creation request must follow the current DTO (`accountNo`, `accountName`, `accountType`); do not copy stale fields from older examples.

For each accepted asynchronous write, retain the returned ID and poll its GET route every second for at most 90 seconds. Display every distinct status received. At timeout, leave the last observed state visible and offer a safe manual poll; do not repeat the POST.

## Credentials, persistence, and failure behavior

- The user supplies ADMIN and REVIEWER credentials. There are no demo credentials in source code.
- Clear each password field after successful login. Keep JWTs only in page memory; do not use local/session storage, cookies, URL parameters, or console output. Refreshing the page ends the UI session.
- Before the first write, require acknowledgement that this run adds persistent records to the currently connected database and the page cannot delete them. Use only a disposable/local environment, never a shared or production database.
- Use unique `DEMO-*` names for traceability. The demo does not claim to clean up its account, imported transactions, review tasks, or audit records.
- Do not automatically retry POST/PATCH requests. Keep server error codes and a concise safe message visible; stop the flow on 401, 403, 409, validation failures, or polling timeout. Never render stack traces or tokens.
- Clear-session affects browser memory only and is explicitly distinct from database cleanup.

## Scope of changes

- Add `src/main/resources/static/demo/index.html` with inline CSS and JavaScript, without a frontend dependency or separate build.
- Permit GET access to `/demo/**` in `SecurityConfiguration`; leave API authorization matchers unchanged.
- Link to the page from `README.md` and update `docs/DEMO.md` with startup, credentials, persistence, and role-switch instructions.
- Add a focused web/security integration test proving the anonymous static demo page is served while protected business APIs remain protected.

## Verification

1. Run the focused security/static-resource test and full `mvn -B -ntp clean test`.
2. Start an isolated six-service environment using the repository's Day 7 acceptance tooling; do not run the write flow against a shared development database.
3. In a browser, complete a full demo run with ADMIN and REVIEWER credentials. Verify account creation, upload acceptance and terminal state, reconciliation terminal state/results, versioned review decision, audit output, and statistics against API responses.
4. Exercise editable inputs that produce a match, a mismatch/out-of-tolerance result, and the risk rule's actual server response. Confirm the UI reports configuration-dependent risk behavior accurately.
5. Verify token/password redaction, the persistent-data acknowledgement, error stopping behavior, and isolated-environment cleanup. Run `git diff --check` and audit scope before committing implementation.

## Known limitation

Risk thresholds are environment configuration, not a runtime API. The demo can vary transaction inputs and show actual rule outcomes, but changing the active threshold requires restarting the service with a different environment value.
