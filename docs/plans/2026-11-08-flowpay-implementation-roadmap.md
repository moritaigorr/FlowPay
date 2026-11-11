# FlowPay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a demonstrable, reliable Java payment simulation that the owner can explain in international junior backend interviews.

**Architecture:** Java modular monolith with company, identity, customer and payment modules; React/Vite dashboard; PostgreSQL; same-origin proxy. The main agent coordinates specialists, shared contracts and integration. This roadmap plans implementation; it does not start it.

**Tech Stack:** Java 21, stable compatible Spring Boot 4, Maven Wrapper, PostgreSQL 18, Flyway, Spring Security/Session JDBC/JPA/Modulith; React, TypeScript, Vite, Tailwind, shadcn/Radix, React Router, TanStack Query, React Hook Form/Zod; Boot-managed JUnit Jupiter/Testcontainers, Vitest/Testing Library, Playwright, Lighthouse and k6. Concrete R00 pins are in docs/development/dependencies.md.

**Spec:** `docs/specs/2026-11-08-flowpay-portfolio-design.md`. Also read `AGENTS.md`, `docs/guides/frontend-guidelines.md`, `docs/guides/performance-guidelines.md` and `docs/agents/README.md`.

The supplied Vibe Coding Kit is integrated through docs/product/prd.md, docs/architecture.md, docs/guides/coding-standards.md, docs/design/guidelines.md, docs/design/design-system.md and docs/guides/quality-checklist.md. These complement this plan; raw archived examples do not override task contracts. Apply balanced mentorship and evidence labels without adding unnecessary ceremony or weakening assigned checks.

## Global constraints

- Currency is BRL. Java BigDecimal, PostgreSQL NUMERIC(19,2), decimal strings over HTTP; no floating-point monetary arithmetic.
- PENDING may become APPROVED, REJECTED or CANCELLED; all final states are immutable.
- Creation requires tenant-scoped idempotency. Payment, initial event and original response commit atomically.
- Authenticated identity determines company access. Foreign IDs return 404. Session/CSRF protections stay enabled.
- Demo limits: 100 customers, 500 payments, 24-hour company lifetime; session inactivity timeout 30 minutes. Preserve company-before-payment lock order.
- Monorepo `backend/`, `frontend/`, `infra/`, `docs/`. Main Java package: `com.flowpay`.
- English public code/UI/docs; communicate with the owner in Portuguese. No real money, cards, banking integrations or unmeasured scale claims.
- No Kafka, Redis, microservices, Kubernetes, refunds, subscriptions, FX or public conventional signup in this implementation.
- Pin compatible stable versions during R00; do not invent version pins while planning. Follow the agreed library boundaries.
- User approval of a roadmap does not grant publication, paid services or public deployment. Local implementation and its verification remain distinct from release actions.

## Review focus

These implied input/failure classes get explicit tests in the owning tasks below:

1. A creation response is lost after commit: retry with the original key/payload must return the original payment, including at the demo quota (R08, R12, R15).
2. A user logs out while a read is in flight: the next session must not display the prior company's data (R14, R19).
3. Equal timestamps or UTC boundaries: ordering and [from,to) filters must produce deterministic pages and summaries (R09, R11).
4. A write fails between payment and event/response persistence: no partial payment/history/idempotency record may survive (R08, R10).
5. A session expires with an unsent form or demo cleanup races a write: show explicit expiry and preserve consistent data without silently creating a new attempt (R12, R15, R19).

## Agents and file ownership

| Role | Responsibility | Does not own by default |
|---|---|---|
| Main/coordinator | Architecture, scheduling, integration, shared contracts/configuration, progress | Does not delegate integration accountability |
| flowpay_backend | Java application, migrations, backend behavioral tests | Frontend, root/proxy/CI configuration, tracker |
| flowpay_frontend | UI, feature hooks/API adapters, frontend tests | Java, HTTP contract changes, tracker |
| flowpay_quality | Independent checks and assigned acceptance/E2E tests | Fixes to application code owned by another task |
| flowpay_performance | Measurement scripts, actual reports and diagnosis | Unassigned application optimization or public load tests |
| flowpay_reviewer | Independent, evidence-based review | All writes: reviewer is read-only |
| flowpay_portfolio | English documentation and interview material | Business behavior, metrics fabrication, tracker |

The coordinator owns `docs/api/openapi.yaml`, root `AGENTS.md`, this plan, `docs/implementation-status.md`, the active kit guides under `docs/`, root ignore/env/Compose files, `infra/`, `.github/`, and routing integration after R03. R03 temporarily assigns `docs/design/design-system.md` to the frontend agent; R22 explicitly assigns the listed documentation for final synchronization. The backend agent owns `backend/pom.xml` and all migrations sequentially; the frontend agent owns frontend manifests/lockfile except the explicit R19 handoff. For every handoff, name actual files, not an unrestricted whole-repository assignment.

One writer per file. The coordinator can temporarily assign a shared file to one task, recorded in its dispatch/status entry; ownership returns after integration. Agents must not revert others' changes. No recursive delegation. Use only the active session's available concurrency; fewer agents are appropriate for dependent work.

## File map

- `backend/src/main/java/com/flowpay/FlowPayApplication.java`: application entry.
- `backend/src/main/java/com/flowpay/{company,identity,customer,payment}/`: explicit application modules, public facades/records and internal implementations.
- `backend/src/main/java/com/flowpay/config/`: security/web infrastructure and demo/bootstrap orchestration outside explicitly selected domain modules.
- `backend/src/main/resources/db/migration/`: forward-only schema/session migrations. Never edit an applied migration.
- `backend/src/test/java/com/flowpay/`: unit, architecture and PostgreSQL integration tests; independent acceptance tests under `acceptance/`.
- `frontend/src/{app,components,features,lib,styles}/`: structure from frontend guide. Feature pages export named React components; main wires them into the router after R03.
- `frontend/e2e/`, `frontend/playwright.config.ts`: real-browser acceptance checks.
- `infra/nginx/default.conf`, `compose.yaml`, service Dockerfiles: same-origin local production-like environment.
- `performance/k6/`, `performance/lighthouse/`: scripts, versions and reproduction instructions created by performance tasks.
- `docs/adr/`, `docs/api/`, `docs/performance/`, `docs/verification/`: decisions/contracts/evidence created when the relevant deliverable exists.

## Dispatch, evidence and status protocol

Before any task, the coordinator reads the live tracker, checks dependencies are DONE, confirms its branch/workspace and assigns file ownership. The repository now has verified R00–R04 foundations; consult the live tracker rather than assuming later business features exist. Verify tool availability and Git state before execution.

Use this dispatch template:

```text
Task: Rxx — title
Agent: exact role
Goal and dependencies: ...
Read: task section + specification sections + applicable guides
Allowed files: exact existing paths and proposed new files
Shared files: explicit temporary ownership, or none
Consumes/produces: exact contract from the task
Acceptance checks: named tests/commands and expected behavior
Do not: edit other owners' files, invent data, change scope, delegate again
Return: files changed, actual checks/results, limitations, interview takeaway
You are not alone in this workspace; preserve and accommodate others' changes.
```

Execution cycle for each implementation task: (1) write the named behavioral tests, (2) run and observe the expected failure, (3) implement the defined behavior, (4) run focused checks, (5) coordinator integrates and requests read-only review, (6) resolve material findings and record evidence/status. Setup/docs tasks use their stated validation instead of artificial failing tests. Once Git exists, make scoped commits after verified tasks; never commit others' unreviewed work. No commits are made as part of writing this roadmap.

States: TODO -> READY -> IN_PROGRESS -> IN_REVIEW -> DONE. BLOCKED requires a concrete dependency/environment blocker, owner and next action in the tracker; it does not mean the application feature is complete. Only the coordinator updates shared status. DONE requires integrated files, actual verification and review evidence; otherwise record precisely what remains.

Backend planned checks: from `backend/`, `./mvnw verify` (PowerShell: `.\mvnw.cmd verify`), with Surefire for unit tests and Failsafe for `*IT` using PostgreSQL. Frontend planned checks: from `frontend/`, `npm run lint`, `npm run typecheck`, `npm run test -- --run`, `npm run build`; E2E `npm run test:e2e` is added in R19. These commands become available only after their setup task. A task's evidence records the exact successful command, not this planned shorthand.

## Delivery waves and parallel work

| Wave | Tasks | Allowed concurrency | Gate |
|---|---|---|---|
| 0 | R00a -> R01; R00b before R02 | Sequential contracts; Docker repair may proceed independently | Compatible toolchain/contracts recorded; database gate preserved |
| 1 | R02, R03 -> R04 | Backend foundation and frontend foundation may run together; R04 waits for both | Buildable local environment |
| 2 | R05 -> R06 -> R07 -> R08 -> R09 -> R10 -> R11 -> R12 | R14 can begin after R05; frontend features start only when consumed endpoints are DONE | Reliable API and isolated demo |
| 3 | R13; R14 -> R15/R16/R17 -> R18 | Acceptance tests may run alongside UI work; R15/R16 share payments files and must run sequentially; R17 can run separately if ownership is disjoint | Integrated product |
| 4 | R19, R20, R21 | Separate test/report files; coordinate environments and never run load during comparable Lighthouse/E2E measurements | Reproduced journeys and baseline evidence |
| 5 | R22 -> R23 | Documentation precedes final review | Honest, reproducible portfolio delivery |

This table is guidance; the exact dependency list on each task takes precedence. Backend reliability tests are implemented with features, not deferred to R13. Review is per-task and at final integration; do not start every role for every tiny edit.

### R00 — Toolchain and workspace baseline

**Owner:** main. **Depends:** none. **Files:** `docs/development/dependencies.md`, `.gitignore`, tool-version files; inspect existing Git before initializing it during authorized implementation.
**Consumes:** agreed stack. **Produces:** exact compatible versions, package-manager/build commands and workspace baseline for all agents.
- [x] Inspect Java, Maven, Node/npm, Docker/Compose and Git availability; record blockers without installing unrelated software.
- [x] Check official compatibility for Boot 4, Java 21, Session, Modulith, OpenAPI and frontend tools; pin stable versions and image tags in `docs/development/dependencies.md`.
- [x] Establish ignores for secrets, runtime artifacts, sessions and local measurement output. Validate that all selected versions and required tools have a documented resolution; share no credentials.
**Done/evidence:** compatible dependency matrix with source references and actual tool checks. **Learning:** explain why reproducible versions matter.

#### R00a — Toolchain and repository baseline

Owner: main. Dependencies: none. Includes R00's checks/version pins/ignore rules, process-local Java helper, dependency documentation and repository branch setup. Done requires actual Java 21/Maven/Node/Git evidence, documented stable compatibility and scoped review. Docker unavailability is recorded separately, never reported as a pass.

#### R00b — Docker readiness

Owner: main. Dependencies: R00a. Requires a successful Docker server query, resolution of the pinned PostgreSQL image and a bounded disposable database smoke check before database-dependent tasks. The initial external Docker Desktop startup issue was resolved when the user opened it; server/image/SQL smoke checks passed, as recorded in docs/development/dependencies.md. Do not reset/delete external application data without the appropriate authorization. R00 parent is complete only when both subtasks pass.

This split was recorded during execution because Docker's external startup failure must not block independent contract/frontend work. It does not waive PostgreSQL/Compose acceptance tests.

### R01 — Freeze shared contracts and decisions

**Owner:** main; reviewer checks. **Depends:** R00a. **Files:** `docs/api/openapi.yaml`, `docs/api/module-contracts.md`, `docs/adr/0001-modular-monolith.md`, `docs/adr/0002-session-authentication.md`, `docs/product/prd.md` and `docs/architecture.md` when synchronizing their summaries; root contract-validation package manifest/lockfile as development tooling.
**Produces:** versioned HTTP schemas and module facade signatures; later agents consume these rather than inventing field names.
- [x] Define every specified route, decimal-string money, enums, pagination, dates, simulation inputs, CSRF header/token shape, problem+json, requestId, replay headers and all documented error codes.
- [x] Check docs/product/prd.md and docs/architecture.md against the detailed spec and preserve one canonical contract; maintain the summaries if a deliberate decision changes them.
- [x] Fix documentation URLs: GET `/api/openapi.yaml` serves the maintained contract; GET `/api/docs` opens Swagger UI. Both are public read-only documentation, with no credentials/sample secrets and interactive execution disabled initially. Record any `/swagger-ui/` asset/redirect routes required by the chosen integration so the proxy/security tasks handle them explicitly.
- [x] Fix validation limits for names/emails/descriptions, session JSON shape, dashboard response and exact summary numeric representation. Record choices left to the implementer by the spec; do not change business scope.
- [x] Define public records/facades for active-company access/locking, owned-customer validation and payment creation used by demo provisioning; avoid cross-module JPA types and identity dependencies in domain services.
- [x] Validate OpenAPI with a version-pinned validator chosen/documented in R00; manually compare all spec routes and error/status examples. Reviewer checks for ambiguity before dependent work.
**Done/evidence:** validated contract, no consumer-facing placeholders. **Learning:** explain contract-first coordination.

### R02 — Buildable backend, migrations and module boundaries

**Owner:** flowpay_backend. **Depends:** R01, R00b. **Files:** `backend/pom.xml`, Maven Wrapper, application entry/config/resources, module `package-info.java` files, migrations, `ApplicationArchitectureTest.java`, `SchemaMigrationIT.java`, `ApiDocumentationIT.java`.
**Produces:** bootable Java application and complete initial core/session schema; explicit domain-module detection excludes orchestration/infrastructure packages.
- [x] Add architecture/schema tests: no module cycles/internal access; fresh PostgreSQL migrates; amount > 0, allowed enums, composite company/customer FK and unique idempotency key are enforced.
- [x] Observe failures, scaffold the pinned application and configure Surefire/Failsafe/Testcontainers; implement core and Spring Session JDBC migrations using supported schemas.
- [x] Add injected Clock for testable expiry/time boundaries, request IDs, consistent HTTP errors, bounded health endpoints and structured redacted logs. Reject unknown request fields in business DTOs.
- [x] Package the canonical R01 YAML into the application build without a separately maintained contract copy, serve `/api/openapi.yaml` and configure the compatible Swagger UI at `/api/docs`. Verify served YAML equals the canonical artifact and documentation assets load; avoid a conflicting automatically generated public schema.
- [x] Run architecture tests, migrations and `mvnw verify`; start with PostgreSQL and confirm health without leaking environment/secrets. Temporary security must fail closed except explicit health endpoints until R05.
**Done/evidence:** schema and module tests pass on real PostgreSQL. **Learning:** explain database constraints versus Java validation.

### R03 — Buildable frontend and visual foundation

**Owner:** flowpay_frontend. **Depends:** R01. **Files:** `frontend/package.json`, lockfile, Vite/TypeScript/lint/test configs, `src/app/{router,providers}.tsx`, `src/styles/globals.css`, `src/components/{ui,shared}/`, `docs/design/ui-foundation.md`, `docs/design/design-system.md` under explicit temporary ownership for the selected visual system.
**Produces:** buildable SPA shell, tokens/components and documented route map; router ownership transfers to main after integration.
- [x] Configure pinned npm dependencies and actual lint/typecheck/test/build scripts; add a meaningful shell navigation/keyboard test and observe failure before its implementation.
- [x] Read docs/design/guidelines.md and docs/design/design-system.md and implement the approved Precision Glass refined target at docs/design/concepts/precision-glass-refined.png (user approval: 2026-11-08). Preserve its light/blue operational hierarchy, refine reusable assets and record exact font/source/fallbacks, material/motion choices and component states. Do not restart broad selection or substitute the kit's sample palette; backend/contract work can proceed independently.
- [x] Define tokens for the selected theme, typography, spacing, status badges and layout; document intended overview/list/detail composition. Use only needed official shadcn components.
- [x] Implement shell, simulation notice, route-level fallback/not-found and responsive navigation. Planned pages are clearly unavailable until integrated; no pretend metrics or payment actions.
- [x] Run configured checks and inspect at 375/768/1440px plus keyboard navigation. Record screenshots when available and actual limitations.
**Done/evidence:** shell works and build succeeds; main receives route/component integration instructions. **Learning:** explain frontend/backend boundaries.

### R04 — Same-origin environment and continuous checks

**Owner:** main. **Depends:** R02, R03. **Files:** `compose.yaml`, `.env.example`, `infra/nginx/default.conf`, `backend/Dockerfile`, `frontend/Dockerfile`, `.github/workflows/ci.yml`, `docs/development/local-development.md`.
**Produces:** PostgreSQL + Java + static frontend/proxy environment and CI commands matching project configuration.
- [x] Add pinned multi-stage builds, persistent database volume, healthchecks, secret-free env example and dev proxy configuration through explicit shared-file handoff.
- [x] Route `/api` and required Swagger UI assets/redirects to Java, and SPA routes to index.html without swallowing API/documentation 404s. Verify `/api/openapi.yaml` is YAML, `/api/docs` reaches documentation and UI assets load through the proxy. Distinguish safe local HTTP cookie settings from production HTTPS Secure cookies. Add configurable login/demo proxy limits.
- [x] Build and start Compose; verify shell, health, API error routing and restart persistence. Configure Java verify and frontend checks in CI with Docker available.
**Done/evidence:** documented start/stop/reset procedure, local smoke results and CI definition; do not claim remote CI passed until a run exists. **Learning:** explain same-origin sessions and reproducible environments.

### R05 — Identity, company access and local provisioning

**Owner:** flowpay_backend. **Depends:** R02, R04. **Files:** company/identity facades + internals, `config/security/`, `config/bootstrap/`, `AuthenticationIT.java`, `CompanyAccessIT.java`.
**Produces:** `/auth/csrf`, login/me/logout and `/company`; active-company/lock facade from R01; explicit local provisioning command/profile.
- [x] Write tests for 401 without session, 403 without CSRF, session-ID rotation, logout invalidation, 30-minute inactivity and configured cookie attributes.
- [x] Implement password hashing, globally unique normalized conventional login, principal-derived company/user IDs and expiry guard using Clock. Passwordless demo users cannot use conventional login.
- [x] Preserve public GET access to the documented schema/UI assets from R01/R02 while denying unauthenticated business operations; document routes do not disable CSRF or grant API write permissions.
- [x] Provision a local conventional company/operator from explicit env configuration; disable that bootstrap in the public profile. Persist sessions via JDBC.
- [x] Verify both own-company access and inactive/expired-company denial; run security integration tests and full backend verify.
**Done/evidence:** real login/session flow works without exposing credentials. **Learning:** explain session security, CSRF and company isolation.

### R06 — Company-scoped customer management

**Owner:** flowpay_backend. **Depends:** R05. **Files:** customer facade + internals, `CustomerIT.java`.
**Produces:** POST/GET `/customers`, pagination/search and owned-customer validation facade from R01.
- [x] Test create/list/search, accepted duplicate customer email, invalid inputs, foreign IDs and tenant-separated pages.
- [x] Implement active-company checks and the demo customer quota (100) under company lock; test concurrent creation at the boundary using controlled demo fixtures.
- [x] Verify schema constraints and HTTP contract with PostgreSQL; run focused integration tests and backend verify.
**Done/evidence:** own customers work; cross-company and quota violations are rejected. **Learning:** explain authorization on data access.

### R07 — Payment value and state rules

**Owner:** flowpay_backend. **Depends:** R06. **Files:** `payment/internal/domain/`, `PaymentRulesTest.java`, `PaymentAmountTest.java`.
**Produces:** Money/value validation and deterministic transitions consumed by creation/processing tasks.
- [x] Test amount strings `"0.00"`, `"-1.00"`, `"1.001"`, out-of-range values and exponent notation fail; `"150.00"` is accepted exactly without rounding.
- [x] Test PENDING -> APPROVED/REJECTED/CANCELLED; every transition from a final status is rejected; APPROVE/DECLINE simulation maps deterministically.
- [x] Implement minimal rules using BigDecimal/enums and contract-defined validation; run focused tests and backend verify.
**Done/evidence:** domain rules pass without HTTP or database dependencies. **Learning:** explain monetary precision and invariant enforcement.

### R08 — Atomic creation and idempotency

**Owner:** flowpay_backend. **Depends:** R07. **Files:** payment creation facade/internal services/repos/controller, `PaymentCreationIT.java`, `PaymentIdempotencyIT.java`, `docs/adr/0003-idempotent-creation.md` (explicit temporary docs ownership).
**Produces:** POST `/payments` with complete idempotency, original-response replay and creation history from its first usable version.
- [x] Test first creation = 201/PENDING/one event; replay = same body, status, ID and replay header; changed payload with same key = 409; another company may use the same key.
- [x] Test simultaneous same-key calls on independent connections create one payment; inject a failure between payment/event/response writes and assert total rollback plus successful subsequent retry.
- [x] Implement canonical DTO hashing, INSERT ON CONFLICT reservation and subsequent read under READ COMMITTED; preserve the spec's demo lock/replay-before-quota order and timeout 503 behavior.
- [x] Validate customer ownership only for new creation, enforce demo 500-payment quota atomically, and test replay of the 500th creation still succeeds after quota is full.
- [x] Run PostgreSQL concurrency/rollback tests and verify. Document why exception recovery inside a rollback-only JPA transaction is insufficient.
**Done/evidence:** reliable creation/replay including lost-response retries; no temporary duplicate-prone endpoint. **Learning:** explain transaction boundaries and idempotency.

### R09 — Payment reads, history, filters and stable pagination

**Owner:** flowpay_backend. **Depends:** R08. **Files:** payment read/query/controller files, `PaymentQueriesIT.java`.
**Produces:** GET payments/list/detail/events exactly as R01; history sorted by sequence.
- [x] Test foreign payment/detail/events return 404; default size 20/max 100, bounded q length 200 and invalid parameters return 400.
- [x] Test equal timestamps sort by createdAt desc/id desc; filters use inclusive from/exclusive to; no duplicates across unchanged dataset pages.
- [x] Implement scoped queries, q semantics, bounded projections and needed initial indexes; avoid N+1 customer lookups. Verify query results and counts against PostgreSQL.
**Done/evidence:** 86 tests passed (12 unit/architecture + 74 PostgreSQL 18.6 integration), source-stable full verification, independent review PASS, OpenAPI valid; see `docs/verification/2026-11-10-payment-queries.md`. **Learning:** explain stable ordering and relational queries.

### R10 — Processing/cancellation with concurrent consistency

**Owner:** flowpay_backend. **Depends:** R09. **Files:** payment transition services/controllers, `PaymentTransitionIT.java`, `PaymentConcurrencyIT.java`, `docs/adr/0004-payment-locking.md` (temporary docs ownership).
**Produces:** process/cancel endpoints with atomic final state/history and reason codes.
- [x] Test APPROVE -> APPROVED, DECLINE -> REJECTED/SIMULATED_DECLINE, pending cancellation and repeated final-state action -> 409.
- [x] Race process against cancel using independent connections; assert exactly one successful transition and one final event. Foreign IDs remain 404.
- [x] Implement tenant-scoped row locking and revalidation with company-before-payment lock order when both are used. Inject event persistence failure and assert status rollback.
- [x] Run concurrency/integration tests and verify; document locking tradeoffs.
**Done/evidence:** 91 tests passed (12 unit/architecture + 79 PostgreSQL 18.6 integration), source-stable full verification, independent review PASS; see [transition verification](../verification/2026-11-10-payment-transitions.md) and [ADR 0004](../adr/0004-payment-locking.md). **Learning:** explain database locks and isolation.

### R11 — Defined dashboard metrics

**Owner:** flowpay_backend. **Depends:** R10. **Files:** payment summary query/controller, `DashboardSummaryIT.java`.
**Produces:** GET `/dashboard/summary` with R01 DTO and exact spec metrics.
- [x] Fixture: one APPROVED 150.00, one REJECTED 20.00, one PENDING 30.00 and one CANCELLED 40.00; assert approved volume 150.00, pending count 1/amount 30.00, total 4 and approval rate 1/2 in the contract's numeric representation.
- [x] Test zero denominator -> no-data representation; another tenant excluded; UTC [from,to) boundary and status changes of payments created inside the period.
- [x] Implement backend aggregation without loading whole history. Verify tests and contract; optional daily chart aggregation is added only if the chart is selected.
**Done/evidence:** 96 tests passed (12 unit/architecture + 84 PostgreSQL18.6 integration), source-stable full verification and independent review PASS; see [dashboard verification](../verification/2026-11-10-dashboard-metrics.md). Metrics are clearly distinguished from revenue/balance. **Learning:** explain metric definitions and SQL aggregation.

### R12 — Isolated demo lifecycle and resource limits

**Owner:** flowpay_backend; main owns proxy changes. **Depends:** R11. **Files:** `config/demo/`, domain facade implementations required by R01, `DemoLifecycleIT.java`, `DemoConcurrencyIT.java`; proxy tests/config only by main handoff.
**Produces:** POST `/demo/sessions`, atomic seeded company/operator, supported session authentication and scheduled expiry cleanup.
- [x] Test two visitors receive distinct companies, existing valid demo session is reused, and partial provisioning failure leaves no company/customer/payment fragments.
- [ ] Implement 24-hour expiry, Clock-driven request guard and cleanup including sessions/records; conventional companies are never removed. Test a write racing expiry/cleanup without inconsistent leftovers or lock-order inversion.
- [ ] Verify 100-customer/500-payment quotas including simultaneous boundary creation and idempotent replay; capacity exhaustion = 503 with no partial provisioning.
- [ ] Main configures concrete proxy login/demo rate limits from env defaults documented locally; verify expected 429 via the actual proxy. Run tests for expired session = 401 DEMO_EXPIRED and inability to log in as a demo user by password.
**Done/evidence:** anonymous visitors can enter isolated, bounded demo environments. **Learning:** explain lifecycle, resource protection and multi-tenancy. See [demo lifecycle verification](../verification/2026-11-10-demo-lifecycle.md): 106 backend tests on PostgreSQL 18.6, independent PASS, rebuilt proxy 429/Retry-After verified.

### R13 — Independent backend acceptance checks

**Owner:** flowpay_quality. **Depends:** R12. **Files:** `backend/src/test/java/com/flowpay/acceptance/PaymentSecurityAcceptanceIT.java`, `DemoAcceptanceIT.java`, `OpenApiContractAcceptanceIT.java`, `docs/verification/backend-acceptance.md`.
**Consumes:** completed backend behavior; no application source edits.
- [x] Add an independent HTTP journey through CSRF/login/customer/create/replay/process/history and foreign-company access; use a real database and distinct sessions.
- [x] Verify quotas, failure rollback, final-state conflicts and session expiry through public behavior, not implementation mocks. Run `mvnw verify`.
- [x] Compare implemented business route/method mappings and representative success/error response schemas against the canonical OpenAPI document using the compatible validation approach recorded in R01; fail on contract drift.
- [x] Report reproducible defects to main; backend owns fixes in separately assigned files. R13 is accepted only after all checks and reruns pass; see the evidence report.
**Done/evidence:** independent acceptance report names commands/results and uncovered areas. **Learning:** distinguish unit, integration and acceptance evidence. See [backend acceptance evidence](../verification/backend-acceptance.md): 114 tests (12 unit/architecture +102 PostgreSQL integration), zero failures/errors/skips, reviewer PASS.

### R14 — Frontend HTTP, authentication and session-safe cache

**Owner:** flowpay_frontend; main integrates routes. **Depends:** R03, R04, R05. **Files:** `src/lib/{http,query-client}.ts`, `src/features/auth/`, related tests; main `src/app/router.tsx`.
**Produces:** `apiRequest<T>(path: string, options?: RequestInit): Promise<T>`, structured ApiError preserving code/requestId, AuthProvider/useAuth and exported LoginPage.
- [x] Test 204, malformed/non-JSON error response, CSRF token refresh after login, cancellation, unauthorized session and bounded read retries with mutations not retried automatically.
- [x] Implement session auth and identity-scoped query keys. Test logout while read is pending: cancel/remove old queries and prevent old results entering the next session's cache.
- [x] Export login/session-expired flows with accessible errors; main wires routes. Verify against real auth endpoints plus frontend checks and browser inspection.
**Done/evidence:** actual login/logout works and data cannot leak through frontend cache reuse. **Learning:** explain server state and session lifecycle.

### R15 — Customer and payment creation/list screens

**Owner:** flowpay_frontend; main integrates routes. **Depends:** R06, R08, R09, R14. **Files:** `src/features/customers/`, `src/features/payments/{api,hooks,schemas,PaymentsPage,CreatePaymentForm}.*`, relevant tests and shared formatting components explicitly assigned by main.
**Produces:** exported CustomersPage/PaymentsPage, real create forms, filter/pagination URL state and server-backed lists.
- [x] Test decimal-string parsing/formatting at the monetary maximum without Number rounding, validation, backend field errors and filter changes resetting page.
- [x] Test response lost after successful create: the retry retains original key/payload; editing the payload creates a new logical attempt/key. Session expiry keeps form feedback explicit and does not silently resubmit under a fresh demo.
- [x] Implement forms with RHF/Zod, pending controls, original attempt snapshot, bounded server queries and post-success invalidation. No optimistic payment success.
- [x] Verify real customer/create/replay/list flow, API failures and empty states at desktop/mobile plus frontend checks. Main wires routes after receiving named exports.
**Done/evidence:** 64 frontend tests plus lint/typecheck/build pass; real browser created/listed a customer/payment and verified a post-commit lost response retries the same key, receives the exact replay, and lists one payment. Keyboard/responsive inspection passed at 1440/768/375; independent review PASS. **Learning:** explain retry ambiguity and client/server validation.

### R16 — Payment details, actions and event timeline

**Owner:** flowpay_frontend. **Depends:** R10, R15. **Files:** `src/features/payments/{PaymentDetailsPage,PaymentTimeline,PaymentActions}.*`, assigned payment API/hooks/tests; main route integration.
**Produces:** exported PaymentDetailsPage consuming route paymentId and real detail/events/process/cancel endpoints.
- [x] Test pending actions, final-state disabled/unavailable actions, conflict refetch, foreign/not-found response and event ordering.
- [x] Implement accessible status/timeline, deterministic scenario explanation and mutation invalidation of detail/history/list/summary. Do not show an approved status before server confirmation.
- [x] Verify approval, decline and cancellation from separate real pending payments, including failed actions; run checks and responsive/keyboard inspection.
**Done/evidence:** 69 frontend tests, lint/typecheck/build passed; real PostgreSQL-backed browser journey verified approval, simulated decline, cancellation, concurrent 409 refresh and not-found handling; keyboard and 1440/768/720/375 CSS-pixel layout checks passed; independent review found no actionable issue. See `docs/verification/2026-11-10-payment-details-ui.md`. **Learning:** the API/database owns state transitions; on a conflict the UI refetches current state instead of resolving it locally.

### R17 — Dashboard presentation and metrics

**Owner:** flowpay_frontend. **Depends:** R11, R14. **Files:** `src/features/dashboard/`, dashboard tests; shared formatting read-only unless temporarily assigned; main route integration.
**Produces:** exported OverviewPage with period selection and defined real metrics.
- [x] Test loading, no data, failure, zero denominator, period/UTC parameters and no invented change percentages/revenue labels.
- [x] Implement summary/recent-payment presentation and independent concurrent reads where useful. If a daily chart is chosen, main first adds its HTTP contract and backend aggregation task; otherwise ship a clear chart-free overview.
- [x] Run frontend checks and inspect hierarchy, numbers, layout stability and mobile/keyboard behavior.
**Done/evidence:** 64 frontend tests plus lint/typecheck/build pass; real API overview, keyboard access and responsive layouts inspected at 1440/768/375; independent review PASS. **Learning:** explain cache invalidation and metric semantics.

### R18 — Guided demonstration

**Owner:** flowpay_frontend. **Depends:** R12, R16, R17. **Files:** `src/features/demo/`, assigned auth/demo adapters and guide tests; main route/nav integration.
**Produces:** demo entry and create -> replay -> process -> history walkthrough using real endpoints.
- [x] Test guide replay uses the exact captured request/key and shows the same ID; a page reload without the in-memory snapshot offers to restart the guide explicitly rather than fabricating replay evidence.
- [x] Implement isolated demo entry, synthetic-data notice, optional request/response inspection and clear 401 expiry/409 quota/429 rate-limit/503 capacity states.
- [x] Verify the complete guide in two separate browser sessions with distinct data; run checks and visual inspection.
**Done/evidence:** 80 frontend tests, lint/typecheck/build passed; two independent real browser sessions completed create -> exact replay -> process -> history with distinct companies/customers/payment IDs; actual 201 replay header and ID were checked; reload restart prompt verified; responsive 1440/375 screens inspected; reviewer PASS after fresh-quota recovery fix. See `docs/verification/2026-11-10-guided-demo.md`. **Learning:** the replay returns the original creation response; current status comes from a later server read.

### R19 — End-to-end, responsive and integration acceptance

**Owner:** flowpay_quality; main integrates CI. **Depends:** R13, R18. **Files:** `frontend/playwright.config.ts`, `frontend/e2e/{demo,payments,session-isolation,accessibility}.spec.ts`, `docs/verification/product-acceptance.md`; explicit exclusive handoff of frontend manifest/lockfile for Playwright setup.
**Produces:** actual test:e2e command and reproducible tests against production-like same-origin environment.
- [x] Configure pinned Playwright and fixtures; test demo guide, customer creation, approval, decline, cancellation, filters and reload/direct route navigation against the real backend.
- [x] Through the actual proxy, verify the documentation link opens Swagger UI, its assets load and `/api/openapi.yaml` contains the expected API contract rather than SPA HTML or a login redirect.
- [x] Test an invalidated session during form submission, logout with a read in flight followed by another demo login, cross-session data isolation and a 503/retry path. Verify proxy/CSRF/cookie behavior over local HTTP and document that the inactivity timeout and HTTPS cookie remain untested.
- [x] Inspect 375/768/1440 CSS-pixel layouts, a 360 CSS-pixel viewport equivalent to 200% zoom at 720 physical pixels, keyboard focus/form toggle and console/network errors. Capture sanitized real screenshots. Native zoom and screen-reader behavior remain unverified.
- [x] Main adds browser E2E CI wiring; resolve findings and rerun affected checks and the core journey. CI workflow is configured but has not yet run on GitHub Actions.
**Done/evidence:** report and test artifacts establish complete UI/API/data behavior. **Learning:** explain end-to-end evidence and limitations.

### R20 — API performance baseline

**Owner:** flowpay_performance. **Depends:** R13. **Files:** `performance/k6/`, `docs/performance/api-baseline.md`; backend fixture helper only via explicit backend-owned follow-up if required.
**Produces:** version-pinned reproducible workload scripts and an actual English baseline report.
- [x] Define disposable deterministic tenants/datasets, authentication/CSRF and controlled smoke/increasing-load profiles; distinguish conventional tenants from quota-limited demo behavior.
- [x] Measure list/detail/new-create/replay/transition scenarios with correct keys, pending fixtures and assertions. Record p50/p95/p99, achieved throughput, unexpected failures, resource settings and dropped iterations when applicable.
- [x] Verify dataset postconditions and distinguish expected conflict tests; document exact command, source revision, environment and generator contention limits. No public load test or invented capacity claim.
**Done/evidence:** `docs/performance/api-baseline.md`; pinned k6 scripts in `performance/k6/`. Smoke 11/11 checks; reads 5,381 iterations and 16,155/16,155 checks; mixed 1,752 iterations and 14,028/14,028 checks; conflict 4/4 races had exactly one commit and one conflict. Cleanup verified per profile. Initial local baseline only, with limited repetition/resource evidence. **Learning:** explain workload design, per-VU session, exact replay, isolated fixtures, race assertions and percentile limits.

### R21 — Frontend performance baseline

**Owner:** flowpay_performance. **Depends:** R19. **Files:** `performance/lighthouse/`, `docs/performance/frontend-baseline.md`.
**Produces:** measured authenticated production-screen baseline and prioritized evidence-based findings.
- [x] Pin Lighthouse/runner and establish auth without publishing session artifacts. Verify reports target overview/list/details rather than login redirects.
- [x] Capture at least three runs per scenario, median plus individual results, mobile/desktop settings, cold/warm distinctions, bundle/network data, LCP/CLS/blocking work and interaction profiling as needed.
- [x] Propose only measured improvements. Main assigns any approved fix to the owning backend/frontend agent with exact files and acceptance, then performance repeats comparable measurements and correctness checks.
**Done/evidence:** `docs/performance/frontend-baseline.md`; Lighthouse 13.6.0, 18 authenticated production-built local audits, three samples for each of Overview/Payments/Payment details in mobile and desktop emulation. Sanitized individual metrics and verified medians: `performance/lighthouse/results-2026-11-10.json`, `performance/lighthouse/medians-2026-11-10.json`. Findings prioritize mobile LCP/estimated unused JS and payment-list desktop CLS; no application changes or capacity promises. **Learning:** explain cold lab conditions, authentication handling, medians and limitations.

### R22 — Portfolio documentation and interview walkthrough

**Owner:** flowpay_portfolio. **Depends:** R19, R20, R21. **Files:** root `README.md`, `docs/architecture.md`, `docs/demo-walkthrough.md`, `docs/interview-notes.md`, `docs/deployment.md`; explicit temporary ownership of `docs/product/prd.md`, `docs/architecture.md`, `docs/guides/coding-standards.md`, `docs/design/guidelines.md`, `docs/design/design-system.md`, `docs/guides/quality-checklist.md` and `docs/agents/prompt-library.md` for synchronization only. Prior ADRs and the raw kit archive stay read-only unless separately assigned.
**Produces:** English portfolio narrative grounded in verified code and evidence.
- [x] Validate start/test commands from a clean environment with main; show actual architecture/state diagrams, demo instructions, limitations and the important ADRs.
- [x] Synchronize docs/product/prd.md and docs/architecture.md, active design/coding guides and final evidence links with implemented behavior; keep archived kit originals unchanged.
- [x] Reference real screenshots/reports and measured results only. Link API documentation to verified `/api/docs` and the canonical schema route. Identify simulation and personal-project status; omit public URLs until they exist.
- [x] Write a short demo narration and interview Q&A covering idempotency, lost responses, transaction rollback, locks, isolation, sessions and the monolith choice. Explain what the owner should be able to change/debug independently.
- [x] Document deployment procedure and provider-neutral requirements; do not publish, provision paid infrastructure, deploy or apply to jobs.
**Done/evidence:** main reviewed English README, module/sequence diagrams, [demo narration](../demo-walkthrough.md), [interview Q&A](../interview-notes.md) and [deployment limits](../deployment.md). Relative links, commands, screenshots and reports were checked; stale local setup/roadmap statements were synchronized. No deployment, public URL or paid resource was created. **Learning:** explain the retry, transaction, locking and isolation story in the project demo and interview.

### R23 — Final integrated review and local release readiness

**Owner:** main; flowpay_reviewer is read-only. **Depends:** R22. **Files:** `docs/verification/release-readiness.md`, shared tracker; fixes remain with their explicit owners.
**Produces:** evidence of an integrated local portfolio delivery and a precise list of any external release steps remaining.
- [x] Run clean build/start/migrate, backend verify, frontend checks and core real-backend E2E journey. Ensure demo data persistence/expiry and explicit reset procedure behave as documented.
- [x] Reviewer inspects exact integrated revision for correctness/security/regressions, module boundaries and unsupported claims. Fix material findings through owners and rerun affected checks.
- [x] Main checks spec/contract/docs consistency, measurement references and Git status; record passing/failed/unavailable checks without extrapolating them.
- [x] Mark DONE only with evidence. Public repository publication/deployment remains a separate explicit action; do not equate local readiness with a live site.
**Done/evidence:** [`docs/verification/release-readiness.md`](../verification/release-readiness.md) records no-cache Docker builds, Compose health/Flyway, Java 21 full verify, clean frontend install/lint/typecheck/80 tests/build, 12 browser journeys, separate default 429 case, local HTTP/proxy smoke and independent reviewer PASS. PostgreSQL volume was preserved; R12 controlled-clock integration tests establish demo expiry/cleanup and conventional-tenant preservation; the documented destructive reset command was not run. No remote CI, public URL, HTTPS deployment or paid resources. **Learning:** explain the full browser → API → PostgreSQL → response flow and distinguish local readiness from public release.

## Change and recovery rules

- If an interface changes, main updates R01 contracts and identifies every affected consumer/test before resuming their tasks. Do not let two agents independently settle incompatible DTOs.
- If a task grows beyond a reviewable slice, main splits it into suffixed IDs (e.g. R08a/R08b), records dependencies and acceptance in this plan/tracker, and preserves the original scope. Do not silently invent extra roadmap phases.
- If a tool/environment prerequisite is missing, record it; do not replace PostgreSQL with an in-memory database or real integration with fake success. Continue only independent READY work.
- If a task fails review, return it to IN_PROGRESS with precise findings; do not release its dependents as DONE. If an already accepted task must change, record affected tasks and required reruns.
- Optional components/chart/compiler/performance infrastructure require a demonstrated need and a bounded task. They do not block the core portfolio by default.

## Future product proposals — not active roadmap tasks

The owner asked to capture three possible expansions: an operational audit trail, simulated partial/full refunds, and server-backed operational reports with CSV export. They are recorded in [expansion proposals](../product/expansion-proposals.md) with user flows, engineering value and open decisions. These ideas are exploratory only: no task IDs, owners, acceptance checkboxes or implementation are assigned, and they do not change the completed R00–R23 baseline. Refunds remain outside the approved MVP scope unless the owner explicitly approves a specification change. Before starting any proposal, compare it with current payment history/contracts, define bounded acceptance criteria and verification, then update the product spec and roadmap deliberately.

## Planning self-review

Coverage checked: contracts, dependencies, modules, migrations/session schema, company/customer identity, money/state rules, idempotency/rollback, queries/pagination, transitions, metrics, demo isolation/quotas/cleanup, interface states/accessibility, complete tests, measured performance, CI/Compose, English portfolio and release limitations all have an owner/task above. Review-focus cases are assigned to their owning tests. Task dependencies are intended to be acyclic; the shared tracker is the live execution record. No task is marked implemented by this planning document.
