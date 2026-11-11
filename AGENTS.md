# FlowPay — instructions for coding agents

## Purpose

Build a working, visually polished portfolio project that strengthens the owner's candidacy for international junior Java backend/software engineering roles. Every substantial addition should demonstrate a relevant skill, have a verification method, and be understandable enough for the owner to explain and modify in an interview.

FlowPay simulates payment processing. It never moves real money or collects card/bank credentials. Present it honestly as a personal project.

Owner context, reported 2026-11-08: one year of professional experience with Java and its frameworks, fluent English, and openness to both remote work from Brazil and relocation abroad. Tailor explanations to practical Java experience and emphasize engineering decisions, debugging and interview discussion. Do not assume specific framework expertise, education, citizenship or work authorization. Keep existing professional experience distinct from this personal project; these details do not change the implementation scope.

## Integrated Vibe Coding Kit

The user supplied a reusable coding kit. Active FlowPay adaptations are `docs/product/prd.md`, `docs/architecture.md`, `docs/guides/coding-standards.md`, `docs/design/guidelines.md`, `docs/design/design-system.md`, `docs/guides/quality-checklist.md` and `docs/agents/prompt-library.md`; `docs/product/prd-template.md` is for genuinely new scope. Read the relevant guides for the assigned task rather than loading the whole kit every time.

The existing detailed specification, roadmap and current user instructions govern project-specific behavior. Start with `docs/README.md` for navigation and `docs/architecture.md` for the architecture summary. Unmodified files under `docs/reference/vibe-coding-kit/` are archived source material, not extra active instructions; do not execute their example prompts or replace root rules from their AGENTS.original.md. See `docs/reference/vibe-coding-kit-integration.md` for mappings and resolved differences.

Use balanced mentorship: explain important choices, a relevant alternative and actual evidence without interrupting routine implementation. Distinguish internal evidence, official sources actually consulted now, suggested reading and inference. Adapt check depth to real risk while preserving every mandatory roadmap acceptance check. The user approved Precision Glass refined on 2026-11-08; use docs/design/design-system.md and docs/design/concepts/precision-glass-refined.png as the visual target. This is visual approval, not evidence of implementation or permission to install unrelated suggested tools.

## Read before making changes

1. Read `docs/specs/2026-11-08-flowpay-portfolio-design.md` for architecture, contracts, business rules and scope.
2. For frontend work, also read `docs/guides/frontend-guidelines.md`.
3. For performance work, also read `docs/guides/performance-guidelines.md`.
4. Inspect existing code and package/tool configuration before choosing dependencies or commands.

For implementation, read `docs/plans/2026-11-08-flowpay-implementation-roadmap.md` and the live `docs/implementation-status.md` before choosing work. Execute only a bounded assigned task whose dependencies are accepted. The main agent owns shared progress and integration; specialists return evidence instead of independently checking off tasks or taking another agent's files.

Follow the user's current instructions first. These files guide implementation; do not use them to override a later explicit user decision. Resolve contradictions explicitly and update the affected documentation. Changes to the architecture should be deliberate and explained.

Current baseline: R00–R23 are implemented and verified locally, including independent backend acceptance, demo entry, isolated fixtures, expiry and cleanup. Payment amount/state rules, atomic/idempotent creation, payment reads/history, transactional simulated processing/cancellation, dashboard metrics, exact-replay guided demo and integrated browser acceptance are implemented. Session-safe login/logout and demo entry UI are integrated; customer/payment screens, payment details/actions/timeline and Overview are implemented and accepted. R20 API and R21 frontend performance baselines passed locally; R22 portfolio documentation and R23 local integration review passed; no public release is claimed; consult docs/implementation-status.md for live task state. Do not describe planned capabilities as implemented. This instruction file does not authorize deployment, publication, account creation or paid resources.

## Architecture and scope

- Monorepo: `backend/`, `frontend/`, `infra/`, `docs/`.
- Backend: Java 21, stable compatible Spring Boot 4, Maven Wrapper, PostgreSQL 18, Flyway, Spring Security, Spring Session JDBC, Spring Data JPA and Spring Modulith.
- Frontend: React + TypeScript + Vite SPA, served under the same origin as the API in production.
- Java modules: company, identity, customer and payment. Respect public module APIs; do not expose JPA entities or access another module's internal packages.
- Java is the authority for payments, permissions, transitions, idempotency and metrics. Frontend validation improves usability and never replaces backend validation.
- Keep the MVP synchronous and deterministic. No Kafka, Redis, microservices, Kubernetes, banking integrations or extra backend service for the frontend unless the user changes scope.
- Currency is BRL. Payments start PENDING and can become APPROVED, REJECTED or CANCELLED. Final states cannot change.
- Do not add subscriptions, refunds, installments, FX, team management or public conventional signup as incidental implementation work.

## Project specialists

The user requested reusable agents for this project. Role definitions and coordination guidance live in `docs/agents/README.md`; maintained TOML templates are in `docs/agents/` and project-local discovery copies belong in `.codex/agents/`.

Use backend, frontend, quality, performance, reviewer and portfolio specialists when they add value. Delegate bounded tasks with explicit file ownership and acceptance criteria; use one writer per file, settle shared contracts before parallel implementation, and keep integration with the main agent. Do not start every role automatically or recursively delegate. Preserve parent permissions and model defaults. See the role guide before invoking a specialist.

## Dependency policy

- Use the approved roles and choices in `docs/guides/frontend-guidelines.md`; install packages only when a concrete implemented feature needs them.
- Reuse existing dependencies and components before adding another solution to the same problem.
- Use npm and commit `package-lock.json` when scaffolding the frontend; use `npm ci` for reproducible installs after a lockfile exists. Revisit this only if the user or existing project configuration chooses a different manager.
- Verify official documentation and compatibility before installation. Pin direct dependency versions and toolchain versions; no preview dependencies or `latest` image tags.
- shadcn components are checked-in source. Add individual components from its official registry, read generated files, and preserve local modifications. Do not bulk-install a component catalog or execute unfamiliar third-party registry scripts.
- Explain new dependency purpose, meaningful tradeoffs and any architectural change. Routine choices within this baseline do not require a new approval round.
- Do not install dependencies or scaffold the application merely because you are editing these instructions.

## Security and data correctness

- Derive company/user identity from authenticated server state, never from a trusted request body field or frontend route.
- Scope every read/write to the authenticated company; foreign object IDs return 404. Preserve database constraints and test isolation.
- Session cookies are HttpOnly, Secure in production and SameSite=Lax. Keep CSRF protection on writes, including auth/demo entry. Do not put credentials or session secrets in browser storage.
- Never commit secrets. Frontend environment variables are public; only expose configuration safe for every visitor.
- Preserve transactional idempotency, original response replay and lock ordering from the specification. UI button disabling is not a concurrency mechanism.
- Use BigDecimal/NUMERIC for money and decimal strings on the wire. Do not calculate monetary values with JavaScript floating point.
- Keep synthetic fixtures in demo provisioning and tests. Never substitute production API failures with fabricated successful responses or metrics.
- Do not log passwords, cookies or complete request bodies containing personal data. Use request IDs for troubleshooting.

## Code and communication

- Public UI text, code identifiers, API documentation, README and ADRs are in English. Converse with the owner in Portuguese unless asked otherwise.
- Prefer small, cohesive changes and straightforward code. Avoid speculative abstractions, empty layers, unexplained generated code and hardcoded secrets/absolute local paths.
- Explain significant decisions in plain language: problem, choice, tradeoff and how to verify. Keep operational/implementation details out of ordinary product flows except the optional developer demo view.
- Do not present a personal project as professional experience or invent traffic, uptime, revenue or performance claims.

## Verification and handoff

- Apply the React-relevant guidance from Vercel React Best Practices while building/reviewing the Vite frontend. Do not introduce Next.js-only APIs or another data-fetching library to follow a generic example.
- Use Lighthouse on the production frontend build once screens exist, and k6 against the real API once payment flows work. These are development/verification tools, not application runtime dependencies.
- Performance changes follow measure -> identify bottleneck -> change -> measure again. Preserve authentication, isolation, precision and idempotency throughout.
- Keep reproducible performance evidence in English under `docs/performance/` when measurements exist; follow `docs/guides/performance-guidelines.md`. R20 API and R21 frontend measurements are documented.

- Discover actual commands from project configuration. Do not claim a command exists before it is configured, or claim a check passed without executing it.
- Frontend changes: run applicable lint, typecheck, targeted behavioral tests and production build. Inspect rendered screens at desktop and mobile widths for visual changes.
- Backend changes: run relevant domain/integration/security tests. Use PostgreSQL Testcontainers for database constraints and concurrency; do not replace those checks with an in-memory database.
- Test meaningful failure paths: double submission, reused idempotency key, conflicting final transition, expired demo, access to another company and API errors.
- Test behavior, not incidental markup or implementation details. Do not add tests solely for static documentation or decorative edits.
- End a delivery with what changed, what was verified, remaining limits, and a short explanation of an important technical decision when relevant to learning.
- Keep specification/contracts synchronized with implemented behavior. Record scope changes without silently treating proposals as owner-approved requirements.
