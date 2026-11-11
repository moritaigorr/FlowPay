# Deployment status and future requirements

**There is no public FlowPay deployment, public demo URL or production host.** The verified run target is the local Docker Compose environment bound to loopback. FlowPay is a personal simulation; it does not process real payments or store financial credentials. This document describes the current boundary and a provider-neutral checklist for a future, separately authorized deployment. It is not an executable production recipe.

## Current local environment

`compose.yaml` runs PostgreSQL, the Java API, a static React frontend behind Nginx, and an optional one-shot bootstrap service. Only the proxy is published, bound to `127.0.0.1:8080`; database and backend ports are not published. `.env` is local and ignored. The demo enters through the API, uses synthetic isolated tenant data, expires after 24 hours and is cleaned up. The API docs are read-only.

Start it with Docker Desktop's Linux engine:

```powershell
.\scripts\setup-local.ps1
docker compose up --build --detach --wait
```

`docker compose down` stops/removes the local containers and network while retaining the named PostgreSQL volume. The local setup is not equivalent to a publicly reachable deployment. The `prod` application profile requires Secure session cookies over HTTPS, but this repository has no deployed TLS endpoint and that end-to-end behavior has not been verified.

## Before any public demo

Treat the following as required design/verification work, not completed configuration:

- **Authorization and owner approval:** agree on a public demo's purpose, retention, quotas, abuse controls and privacy statement before exposing signup or the demo endpoint. Conventional signup and payment-provider integrations remain out of scope.
- **HTTPS and origin:** deploy behind managed TLS; verify redirect/HSTS policy, secure cookies, CSRF, same-origin proxy behavior, CORS denial and host validation against the real domain.
- **Secrets:** issue strong secrets via the host's secret manager/environment injection; never bake `.env` or bootstrap credentials into an image, repository, build log or browser bundle. Confirm no local bootstrap path is enabled in a public runtime.
- **Database:** use supported PostgreSQL 18, private networking and least-privilege credentials; define backups, restore drills, migration failure/recovery and demo-data retention before accepting visitors.
- **Abuse and resource controls:** configure and test proxy limits at the public edge, global demo admission, per-company quotas, request/connection limits, and safe behavior when capacity is reached. Local values are not public capacity guarantees.
- **Observability and operations:** retain request IDs and privacy-safe logs; define health/readiness checks, alerting, patching, rollback, incident response and an owner for data deletion requests. Do not log passwords, cookies or whole personal-data request bodies.
- **Build and release:** run backend PostgreSQL integration tests, frontend lint/typecheck/tests/build and browser journeys in CI; pin reviewed image/tool versions; scan dependency/image updates; deploy an immutable artifact with a documented rollback.
- **Performance and acceptance:** rerun the documented k6 and Lighthouse scenarios in a production-like isolated staging environment with resource allocation recorded. Establish budgets only after repeated measurements. Verify access isolation, CSRF, expiry/cleanup and restore/release procedures under HTTPS.
- **Product truthfulness:** keep the “simulation only / no real money” disclosure visible. Do not present local lab metrics as production uptime, user traffic, capacity or financial processing results.

Do not provision paid infrastructure, publish a repository, create public accounts or deploy based on this checklist. Those require a separate explicit product/release decision.

## Evidence boundary

Local setup and commands are in [local development](development/local-development.md). The configured GitHub Actions workflow has not been run remotely. R20 k6 and R21 Lighthouse measurements are local lab evidence in [API](performance/api-baseline.md) and [frontend](performance/frontend-baseline.md) reports; neither is a production or service-level guarantee.
