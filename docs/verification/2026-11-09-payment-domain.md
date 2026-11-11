# Payment domain verification — 2026-11-09

Scope: R07 amount validation and deterministic payment state rules. Source base: `bed7d1d` plus the R07 change. This stage adds internal domain code, not payment endpoints or a working payment UI.

## Behavior and decisions

`PaymentAmount.parse(String)` validates the agreed ASCII decimal grammar before constructing `BigDecimal`. It accepts equivalent strings such as `1`, `1.0` and `1.00`, returns scale two and a plain decimal representation, and rejects zero, negative values, excess fractional digits, exponents, signs, whitespace, separators, leading zeros and values beyond `99999999999999999.99`. The input length is bounded before parsing. Scale normalization uses `RoundingMode.UNNECESSARY`; no requested amount is rounded.

`PaymentRules.transition` permits only PENDING to APPROVED, REJECTED or CANCELLED. Every final state rejects all subsequent transitions, including a repeated transition to itself. Processing is deterministic: APPROVE returns APPROVED with no reason; DECLINE returns REJECTED with `SIMULATED_DECLINE`. Processing uses the same transition guard. Classes have no Spring lifecycle, database, random behavior or external call.

The implementation reuses the existing public enums and allowed company validation exception, without adding a dependency. A small internal final-state exception contains the current status. Translating it to the public HTTP 409 contract belongs to the later payment adapter/application tasks.

The alternative of parsing arbitrary BigDecimal syntax and rounding would accept inputs outside the wire contract and could change an amount. Explicit grammar plus exact scale normalization keeps that decision visible and testable.

## Verification

Final acceptance: the full Maven Wrapper `verify` passed 54 tests (12 unit/architecture, including 11 new domain tests, plus 42 PostgreSQL 18.6 integration tests), with zero failures/errors/skips and BUILD SUCCESS. SHA-256 source manifests matched before and after the final run. Independent `/root/payment_domain_review` found no actionable findings in this targeted spec/quality review. The initial RED run executed 11 tests: 11 expected assertion failures against compiling stubs, zero errors/skips. The first implementation passed a full backend run of 54 tests: 12 unit/architecture plus 42 PostgreSQL integration tests. A subsequent metadata assertion exposed `INVALID_TRANSITION` outside the documented field-error code set; it failed expecting `INVALID_FORMAT` before the production correction. The final suite includes the corrected assertion and implementation.

Actual commands from `backend/`, using the configured Java helper/local Maven cache:

```powershell
.\mvnw.cmd -B '-Dtest=PaymentAmountTest,PaymentRulesTest' test
.\mvnw.cmd -B '-Dtest=PaymentRulesTest#rejectsPendingToPending' test
.\mvnw.cmd -B verify
```

The first two commands recorded the RED assertions; the final full verify proves all domain tests green. Ignored local logs: `.tools/r07-red.log`, `.tools/r07-metadata-red.log` and `.tools/r07-final-verify.log`. The initial source-hash wrapper used an incorrect PowerShell argument; it was corrected for the final run rather than counted as source-stability evidence. Existing Mockito/JVM warnings and the expected customer lock-timeout warning remain in the regression output. No warning-free claim is made.

## Integration limits

The parser receives a String. Rejecting numeric JSON before coercion belongs to the R08 HTTP adapter; validating the planned `NewPayment` BigDecimal command boundary is also required there. These domain rules do not yet create payments, enforce idempotency, persist events, scope payment access, lock payment rows or expose process/cancel endpoints. R10 must invoke transition rules under the owned database lock and commit status/history atomically.

No frontend change, migration, dependency installation, performance result, remote CI execution or deployment is claimed. Existing PostgreSQL regression tests remain required even though the new domain tests run without Spring or a database.

Interview explanation: validate the decimal grammar before BigDecimal, normalize without rounding, and share one final-state guard between processing and cancellation. Database concurrency protection remains the application service's responsibility.
