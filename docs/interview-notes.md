# FlowPay interview notes

Use these notes to explain choices in the personal project clearly. FlowPay is not payment-industry production experience: it simulates payments and never moves real money. The project should be presented separately from employment history.

## 30-second introduction

> “FlowPay is a personal Java and Spring Boot payment-processing simulation with a React dashboard and PostgreSQL. I focused on correctness under retries and concurrency: payment creation, its initial history event and the original response commit in one transaction; a repeated request with the same key replays that response. Process and cancel serialize through PostgreSQL row locks, and every read is scoped to the authenticated company. I verified backend behavior against PostgreSQL Testcontainers and browser journeys against the local Compose stack. It does not process real money.”

## Walkthrough outline

1. Start the application and enter an isolated synthetic workspace.
2. Show Overview and the API-backed payment list.
3. In the guided demo, create a pending payment, replay the exact request and point to the same ID plus `Idempotency-Replayed: true`.
4. Process it, then read the current state and ordered event history.
5. Explain the transaction and database constraints behind each visible result.

The step-by-step script is [demo walkthrough](demo-walkthrough.md); the successful browser evidence is [R18](verification/2026-11-10-guided-demo.md) and [R19](verification/product-acceptance.md).

## Questions to prepare for

### Why use a modular monolith instead of microservices?

One application and one PostgreSQL transaction boundary make company-scoped payment creation and state/history updates understandable. Spring Modulith checks keep company, identity, customer and payment code separated behind public module APIs. Microservices would add network failures and distributed consistency without a demonstrated need. The tradeoff is one deployment and runtime; extraction is an option only if ownership, scaling or availability needs become real.

### What does the idempotency key guarantee?

For one company and create-payment operation, a unique database constraint arbitrates a key. The API canonicalizes the validated request and hashes it. A matching retry returns the saved status and exact original JSON body, with a replay indicator; a changed request with that key returns `409 IDEMPOTENCY_KEY_REUSED`. The reservation, payment, creation event and response commit or roll back in one transaction. This gives an at-most-one created payment for that key, not a general “exactly once” guarantee across arbitrary networks or external systems.

### What happens if the response is lost after commit?

The client retries with the same key and body. The second request observes the committed idempotency record and returns the original `201` status, body and payment ID, plus the replay header. A separate GET reports the current state if the payment has since been processed. The guided demo checks the real replay header/ID and does not simulate a replay in the browser.

### How do you prevent two final transitions?

Process and cancel run in PostgreSQL transactions. For a conventional tenant the service locks the owned payment row using `SELECT ... FOR UPDATE`, rechecks that the state is `PENDING`, then updates status and inserts the next event atomically. The competing request observes a final state and gets `409`. Demo mutations also lock the company first, preserving company-before-payment order with quota and expiry cleanup. A database timeout is a retryable `503`, not a success.

### How is company isolation enforced?

The server derives company and actor IDs from the authenticated session principal. The client never chooses the authorization tenant in the request body. Every list, detail and mutation query is company-scoped; database foreign keys also include company identity where needed. Tests cover foreign object access returning `404`, and independent demo browser sessions receive distinct companies.

### Why session cookies instead of JWTs?

The dashboard and API share an origin, so an HttpOnly server session avoids storing bearer tokens in browser storage and supports explicit logout/revocation. Spring Session JDBC persists sessions in the existing PostgreSQL service. The tradeoff is database-backed session traffic and an explicit CSRF token for writes. `SameSite=Lax` helps but does not replace CSRF protection. Production Secure-cookie behavior is not verified through a deployed HTTPS environment.

### Why decimal strings and `BigDecimal`?

Binary floating-point cannot exactly represent many decimal fractions. Java uses `BigDecimal`, PostgreSQL uses `NUMERIC(19,2)`, and the API sends decimal strings. The frontend formats those strings for display instead of converting amounts to JavaScript `number` for calculations.

### What have you measured?

The k6 API report has one local run per smoke, read, mixed and conflict profile. It exercised real PostgreSQL, sessions, CSRF, replay and transition races. Lighthouse has three runs per screen/device combination (18 total) on the local production-built frontend. Mobile lab results point to LCP and unused-JavaScript investigation; payment-list desktop CLS was 0.109. These are local lab measurements, not production capacity, real-user INP or a before/after performance improvement. Details: [API baseline](performance/api-baseline.md) and [frontend baseline](performance/frontend-baseline.md).

### What would you improve next?

Inspect the payment-list chunk graph and Lighthouse LCP element, and use a trace/layout-shift audit to find the payment-list desktop shift. Then make one evidence-based change, preserve the same authentication/isolation and correctness checks, and rerun comparable measurements. Do not optimize by removing CSRF, transactions, history or tenant filters.

## Alternatives worth discussing

| Decision | Chosen approach | Relevant alternative and tradeoff |
|---|---|---|
| State concurrency | PostgreSQL row locks and revalidation | JVM mutex is smaller but does not coordinate multiple app instances |
| Duplicate create | Unique key plus canonical request hash in the same transaction | Catching a JPA uniqueness exception can leave the transaction aborted; separate reservation storage adds recovery states |
| Browser auth | JDBC session cookie plus CSRF | JWT bearer auth can suit separate clients, but adds browser token storage and revocation decisions not needed for this same-origin MVP |
| Architecture | One Spring Boot modular monolith | Services add independent deployment/operations and distributed failure modes without a current requirement |
| Payment outcome | Deterministic `APPROVE`/`DECLINE` simulation | Real processor integration is out of scope and would require external credentials, settlement/reconciliation and failure handling |

More decision context is in [ADR 0001](adr/0001-modular-monolith.md), [ADR 0002](adr/0002-session-authentication.md), [ADR 0003](adr/0003-idempotent-creation.md) and [ADR 0004](adr/0004-payment-locking.md).

## Be ready to modify it

- Trace one request from the controller through the public module API into the transaction and SQL.
- Change the simulation scenario or add a validation rule, then update domain/integration tests and the API contract where needed.
- Reproduce an idempotency retry and a process-versus-cancel race; explain which database constraint or lock selects the winner.
- Use a request ID to locate a safe diagnostic without logging passwords, cookies or complete personal-data bodies.
- Explain the limits of a test or benchmark before presenting its result.

For an international application, put FlowPay under **Personal Projects** and describe it using these verified details. Keep the owner's professional Java experience and English fluency in the separate work/language portions of their CV; do not count this project as an extra year of employment or imply work authorization in another country.
