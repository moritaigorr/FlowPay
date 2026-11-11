# FlowPay product expansion proposals

Status: exploratory ideas captured on 2026-11-10 at the owner's request. These are **not approved requirements, implementation tasks, or completed features**. R00–R23 and the current MVP scope remain unchanged until the owner chooses a proposal and approves a bounded specification change.

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

## Proposal C — Operational reports and CSV export

**User need:** review a bounded period of payment activity outside the screen and share a useful operational summary.

**Possible scope:** add a server-generated CSV export that uses the same company-scoped filters and documented date boundaries as payment search. Include only fields needed for the report, with stable column names, correct BRL decimal strings and timestamps, and safe CSV escaping/formula-injection handling.

**Potential user flow:** an operator applies status/customer/date filters, requests an export, and downloads a CSV whose rows match those criteria.

**Engineering story:** streaming versus bounded export design, query reuse, authorization, consistent filters, CSV correctness, and tests for escaping, tenant boundaries, empty results and larger datasets.

**Decisions before implementation:** maximum date range/row count, whether export reflects the current filtered page or all matching results, exact columns/timezone, synchronous response limits, and whether the API should expose a download endpoint or a generated report resource. Do not add a decorative export control before a real API contract exists.

## Suggested order (provisional)

1. Audit trail refinement: first compare the proposal with existing immutable payment events to avoid duplicating history.
2. Simulated refunds: strongest additional payment-domain and concurrency story, with the most contract/schema impact.
3. CSV reporting: builds on existing filters and becomes more useful once operational data grows.

This order is a discussion aid, not a commitment. Before any proposal enters the active roadmap, define its user-visible outcome, scope, API/data changes, security and tenant rules, acceptance criteria, owner/file boundaries, verification evidence, and learning goal. Update the product specification and contracts deliberately; preserve the current MVP until that decision is made.
