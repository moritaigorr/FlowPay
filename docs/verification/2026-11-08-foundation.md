# Foundation verification — 2026-11-08

Scope: R00–R04 local implementation, on the dedicated codex/flowpay-implementation branch. This is a personal simulator foundation, not a working payment processor or production deployment. R02/R03 source baseline:7d7ac83; R04 integration changes were verified before their final reviewed commit.

Environment observed: Windows 11, Temurin 21.0.13, Maven 3.9.16, Node 24.18.0/npm 11.16.0, Docker engine 29.6.2/Compose 5.3.1, PostgreSQL 18.6, Chrome 156.0.8078.81. Container builds also compiled the application on Linux. Exact dependencies/images are recorded in [dependencies](../development/dependencies.md).

| Check | Result and evidence |
|---|---|
| Maintained OpenAPI | npm run validate:api passed with no warnings;17 specified operations checked |
| Java foundation | Maven Wrapper verify passed 11 tests, 0 failures/errors/skips: 1 architecture, 6 schema/session, 4 documentation/security |
| Real database | PostgreSQL Testcontainers validated tenant composite FKs, amount/enum constraints, idempotency uniqueness, event references and actual Spring Session persistence |
| Frontend | Lint, typecheck, 7 behavioral tests and production build passed; route regression first failed on four assertions, then passed |
| Browser shell |375/768/1440 viewports: title/content, no page overflow, keyboard/skip link, heading focus/current route,404 recovery, case/trailing route variants, reduced motion, no console/page errors |
| Dependency check | Initial minimatch lint advisory resolved through scoped11.0.1 override; subsequent npm audit reported0 findings, including production-only audit. Not a complete security audit |
| Build/runtime | Both multistage images built; Compose reports database/backend/frontend healthy. Java runs as UID10001; only127.0.0.1:8080 is published |
| Packaged/proxied docs | GET /api/openapi.yaml returns application/yaml and canonical bytes; Swagger assets load, UI renders, execution disabled, no external browser requests |
| Closed business API | /api/v1/auth/me and unknown API path return401 problem JSON instead of SPA HTML; missing-CSRF writes return403 |
| Proxy limits | Login/demo repeated missing-CSRF requests reach429 with Retry-After; no demo/company/authenticated business write is performed by the smoke |
| Restart persistence | Inserted a uniquely identified synthetic company marker, restarted only the project database, read the original marker, then deleted exactly that test row. Named volume retained |
| Local config | Random password generated only in ignored.env; Windows PowerShell5.1 rerun preserved its existing contents |
| CI | Definition added with fixed official action SHAs and actual Java/frontend/OpenAPI commands. NOT RUN remotely |

R04 found a real packaged-documentation defect: static YAML was returned as application/octet-stream. Direct backend and proxy responses agreed, locating the cause in Java static resource MIME handling. The new Content-Type assertion failed before correction. A dedicated controller now returns the same packaged canonical resource with application/yaml. Full11-test verify and the actual Compose smoke passed after rebuilding Java.

## Reproduction

Follow [local-development.md](../development/local-development.md). Core commands, from the appropriate directories:

```text
root: npm ci; npm run validate:api
frontend: npm ci; npm run lint; npm run typecheck; npm test; npm run build
backend with Java21/Docker: mvnw.cmd -B verify (Windows), ./mvnw -B verify (Linux)
root with ignored.env configured: docker compose up --build --detach --wait
root: npm run check:local
```

The initial Docker build skips Java tests inside its packaging stage; the separate wrapper verify executes them. Container build success alone is not a test result. The HTTP smoke only targets loopback and documents its foundation-specific assertions. It does not authenticate, create payments or demonstrate business permissions.

Browser method: integrated CUA browser acquisition was unavailable/blocked by its URL policy check. Used a fresh isolated Playwright/Chrome context against the real local server, then the production Compose origin. Child sandbox Vite was unreachable from the outside browser; coordinator started the server with the required process/socket permission. No personal browser profile was automated. Screenshots: [production shell](../design/implementation/shell-compose-1440.png), [mobile shell](../design/implementation/shell-compose-375.png), [read-only docs](../design/implementation/docs-compose-1440.png).

## Limits

Authentication/session lifecycle, customers, payments, original-response replay, concurrent transitions, dashboard metrics and isolated demo provisioning are not implemented. Those require R05+ checks. Native Chrome200%zoom was not exercised: the existing720CSSpx/DPR2 screenshot is reflow equivalence only. Firefox/Safari, Lighthouse, k6, remote CI and public deployment are unverified. No throughput, latency, uptime, revenue or scale claim is made.

Docker Desktop's internal dockerInference startup issue was recovered by preserving/recreating only its runtime directory after a graceful stop. Images, volumes and settings were retained; recurrence is possible and is separate from application correctness.
