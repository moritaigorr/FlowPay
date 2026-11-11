# FlowPay documentation

Start with the [project overview](../README.md), [architecture](architecture.md) and [live implementation status](implementation-status.md). This index groups documents by their purpose; it does not replace the canonical specification or HTTP contract.

| Area | Entry points |
|---|---|
| Product | [Requirements](product/prd.md), [expansion proposals (not approved)](product/expansion-proposals.md), [template for new scope](product/prd-template.md) |
| Specification | [Approved product and architecture specification](specs/2026-11-08-flowpay-portfolio-design.md) |
| Planning | [Task ownership, dependencies and acceptance](plans/2026-11-08-flowpay-implementation-roadmap.md), [live status](implementation-status.md) |
| Architecture decisions | [Modular monolith](adr/0001-modular-monolith.md), [session authentication](adr/0002-session-authentication.md) |
| API | [Canonical OpenAPI](api/openapi.yaml), [module and HTTP contracts](api/module-contracts.md) |
| Development | [Local setup and checks](development/local-development.md), [dependency/toolchain evidence](development/dependencies.md) |
| Engineering guides | [Coding](guides/coding-standards.md), [frontend](guides/frontend-guidelines.md), [quality](guides/quality-checklist.md), [performance](guides/performance-guidelines.md) |
| Design | [Guidelines](design/guidelines.md), [design system](design/design-system.md), [UI foundation](design/ui-foundation.md), [approved concept](design/concepts/precision-glass-refined.png), [rendered evidence](design/implementation/README.md) |
| Agents | [Roles and coordination](agents/README.md), [optional task prompts](agents/prompt-library.md) |
| Verification | [Release readiness](verification/release-readiness.md), [integrated product acceptance](verification/product-acceptance.md), [foundation](verification/2026-11-08-foundation.md), [identity](verification/2026-11-09-identity.md), [customers](verification/2026-11-09-customers.md), [payment domain](verification/2026-11-09-payment-domain.md), [payment creation](verification/2026-11-09-payment-creation.md), [payment queries/history](verification/2026-11-10-payment-queries.md), [payment transitions](verification/2026-11-10-payment-transitions.md), [dashboard metrics](verification/2026-11-10-dashboard-metrics.md), [demo lifecycle](verification/2026-11-10-demo-lifecycle.md), [backend acceptance](verification/backend-acceptance.md), [frontend auth](verification/2026-11-09-frontend-auth.md), [payment detail UI](verification/2026-11-10-payment-details-ui.md), [guided demo](verification/2026-11-10-guided-demo.md) |
| Performance | [API baseline](performance/api-baseline.md), [performance methodology](guides/performance-guidelines.md) |
| Portfolio | [Demo walkthrough](demo-walkthrough.md), [interview notes](interview-notes.md), [deployment status and future requirements](deployment.md) |
| References | [Imported kit integration](reference/vibe-coding-kit-integration.md), [unaltered source archive](reference/vibe-coding-kit/README.md) |

## Maintenance

- Keep product rules in the specification and wire contracts in `api/`; summaries link to them.
- Keep plans and actual progress separate. R00–R23 are locally verified; no public deployment/release is claimed. See [release readiness](verification/release-readiness.md), [API performance evidence](performance/api-baseline.md), [frontend performance evidence](performance/frontend-baseline.md), [integrated acceptance](verification/product-acceptance.md) and [identity verification](verification/2026-11-09-identity.md) for results and limits.
- Store new guides, decisions and evidence in their matching directories instead of adding loose root documents.
- Agent definitions are maintained in `agents/` and synchronized with discovery copies in `.codex/agents/` at the repository root.
- Treat imported originals under `reference/vibe-coding-kit/` as historical reference, not active agent instructions.

On 2026-11-09, existing documents were relocated into these categories and their links updated. Application source, API artifact paths and build entry points were preserved; this reorganization does not implement additional product capabilities.
