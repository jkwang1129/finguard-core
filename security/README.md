# Day 6 security verification

This directory contains reproducible wrappers and policy files for Week 6 Day 6. Raw reports, vulnerability databases, image archives and temporary credentials stay under ignored `security/results/` or `security/.cache/`.

## Static repository and image scan

Build the current application image first, then run:

```powershell
& security/scripts/run-static-security-checks.ps1 `
  -RepositoryRoot (Get-Location).Path `
  -ImageReference 'finguard-core:day6'
```

The wrapper uses `aquasec/trivy:0.72.0` and writes JSON only to the ignored results directory. It scans the repository for vulnerabilities, misconfiguration and secrets, then scans the exported image for OS-package vulnerabilities plus configuration and secret findings. The image scan skips embedded JAR dependency analysis because the Day 6 offline run does not retain Trivy's separate Java database; the repository/POM scan, Maven dependency resolution and full regression provide complementary evidence, but do not replace a future SBOM-aware SCA run. Scanner results require source-backed triage before they are treated as findings.

## Passive web baseline

Start the isolated application on a loopback port and run:

```powershell
& security/scripts/run-zap-baseline.ps1 `
  -TargetUrl 'http://host.docker.internal:18080'
```

The wrapper uses `ghcr.io/zaproxy/zaproxy:2.17.0` and only invokes `zap-baseline.py`. It does not invoke ZAP full scan or API active scan. Exit code 2 means passive warnings were produced for triage; exit codes 1 and 3 stop the wrapper.

## Tool tests

```powershell
& security/tests/test-security-tools.ps1
```

Dry-run output contains tool names and command boundaries only. Passwords and JWTs are never accepted as command-line parameters.
