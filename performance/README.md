# FinGuard performance tests

Week 6 Day 5 measures the authenticated default audit-log page on a fixed 50,000-row synthetic fixture. The committed files contain only test definitions and scripts. Raw JTL files, HTML dashboards, JMeter binaries, JWTs, credentials, SSH details, and server addresses stay outside Git.

## Tool installation

Run from the repository root in PowerShell:

```powershell
$installOutput = & .\performance\scripts\install-jmeter.ps1
$installOutput
```

The installer downloads Apache JMeter 5.6.3 from the Apache distribution service, verifies the published SHA-512 digest, and extracts it under ignored `performance/.tools/`. Re-running it uses the verified installed copy.

Apache recommends CLI mode for actual load generation. Day 5 runners therefore use `jmeter -n` and generate CSV JTL plus the standard HTML dashboard; GUI listeners are not present in the committed JMX.

## Safety model

- Validate scripts in an isolated local Compose project before using the retained ECS.
- Reach the ECS application only through an SSH tunnel; do not expose application or monitoring ports publicly.
- Obtain an ADMIN JWT before the load stage and pass it in memory. Do not put login requests inside the repeated sampler.
- Stop escalation when errors exceed 1%, health is not `UP`, a container restarts/OOMs, CPU remains saturated, Hikari pending persists, or disk space becomes unsafe.
- Seed and cleanup only rows whose file names start with `D5PERF_`; never delete named volumes.

The complete workload, evidence fields, optimization gate, and cleanup requirements are defined in `docs/design/week6-day5-performance-testing-design.md`.
