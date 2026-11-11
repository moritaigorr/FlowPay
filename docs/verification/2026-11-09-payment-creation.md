# Payment creation verification — 2026-11-09

Scope: R08 POST `/api/v1/payments`, atomic creation/event/original-response persistence and tenant-scoped idempotency. Base: `32d3e4b` plus the R08 change. Payment reads, processing and cancellation remain later tasks at this handoff.

Creation validates strict JSON strings and the public BigDecimal command boundary, normalizes the DTO and hashes its explicitly ordered canonical representation. A single READ COMMITTED transaction reserves the key using `INSERT ... ON CONFLICT DO NOTHING`; a separate statement reads the committed conflict. New creation writes the payment, sequence-one event and original response before commit. Replay returns the exact original JSON/status/location, with a new request ID and `Idempotency-Replayed: true`, even after current status changes. Hash conflicts precede customer/quota checks; active-company checks still apply to every request.

Demo creation locks the company before reservation and quota decisions. The 500th payment remains replayable at the full quota. A transaction-local three-second lock timeout covers both company locking and conventional unique-index waiting; success restores the caller's setting. Timeout/deadlock exits the failed transaction and maps to safe 503 with `Retry-After: 1`.

## Actual evidence

| Check | Observed result |
|---|---|
| Initial RED | 13 payment integration tests: 12 assertion failures and one expected UnsupportedOperationException from the compiling stub; existing 12 unit/architecture tests passed |
| First focused GREEN | 13 payment integration tests plus 12 unit/architecture tests passed |
| Required-field regression | One expected metadata assertion failure exposed INVALID_FORMAT for a missing/null required value; corrected to REQUIRED |
| Final full Maven Wrapper verify | 70 tests passed: 12 unit/architecture and 58 integration, including 10 creation and 6 idempotency tests; zero failures/errors/skips; BUILD SUCCESS |
| Source stability | SHA-256 manifests matched before/after final verification |

Tests use PostgreSQL 18.6 Testcontainers and independent connections. They observe actual unique-index/company lock waiting, verify conventional same-key arbitration without the demo company lock, exercise both timeout paths, expiry after lock acquisition, the demo quota boundary and replay, tenant/key case sensitivity, canonical Unicode/escaping/hash order, strict wire types and direct command validation. Test-only PostgreSQL triggers fail event insertion and response completion; payment/event/reservation counts remain zero and the same-key retry succeeds. Outer rollback and caller lock-timeout restoration are also tested.

From `backend/`, after the documented Java/Docker setup:

```powershell
.\mvnw.cmd -B '-Dit.test=PaymentCreationIT,PaymentIdempotencyIT' verify
.\mvnw.cmd -B verify
```

The coordinator used the configured local Maven cache. Ignored raw logs: `.tools/r08-red.log`, `.tools/r08-focused.log`, `.tools/r08-required-red.log`, `.tools/r08-verify.log`. Existing Mockito/JVM warnings, expected lock-timeout warnings and safe exception-type logs for injected failures are not test failures; no warning-free output is claimed.

Independent /root/payment_creation_review passed this targeted spec/quality review with no actionable findings; the reviewer inspected source/evidence without rerunning builds. Deadlock-specific mapping and a contender proceeding after its competitor rolls back are not directly tested. A subsequent packaged Chrome-context API smoke created a real customer/payment through the same-origin proxy and confirmed 201/PENDING, exact decimal amount, Location, false/true replay headers and byte-identical response replay. This does not imply a payment form exists. No new dependency or migration was introduced. [ADR 0003](../adr/0003-idempotent-creation.md) records the decision, alternatives and official references actually consulted. Payment transaction tests do not establish browser retry UX, measured throughput, remote CI or deployment.

Interview explanation: the unique constraint arbitrates competing keys, while the shared transaction prevents stranded reservations or partial history. A replay uses the saved creation response rather than serializing the payment's current state. Catching a uniqueness exception and continuing in an aborted persistence transaction would not provide that guarantee.
