# Isolated demo lifecycle verification

Date: 2026-11-10  
Scope: R12 anonymous demo entry, seeded fixtures, active-capacity admission, expiration cleanup and session lifecycle.

## Implemented behavior

- Only `POST /api/v1/demo/sessions` is anonymous; CSRF protection remains enabled. Successful entry rotates the session and CSRF state. Repeating with a valid current demo reuses its company/operator and keeps the original expiry; a conventional or expired session may explicitly enter a fresh demo.
- Provisioning is one transaction: reserve an active-demo slot under a transaction-scoped PostgreSQL advisory lock, create the company/operator and four synthetic customer/payment fixtures, and commit before authenticating the browser session. The company admission facade requires the caller's outer transaction (`MANDATORY`), preventing unseeded partial companies from being committed by an unscoped call.
- Demo operators have no password hash and cannot log in through conventional password auth. The 24-hour expiry is derived from one clock sample normalized to PostgreSQL's microsecond timestamp precision.
- A scheduled bounded cleanup lists expired demo IDs, locks and revalidates each company, deletes payment/event/idempotency data, customers, operators and persisted sessions, then deletes the demo company. Conventional companies are not deleted. Demo customer/payment limits remain 100/500 under company-before-payment lock ordering.
- Global active capacity defaults to 500 demos and can be overridden by `flowpay.demo.max-active-companies`; cleanup cadence and batch size have documented `flowpay.demo.*` defaults. These are local application defaults, not production capacity guarantees.

## Verification evidence

- `.tools/r12-red-confirm.log`: before implementation, 12 unit/architecture tests passed and six of seven demo integration checks failed because entry/cleanup routes were absent; no skips. A later focused RED `.tools/r12-mandatory-red.log` confirmed the unscoped admission test failed before enforcing `MANDATORY`.
- `.tools/r12-targeted-green4.log`: 12 unit/architecture and 10 R12 PostgreSQL 18.6 integration tests passed, zero failures/errors/skips; `SourceStable=True`.
- `.tools/r12-final-verify.log`: complete backend verify passed 12 unit/architecture and 94 integration tests, zero failures/errors/skips; `SourceStable=True`.
- Independent read-only R12 review: PASS with no remaining actionable findings after the transaction-boundary fix.
- Testcontainers coverage includes CSRF, isolated sessions, reuse/expiry, passwordless demo operator, seed rollback, global capacity and no partial records, cleanup/session deletion, preserving conventional data, expiry/write race and last-slot concurrency.
- Rebuilt Compose stack became healthy. Actual proxy checks returned 429 with `Retry-After: 60` after the configured allowed login/demo attempts; verification requests intentionally omitted CSRF and created no sessions or records.

## Interview discussion and limits

The advisory transaction lock serializes global slot admission, while the company's row lock protects per-company quotas and expiry cleanup. The outer transaction stays open through seed creation, so failure rolls back the entire demo; authentication happens only after commit. The global capacity lock is appropriate for this single-database monolith and would need reevaluation before a multi-region or sharded design. Demo data is synthetic and no real money moves.
