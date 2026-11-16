# FlowPay product expansion proposals

Status: 2026-11-16. Operational reporting and CSV export are approved and implementation is underway. Refunds and audit trail remain proposals.

## Why consider an expansion

FlowPay already demonstrates a complete simulated payment lifecycle. A useful next step should add a distinct engineering story rather than merely increase the number of screens. Each proposal below connects an operational need to backend behavior, a user flow, and interview discussion relevant to Java/Spring and SQL roles.

## Proposal A — Operational audit trail

**User need:** understand who performed an important operation, what changed, and when, especially while investigating a support or security question.

**Possible scope:** extend or complement the existing payment event history after inspecting its current guarantees. Record selected business/security-relevant actions with actor, action, target, timestamp, request ID, and a safe summary of the change. Keep records company-scoped and append-only to ordinary application flows.

**Potential user flow:** an operator opens a payment or an activity view, sees the ordered changes and actor, and follows a request ID when investigating an operation.

**Engineering story:** transactional event persistence, immutable history, tenant isolation, actor identity, privacy-safe logging, and tests proving that failed state changes do not leave misleading audit records.

**Decisions before implementation:** whether payment history already covers the intended need; which actions are in scope (payment transitions, customer changes, authentication events); retention/access policy; whether audit records are stored in the same transaction; and whether a dedicated screen is justified. Do not record passwords, cookies, complete request bodies, or unnecessary personal data.

## Proposal B — Simulated full and partial refunds

**User need:** model the operational correction of an approved payment while preserving the original payment record and its history.

**Possible scope:** create refund records for approved payments only. Enforce positive BRL amounts, cumulative refunds no greater than the original amount, idempotent creation, and correctness under concurrent refund attempts. Keep all behavior explicitly simulated; no real money moves.

**Potential user flow:** an operator opens an approved payment, requests a full or partial refund, sees the simulated result and remaining refundable amount, then reviews the refund in the payment history.

**Engineering story:** domain modeling, transactional invariants, database constraints, idempotency, lock ordering, concurrency tests, and a clear distinction between payment status and refund activity.

**Decisions before implementation:** model refunds as separate records versus expanding payment states; allowed amount precision; whether multiple partial refunds are supported; outcome/failure simulation; whether a fully refunded payment gets a derived display state; and how dashboard totals represent refunds. These choices affect Java domain rules, PostgreSQL schema, OpenAPI, metrics, and UI. Refunds are explicitly outside the current MVP in the [approved specification](../specs/2026-11-08-flowpay-portfolio-design.md); implementation requires an explicit scope/spec update.

## Approved scope — Operational reports and CSV export

**User need:** review a bounded period of payment activity outside the screen and share a useful operational summary.

**Scope:** add API-backed daily status counts and BRL volume summaries, plus a server-generated CSV that uses the same company-scoped search/status/customer/date filters as payment search. Export all matching rows (bounded to a documented maximum), with stable columns, BRL decimal strings, ISO timestamps, and safe CSV quoting/formula-injection handling. The overview links each status metric to the corresponding payment list filter. Both date range endpoints follow existing [from,to) semantics.

**Potential user flow:** an operator applies status/customer/date filters, requests an export, and downloads a CSV whose rows match those criteria.

**Engineering story:** streaming versus bounded export design, query reuse, authorization, consistent filters, CSV correctness, and tests for escaping, tenant boundaries, empty results and larger datasets.

**Implementation decisions:** export all matching results up to 10,000 rows; requests exceeding that limit return a validation error asking the operator to narrow the filters. CSV timestamps are UTC ISO-8601. Export is a synchronous authenticated download and is never cached. Daily aggregation is by UTC creation date and current status. No chart library is required; use a clear semantic table first.

## Suggested order (provisional)

1. Audit trail refinement: first compare the proposal with existing immutable payment events to avoid duplicating history.
2. Simulated refunds: strongest additional payment-domain and concurrency story, with the most contract/schema impact.
3. CSV reporting: builds on existing filters and becomes more useful once operational data grows.

This order is a discussion aid, not a commitment. Before any proposal enters the active roadmap, define its user-visible outcome, scope, API/data changes, security and tenant rules, acceptance criteria, owner/file boundaries, verification evidence, and learning goal. Update the product specification and contracts deliberately; preserve the current MVP until that decision is made.
