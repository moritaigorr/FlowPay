# Payment queries and history verification

Date: 2026-11-10  
Scope: R09 payment listing, current detail and event history, plus search validation shared with customer listing.

## Implemented behavior

- `GET /api/v1/payments` lists records for the authenticated company with optional status, customer, literal text/UUID search and creation-time filters. Filters combine with AND; name/email alternatives inside text search combine with OR.
- Pagination is bounded and defaults to a stable `createdAt DESC, id DESC` order. The page and total are derived from one PostgreSQL statement snapshot, including empty and out-of-range pages.
- `GET /api/v1/payments/{id}` returns current state only when the payment belongs to the active company. `GET /api/v1/payments/{id}/events` first enforces the same ownership check, then returns events in sequence order.
- Amounts remain two-place decimal strings. Offset-bearing RFC3339 dates are validated strictly; `createdFrom` is inclusive and `createdTo` exclusive, with sub-microsecond bounds rounded up to PostgreSQL timestamp precision.
- Search text is literal (including `%`, `_` and `!`), length-bounded by Unicode code points and rejected with `400 VALIDATION_FAILED` when it contains U+0000, which PostgreSQL text cannot represent. Foreign and missing payment IDs use the same safe 404 response.

The list performs a separate active-company check through the company module and one payment-list SQL statement; it does not perform one customer lookup per returned row. Existing schema indexes are reused. No index, performance or benchmark claim is made.

## Verification evidence

- `.tools/r09-final-accepted-verify.log`: final `mvnw verify` with PostgreSQL 18.6 Testcontainers after Docker was restored: 12 unit/architecture and 74 integration tests passed; zero failures, errors or skips. Coordinator output confirmed `SourceStable=True` across the run.
- `.tools/r09-nul-red.log`: both new HTTP regressions failed before the correction with expected 400 and actual 500.
- `.tools/r09-nul-verify.log`: after the fix, 86 tests passed (12 unit/architecture and 74 integration), no failures, errors or skips; includes the two NUL regressions.
- Independent read-only review: PASS, no actionable findings.
- `npm run validate:api`: passed with zero warnings after synchronizing the OpenAPI and module contract.
- `git diff --check`: passed.

## Limits and next steps

R09 does not change payment state. Processing, cancellation and transactional history writes are R10. It does not add dashboard aggregation, demo lifecycle, frontend payment screens, deployment or performance measurements. Offset pagination provides deterministic order for an unchanged dataset, not a cross-request snapshot while rows change.

## Interview discussion

Company scope comes from authenticated server context, then is included in every query rather than accepted from a request parameter. A joined projection handles customer-name/email search without per-payment lookups. A single statement keeps page rows and total count consistent for that request, and explicit tie-breaking prevents unstable page ordering when timestamps match.
