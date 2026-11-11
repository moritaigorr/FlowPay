# FlowPay — performance measurement plan

Status: user-approved performance baseline guidance. R20 k6 API and R21 Lighthouse frontend scripts and local measurements are recorded in `docs/performance/`; this guide continues to govern comparable reruns and any measured optimization.

## Goal and tools

Produce a responsive payment dashboard and evidence of backend performance that the owner can reproduce and explain in an international interview.

- Vercel React Best Practices: apply relevant React/Vite guidance during component construction and review. Existing skills may supply this guidance; do not install duplicate skill packs just to satisfy a checklist.
- Lighthouse: audit the production-built frontend after real screens and authenticated flows exist.
- k6: measure the real Java API with PostgreSQL after payment flows and correctness tests work.
- Browser and React profiling: diagnose loading and rendering when a concrete bottleneck appears.

Do not add benchmark tools to the runtime application. Pin tool versions when configured and document actual commands. React Compiler, Lighthouse CI and automated performance gates can be evaluated later; they are not prerequisites for the first functional slice.

## Working method

1. Define a user scenario and collect a baseline.
2. Inspect the evidence and identify the actual bottleneck.
3. Make a targeted change and explain the expected effect.
4. Rerun the same scenario under comparable conditions.
5. Record results, correctness checks and limitations.

Do not remove authentication, CSRF, tenant filters, monetary validation, history writes or idempotency to improve a benchmark. Preserve relevant correctness tests after optimization. Synthetic test traffic and fixtures must be identified as such.

## Frontend measurement

- Use a production build, not Vite's development server, with a working API and database.
- Cover Overview, Payments and Payment details. Establish an authenticated session through supported login/demo flows; verify the report actually measured the intended screen instead of a redirect or loading shell. Never commit cookies, session files or credentials.
- Record tool/browser versions, build commit or other immutable source identifier, machine, viewport, throttling settings, dataset and backend configuration.
- Collect at least three comparable runs per reported scenario, preserve individual results and report the median. Label cold-cache and warm-cache scenarios separately; document session setup and cache-clearing behavior.
- Inspect LCP, CLS, blocking work, network requests and transferred JavaScript, as applicable to the report. Track Lighthouse's diagnostic findings rather than optimizing only its aggregate score.
- Exercise filtering, pagination, payment creation and status changes with browser/React profiling when needed. Measure API waiting separately from frontend processing where possible. Do not label Lighthouse's lab metrics as real-user INP evidence.
- Use explicit Query freshness/refetch settings and mutation invalidation. Prefer server pagination, bounded payloads, useful code splitting and stable layout before adding complex optimization mechanisms.

## API load scenarios

Use a local or dedicated test environment with disposable synthetic data, real PostgreSQL and production-like configuration. Target selection must be explicit; public deployment load tests are not part of this setup task.

Provide deterministic fixture provisioning and cleanup before benchmark execution. Use conventional test tenants for load above demo quotas, retaining all ordinary authorization and payment rules. Measure the actual demo path separately when relevant, including its quota locks and rate limits. Label these profiles so their results cannot be confused.

Required scenarios:

| Scenario | Behavior and assertion |
|---|---|
| Payment listing | Paginated reads with representative date/status filters; validate response structure and bounded page size |
| Payment details | Retrieve payment and history for authorized users; validate expected ID/company behavior |
| Fresh creation | Unique idempotency key per logical payment; expect 201 and record created IDs |
| Replay | Reuse an existing key and identical payload; expect original response/ID and no extra payment |
| Process/cancel | Use independent pending fixtures; assert the intended final state |
| Concurrent conflict | Separate correctness scenario: competing transitions may yield expected 409, with exactly one final transition |

- Authenticate each virtual user through supported flows, including CSRF. Do not share one session across tenants or disable protections. Keep credentials external to scripts and reports.
- Separate setup/login/fixture costs from steady-state endpoint measurements, and label an end-to-end scenario separately if those costs are included.
- Start with a bounded smoke test, then controlled increasing load. Fix and record virtual users or arrival rate, duration, warm-up, request mix, dataset size, connection pool limits and resource allocation for each comparison.
- For fresh creations use unique keys; for replay reuse exact key/payload pairs. Do not accidentally benchmark only repeated responses while reporting new-payment throughput.
- Record latency p50/p95/p99 by scenario, achieved throughput, unexpected error rate, check failures and resource observations. Record dropped iterations if using an arrival-rate executor.
- Classify expected 409 in the dedicated conflict scenario separately from failures; an unexpected 409, 401, 429 or 5xx in a normal success scenario must remain visible. Do not exclude errors to make a result look faster.
- Examine query counts, JPA N+1 behavior, payload size and PostgreSQL query plans before tuning queries/indexes. Use EXPLAIN ANALYZE only where executing the statement is appropriate for the disposable environment.
- Remember that the load generator can compete with the application for resources on one machine. Report that limitation; do not extrapolate local results to production capacity.

## Budgets and automation

Define numerical budgets after the first reproducible baseline and document their rationale. This plan does not promise a Lighthouse score, response-time target or concurrent-user capacity without evidence.

Include measurement commands and summaries in the project documentation. Once stable, consider a lightweight CI performance smoke test and a separate manually triggered full benchmark. Avoid adding a noisy full load test to every commit. Do not report checks as configured until their scripts/workflows exist.

## Portfolio evidence

Create `docs/performance/` when the first actual measurements exist. Write reports in English with:

1. Question and tested user scenario.
2. Source revision, tool versions, environment, dataset and exact command.
3. Baseline results and the evidence identifying the bottleneck.
4. Change and its technical tradeoff.
5. Comparable results after the change, including individual runs and a concise table.
6. Correctness verification, limits and reproduction steps.

If no improvement is observed, say so. A baseline report is useful even before any optimization. Only use measured outcomes in the README/CV; never invent a before/after improvement or present synthetic traffic as real users.

## Official references

- React guidance: https://github.com/vercel-labs/agent-skills
- Lighthouse: https://developer.chrome.com/docs/lighthouse/overview
- TanStack Query defaults: https://tanstack.com/query/latest/docs/framework/react/guides/important-defaults
- k6: https://grafana.com/docs/k6/latest/
