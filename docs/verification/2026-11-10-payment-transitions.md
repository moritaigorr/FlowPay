# Payment processing and cancellation verification

Date: 2026-11-10  
Scope: R10 process/cancel state transitions, event history consistency, locking and tenant isolation.

## Implemented behavior

- Process and cancel are authenticated, company-scoped operations on pending payments. Processing resolves the stored simulation scenario to `APPROVED` or `REJECTED`; cancellation resolves to `CANCELLED`.
- A final payment is immutable. Repeating an action after it reaches a final state returns `409 PAYMENT_FINAL_STATE` without another event.
- Status update and next immutable event commit in one transaction. A failed event insert rolls the payment update back.
- Payment rows are locked by both payment and company ID. Demo operations lock and revalidate the active company first, preserving company-before-payment ordering required by expiry cleanup. Conventional operations lock only the owned payment row.
- Row lock waits are bounded and translated to a retryable `503 CONTENTION_TIMEOUT`. Since process/cancel has no idempotency replay key, the response advises retrying the same operation; callers should read the payment after an unknown timeout outcome.

## Verification evidence

- `.tools/r10-red.log`: 12 unit/architecture checks passed; all 5 assigned integration cases failed against absent transition routes before implementation, as expected.
- `.tools/r10-targeted-verify.log`: 12 unit/architecture and 5 R10 integration tests passed, zero failures/errors/skips; source hashes matched before and after.
- `.tools/r10-full-verify.log`: final `mvnw verify` passed 12 unit/architecture and 79 PostgreSQL 18.6 integration tests, zero failures/errors/skips; `SourceStable=True`.
- Tests use PostgreSQL Testcontainers and independent connections for process-versus-cancel. They assert exactly one winner and one final event, foreign-company access remains not found, and event persistence failure rolls status back before retry.
- Independent read-only review: PASS, no actionable findings.
- [ADR 0004](../adr/0004-payment-locking.md) records the lock strategy and alternatives. No schema or dependency changes were needed.

## Limits and next steps

This implements deterministic simulated outcomes; it does not call a real payment network or move money. It does not make process/cancel idempotent, because the agreed API does not accept an idempotency key for those operations. Dashboard metrics and demo lifecycle remain separate tasks.

## Interview discussion

The database row lock, rather than a JVM mutex, serializes competing requests across independent connections and application instances. Revalidating `PENDING` after lock acquisition makes the loser see the committed final state. Updating payment and inserting its event in one transaction prevents state/history drift when persistence fails.
