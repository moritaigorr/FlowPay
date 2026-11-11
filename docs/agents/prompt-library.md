# FlowPay agent prompts

FlowPay-specific adaptations of the supplied kit. Its original 22 prompts remain in docs/reference/vibe-coding-kit/PROMPT-LIBRARY.md. These are optional task starters, not commands that run automatically or grant new permissions.

## 1. Resume the next task

Read AGENTS.md, docs/product/prd.md, docs/architecture.md, the roadmap and docs/implementation-status.md. Identify the next READY task and its dependencies. Summarize the objective, exact file ownership, contracts and acceptance checks. Execute only when implementation is requested; the main agent owns dispatch and status.

## 2. Backend implementation

Use flowpay_backend for task [ID]. Allowed files: [paths]. Follow its interfaces and tests. Preserve company isolation, session/CSRF, exact money, atomic history/idempotency and lock order. Return changed files, actual checks, limitations and the most important tradeoff.

## 3. Frontend implementation

Use flowpay_frontend for task [ID]. Read docs/design/guidelines.md, docs/design/design-system.md and docs/guides/frontend-guidelines.md. Use the selected visual target and actual API contract. Preserve retry keys, session cache isolation and complete interaction/error states. Inspect rendered desktop/mobile results and report actual evidence.

## 4. Visual refinement

Refine [screen/component] using [explicit selected reference]. State what remains fixed and what changes. Preserve supported actions, financial readability and accessible fallbacks. For a new broad direction, compare visual alternatives before implementation; avoid restarting selection for small changes inside an already chosen direction.

## 5. Independent review

Use flowpay_reviewer on [exact artifact/revision]. Stay read-only. Prioritize concrete correctness, contract, isolation, transaction, concurrency and meaningful verification gaps. Give file/line, trigger, impact and fix direction; separate observed facts from assumptions.

## 6. Integrated verification

Use flowpay_quality on [task/journey], with writable tests limited to [paths]. Follow docs/guides/quality-checklist.md. Reproduce behavior against real backend/PostgreSQL where required. Do not repair another owner's application files or invent passing results; return precise defects and evidence.

## 7. Performance investigation

Use flowpay_performance for [scenario], targeting [explicit local/test environment]. Read docs/guides/performance-guidelines.md. Establish a baseline, identify a bottleneck, coordinate a scoped fix and repeat comparable measurements. Keep authentication and invariants enabled; write only actual results.

## 8. Explain the engineering

Explain [actual change/concept] at the level of someone with a year of Java experience. Show the real execution path, the key decision and one alternative/tradeoff. Cite actual files and official documentation opened now; label unconsulted reading as suggested. Offer a brief exercise only if useful.

## 9. Prepare portfolio evidence

Use flowpay_portfolio for [verified task/revision]. Produce concise English setup/demo/architecture/interview material grounded in real files, screenshots and reports. Distinguish professional experience from this personal simulation and all plans from actual outcomes.

## 10. Release-readiness review

Review [explicit scope] against docs/guides/quality-checklist.md and R23. Record PASS/FAIL/NOT RUN/NOT APPLICABLE with evidence. Identify material remaining steps. This review does not authorize publication, deployment, account changes or paid resources.
