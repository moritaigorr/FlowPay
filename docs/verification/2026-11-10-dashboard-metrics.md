# Dashboard metrics verification

Date: 2026-11-10  
Scope: R11 company-scoped summary aggregation for the requested creation window.

## Implemented behavior

- `GET /api/v1/dashboard/summary` requires timezone-aware `from` and `to`, and rejects nonascending ranges.
- All metrics describe payments created in the half-open interval `[from,to)` and use current payment status. Approved volume includes APPROVED only; pending count/amount use PENDING; total counts every state.
- Approval ratio is `approvedCount / (approvedCount + rejectedCount)`, scale 4, `HALF_UP`. Pending and cancelled payments are excluded from its denominator. Zero denominator is JSON `null`, so the UI can show “No data”.
- Aggregates run in PostgreSQL and are scoped to the authenticated company. Money is emitted as decimal strings; aggregate sums use `BigDecimal` without narrowing to a single payment's `NUMERIC(19,2)` maximum.
- Timestamp bounds round upward at microsecond precision to keep exact membership semantics on PostgreSQL.

## Verification evidence

- `.tools/r11-red.log`: 12 unit/architecture tests passed; all five new integration cases failed before the endpoint existed. The first implementation run exposed one test-fixture date issue (the payment was created outside the fixed January cohort); the fixture was corrected to create a pending payment inside that cohort and process it through the real endpoint.
- `.tools/r11-targeted-green.log`: 12 unit/architecture + 5 PostgreSQL integration tests passed, zero failures/errors/skips; `SourceStable=True`.
- `.tools/r11-full-verify.log`: full `mvnw verify` passed 12 unit/architecture + 84 PostgreSQL 18.6 integration tests, zero failures/errors/skips; `SourceStable=True`.
- Independent read-only review: PASS, no actionable findings.
- No schema/dependency changes. No chart endpoint, benchmark or performance claim.

## Limits and next steps

The summary is a current-state creation cohort, so later transitions change its status metrics even though its date membership stays fixed. It does not represent revenue, balance, settlement or real payment-network data. Demo entry and dashboard presentation remain separate roadmap tasks.

## Interview discussion

The query aggregates in the database instead of loading every payment into the JVM. The `[from,to)` boundary composes cleanly across adjacent windows. `BigDecimal` preserves sums that exceed one payment's column bound, and a null ratio distinguishes “no denominator” from a measured zero.
