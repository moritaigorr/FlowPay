# ADR 0004: Serialize payment state transitions in PostgreSQL

- Status: Accepted
- Date: 2026-11-10
- Scope: R10 process and cancel operations

## Context

A payment can move from `PENDING` to exactly one final status. Processing and cancellation can arrive concurrently. The payment status and its immutable event history must commit together, and a retry after a final transition must return a deterministic conflict rather than adding another event. Demo expiry cleanup also locks the company before deleting payments, so demo mutations must follow company-before-payment lock order.

## Decision

Each process/cancel operation runs in one PostgreSQL `READ COMMITTED` transaction. It checks the authenticated company's active state; for demo companies it acquires and revalidates the company row lock first. It then selects the payment by both payment ID and company ID with `FOR UPDATE`, rechecks that its state is `PENDING`, updates the payment and inserts the next event sequence in the same transaction. The operation returns the updated payment only after both writes succeed. Competing transitions serialize on that payment row; the loser sees the committed final state and returns `409 PAYMENT_FINAL_STATE`.

A short PostgreSQL `lock_timeout` bounds lock waiting. Lock-timeout and deadlock SQL states are translated to `503 CONTENTION_TIMEOUT` with a retry hint. The process and cancel endpoints have no idempotency key or stored response replay; clients may retry only after observing a safe response and must understand that a timeout can leave the outcome unknown until the payment is read again.

## Alternatives considered

- Optimistic conditional update (`WHERE status='PENDING'`) could choose a single winner, but requires reliably distinguishing missing/foreign IDs from a lost race and still needs atomic event insertion. A pessimistic row lock makes state revalidation and event sequencing explicit.
- A JVM mutex would not coordinate multiple application instances and would disappear on restart. PostgreSQL is already the source of truth and coordinates independent connections.
- Locking every conventional company's row would simplify one lock path but would serialize unrelated operations for that tenant. Conventional transitions therefore lock only the owned payment row; the extra company lock is used for demo operations where expiry and cleanup require it.

## Consequences

- State changes and history cannot diverge after a database failure; rollback undoes both.
- At most one process/cancel request wins for a pending payment. A final payment is immutable.
- The row lock is held only through local database work. No external calls occur inside the transaction.
- Correctness depends on retaining the company-before-payment order for demo writes and cleanup.
- PostgreSQL integration tests must verify independent-connection races, rollback after event failure, tenant isolation and the exact final history.

## Verification boundary

R10's assigned PostgreSQL Testcontainers cases provide evidence for these behaviors. This ADR is not a throughput measurement or a claim about production scale.
