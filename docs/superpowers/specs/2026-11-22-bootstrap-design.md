# FlowPay bootstrap and development standard

**Status:** bootstrap implemented; backend and database startup still need an environment with Java 21 and Docker Compose.
**Date:** 2026-11-22

## Intent

Turn the documentation-only FlowPay repository into a reproducible local project. Establish the smallest useful backend, frontend, PostgreSQL, and CI foundation so later product work can proceed in short, demonstrable slices.

The project owner also requested two standing rules: the assistant should assess requests independently and explain evidence-backed disagreement; development should use a consistent, fast process without unnecessary ceremony.

## Goals

- A new contributor can follow the README on a clean machine and start the local stack using the documented runtimes and commands.
- Backend and frontend have runnable development and production-build paths; the backend exposes a health response without configuration or sensitive data.
- Local PostgreSQL uses development-only credentials and is reachable only through local configuration.
- CI checks the same fast, repeatable build and static validation commands documented for local use.
- Project guidance describes independent judgment and a lightweight standard sized to change risk.

## Non-goals

- No login, membership, financial CRUD, CSV, reconciliation, reports, or payment sandbox in this bootstrap.
- No production deployment, real credentials, real financial/person data, external integrations, or cloud infrastructure.
- No microservices, broker, Redis, Kubernetes, or speculative module scaffolding.
- No requirement to use agents, write an ADR, or run a full E2E suite for every small change.

## Approaches considered

1. **Backend and frontend bootstrap with local PostgreSQL (recommended and selected by the owner):** one repository, one Spring Boot service, one React SPA, Compose for PostgreSQL, and basic CI. This matches the decided stack and gives a complete local foundation without implementing product rules prematurely.
2. Backend-only bootstrap: less setup initially, but delays the selected SPA and makes API/UI integration a later source of setup churn.
3. Implement the whole MVP in one pass: rejected because it combines unresolved financial, identity, CSV, and reconciliation contracts and makes failures harder to isolate.

## Proposed implementation

### Repository and runtime

- Keep the existing docs, agent profiles, skills, and validator intact.
- Add `backend/`, `frontend/`, `.github/workflows/`, root `.gitignore`, `.env.example`, and a root README only as needed for executable setup.
- Backend: Java 21, Maven 3.10.1, Spring Boot **4.2.0** as the current stable line. Use Spring Web, Validation, Data JPA, PostgreSQL, Flyway, and Actuator only where needed for the bootstrap. Do not implement authentication in this increment. Expose only health endpoints and keep details/configuration private. A Maven Wrapper is omitted from the first slice because the environment lacks Maven and Java 21; use the documented Maven prerequisite and reconsider a wrapper after a Java 21 toolchain is available.
- Frontend: React + TypeScript + Vite, Node.js **26 LTS** and npm with a committed `package-lock.json`. Enable strict TypeScript. Add routing, query, forms, and UI libraries when the first real screen needs them instead of scaffolding unused features.
- Local database: PostgreSQL **18.7** using the pinned official image `postgres:18.7-alpine3.23`, Compose health check, named local volume at `/var/lib/postgresql` (the official PostgreSQL 18 image mount point), configurable host port, and development-only credentials. PostgreSQL 19 was released on 2026-11-19; use the established 18.7 line for initial compatibility and revisit the major upgrade separately ([version policy](https://www.postgresql.org/support/versioning/), [18.7 release](https://www.postgresql.org/about/news/postgresql-187-1712-1616-1520-1425-and-19-rc-2-released-3442/), [official image volume change](https://github.com/docker-library/docs/blob/master/postgres/content.md#pgdata)). Keep actual `.env` files ignored.
- CI: GitHub Actions with Java 21 and Node 26, Maven, `npm ci`, frontend typecheck/build and backend verification. Start PostgreSQL in CI only when a real integration check requires it; do not report database coverage without such evidence.
- README: document prerequisites, copy/configure local env, start/stop database and apps, and the exact commands available. Include Windows PowerShell-friendly commands alongside portable npm/Maven commands where they differ.

Version basis checked on 2026-11-22: Spring Boot 4.2.0 is available and supports Java 21 ([release](https://spring.io/blog/2026/11/19/spring-boot-4-2-0-available-now/), [requirements](https://docs.spring.io/spring-boot/system-requirements.html)); Node 26 is LTS and 26.13.1 is a current LTS release ([schedule](https://nodejs.org/en/about/previous-releases), [release announcement](https://nodejs.org/en/blog/release/v26.13.0)); Vite 8.3 is the current supported line ([Vite releases](https://vite.dev/releases.html)). Recheck supported patch versions when implementing the bootstrap; use security-patched patch releases without changing the selected major line.

### Independent judgment

Add to `AGENTS.md` and the agent guide:

- Treat the owner's intent as the goal, but assess whether a requested approach fits the documented architecture, product constraints, evidence, and risks.
- Do not agree by default. If a request conflicts with a constraint or creates meaningful risk, explain the concrete reason and evidence, then recommend a workable option.
- Distinguish documented requirements from implementation decisions and uncertainty. Ask only when a missing answer blocks a materially different or risky outcome; otherwise state a sensible, reversible assumption and proceed.

### Lightweight development standard

For each delivery, inspect the current implementation, make one small vertical change, and finish with a short report of what changed and what was actually checked.

- Small/reversible change: implement directly; no separate plan or ADR. Check the changed behavior with the fastest relevant existing command, unless the user asks for documentation-only work or no tests.
- Product or financial behavior: define the visible acceptance condition and affected invariant first; add/run the narrowest meaningful tests when authorized and available.
- High-risk or durable architecture change: compare options and record an ADR only when the decision will constrain future work or is costly to reverse.
- Use agents only when independent work can proceed in parallel with clear file/contract boundaries. Do not delegate a small task or work on shared files concurrently.
- Definition of done: requested behavior is implemented; relevant checks and limitations are reported; docs/commands are updated when they changed. Do not require unrelated audits, broad coverage targets, or full E2E for every change.

This standard does not weaken the required financial invariants, tenant isolation, authorization, or secure handling of data. Validation depth follows risk.

## Acceptance criteria

1. A clean checkout can build backend and frontend using the documented Maven/Node runtimes and committed npm lockfile.
2. Compose starts a healthy local PostgreSQL instance with no dependency on a pre-existing database and no production credentials.
3. Backend starts and returns a minimal health response; the response exposes no environment, secrets, PII, or database credentials.
4. Frontend starts and produces a production build; TypeScript strict checks run through a documented command.
5. CI invokes the committed backend/frontend commands and fails when one fails.
6. `.gitignore` excludes local env files, build artifacts, logs, and local database data; `.env.example` contains placeholders only.
7. `AGENTS.md` and `docs/DEVELOPMENT_AGENTS.md` state the judgment rule and lightweight development standard consistently.
8. Existing docs, skills, agent profiles, and local validator continue to pass their structural/reference checks.

## Open implementation details

The generated starter layout and compatible dependency patch versions are implementation choices. Keep them pinned and documented after checking current official releases. Do not decide product contracts (roles, money transport, opening balance semantics, tenant timezone, CSV dialect, or idempotency conflict response) in the bootstrap; resolve each before its dependent feature.

## Implementation evidence and limits

- Frontend was generated from the official Vite React/TypeScript starter; dependencies are recorded in `frontend/package-lock.json`.
- Local database credentials are isolated to the Spring `local` profile; the default profile requires explicit datasource environment variables.
- `npm ci`, `npm run typecheck`, `npm run lint`, and `npm run build` passed using Node.js 24.18.0 and npm 11.16.0. A local HTTP smoke check returned 200 and confirmed the UTF-8 page title.
- `mvn -f backend/pom.xml verify` passed with Maven 3.10.1 and the JDK 25 found in `JAVA_HOME`; Maven compiled the application with `--release 21`. There are no Java test sources yet (`No tests to run`).
- `node scripts/validate-agent-assets.mjs` passed after the documentation updates.
- Docker Compose and PostgreSQL startup were not run because Docker is unavailable in the current environment.
- These checks do not verify browser rendering, Spring application startup/health, Compose health, database persistence, or production deployment.
