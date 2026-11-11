# FlowPay product requirements

This is a filled product brief adapted from the supplied Vibe Coding Kit. Detailed business/technical rules remain in [the specification](../specs/2026-11-08-flowpay-portfolio-design.md); task ownership and acceptance remain in [the roadmap](../plans/2026-11-08-flowpay-implementation-roadmap.md). Do not maintain competing versions of those contracts here.

## Product and goal

FlowPay is a personal Java backend portfolio project simulating company payment processing. It should be functional, visually convincing and understandable enough for its owner to explain/debug/modify in international interviews. The owner reports one year with Java/frameworks, fluent English and openness to remote work from Brazil or relocation.

Fictional operators manage customers and payment lifecycle. Portfolio visitors enter isolated demo environments and reproduce reliable creation/replay/processing/history. No real money, cards or financial account credentials are involved.

## Required outcomes and verification owners

| Requirement | Acceptance reference |
|---|---|
| Session authentication and company-scoped access | R05, R13, R14, R19 |
| Scoped customer creation/search/pagination | R06, R15 |
| Exact BRL values and immutable final statuses | R07, R10, R16 |
| Transactional creation/history/idempotent replay | R08, R13, R15, R18 |
| Scoped payment queries and ordered history | R09, R16 |
| Defined backend dashboard metrics | R11, R17 |
| Isolated demo, quotas, expiry and cleanup | R12, R18, R19 |
| Real integrated responsive UI and failure feedback | R14–R19 |
| Reproducible performance evidence | R20, R21 |
| Honest English documentation and local reproduction | R22, R23 |

Primary journey: enter -> create/select customer -> create payment -> replay original request -> confirm same payment -> process -> inspect final history. Validate network ambiguity, invalid input, access denial, conflicts, quota/rate/capacity limits and expiry as specified.

## Non-goals and quality

Use docs/architecture.md and the detailed spec for scope; this import does not add a new architecture, new payment features or a dependency. Public deployment and repository publication remain separately requested actions.

Current phase: local implementation. Tooling and a disposable PostgreSQL connection are verified; HTTP/module contracts are defined. Application foundation work is underway; these checks do not establish working payment/authentication flows. Intended exposure: a future public demonstration with synthetic business data, real session security and isolated tenants. Do not collect real customer/payment data as demo input.

Apply docs/guides/quality-checklist.md proportionally to the actual change. Payment consistency, authentication, tenant isolation and schema changes require the named integration/concurrency/security tests even in a simulation. Static documents do not need application tests. Future public exposure requires verification of actual deployed behavior before claiming it is live/ready.

Visual direction is approved: Precision Glass refined, recorded in docs/design/guidelines.md and docs/design/design-system.md. Components and rendered verification remain to be implemented. Version pins are assigned to R00; HTTP/module contracts to R01. Success means a reproducible working demonstration with verifiable engineering evidence, not an arbitrary performance score or guaranteed job offer.

## Remaining decisions

- Prepare reusable assets, exact font/source/fallbacks and complete component states for the approved visual target in R03; broad visual selection is already resolved.
- Verify application-level dependency integration during R02/R03 using the concrete pins in docs/development/dependencies.md.
- Set numerical performance budgets after measured baselines; DTO details now live in the R01 contract.
- Select a provider only when preparing an explicitly requested public deployment.

Use docs/implementation-status.md for real progress; this brief does not mark an implementation task complete.
