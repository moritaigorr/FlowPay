# FlowPay quality checklist

Adapted from the supplied Vibe Coding Kit. Select relevant checks by phase, exposure and actual impact. Existing roadmap acceptance tests remain mandatory for their tasks; a local/synthetic label does not waive payment consistency, authorization or concurrency requirements.

Use PASS / FAIL / NOT RUN / NOT APPLICABLE with evidence. A checkbox or manual inspection is not an automated test result.

## Proportional verification

| Change | Expected verification |
|---|---|
| Docs/reference import | File integrity, local links, consistency and unchanged implementation status |
| Static visual concept | Content/constraint review; clearly label as nonfunctional and unapproved until selected |
| Rendered UI | Configured lint/typecheck/build, meaningful interaction tests, mobile/desktop/keyboard inspection |
| Payment/auth/schema | Domain plus real PostgreSQL integration, tenant isolation, atomicity, boundary/concurrency and relevant HTTP tests |
| Integrated demonstration | Real browser/API/data journeys, expiry/failure states, proxy/session checks and independent review |
| Performance work | Reproducible baseline/comparison and correctness checks; see docs/guides/performance-guidelines.md |
| Public release | Actual environment configuration, security/critical journeys, truthful remaining limits and explicit release authorization |

## Requirements and architecture

- [ ] Task requirements and dependencies match PRD/spec/roadmap.
- [ ] File ownership and module boundaries are preserved.
- [ ] DTO/schema/dependency changes are deliberate and documented.
- [ ] No unsupported features or structural refactor were added incidentally.

## Implementation and correctness

- [ ] Actual build, lint/typecheck and relevant tests executed with recorded commands/results.
- [ ] Exact money, input limits, errors, retries and final-state rules meet contract.
- [ ] Database constraints, migration behavior, history/idempotency atomicity and relevant races checked against PostgreSQL.
- [ ] Regressions and observable failure paths checked; no test skipped merely to obtain green output.

## Security and data

- [ ] Tenant identity derives from authenticated state and reads/writes remain scoped.
- [ ] Session, logout, expiry, cookies and CSRF checked where affected.
- [ ] No secrets/session files/raw personal data are committed, exposed to the frontend or unnecessarily logged.
- [ ] Demo isolation, quotas, cleanup and capacity/rate failure behavior checked where relevant.
- [ ] Dependency review uses available tools honestly; record unavailable checks.

## Interface and design

- [ ] Implementation follows the selected design target and documented tokens; selection is not inferred from an unapproved mockup.
- [ ] Responsive layout, long content, keyboard, focus, labels and actual contrast checked.
- [ ] Loading, empty, filtered-empty, failure, success and expired-session states exist where relevant.
- [ ] Reduced-motion/opaque fallbacks preserve operation; visual effects are purposeful and measured when needed.
- [ ] Financial UI reflects real API data and exposes only supported actions.

## Operation, evidence and learning

- [ ] Setup/env documentation, CI commands and observability match actual behavior.
- [ ] Performance claims have command/source/environment/dataset/results; local measurements are not production capacity.
- [ ] Explain the important choice, alternative and concept using actual project files.
- [ ] Separate internal evidence, official sources consulted now, suggested reading and inference.
- [ ] Distinguish planned, implemented, verified, locally ready and publicly deployed.

## Task evidence format

| Check | Status | Actual evidence | Remaining action |
|---|---|---|---|
| Relevant check | NOT RUN until verified | Command/result, file/symbol or screenshot | Specific follow-up |

Do not fill all rows for trivial changes; use the relevant scope and document material omissions. No application checks or security audit are implied by importing this checklist. Complete the task only under the roadmap's actual acceptance criteria.
