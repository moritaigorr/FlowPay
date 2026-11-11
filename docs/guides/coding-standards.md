# FlowPay coding standards

Adapted from the supplied Vibe Coding Kit. Read AGENTS.md and docs/architecture.md first. Existing project contracts and task ownership take precedence over generic examples.

## Engineering

- Make focused, reviewable changes with clear names and responsibilities. Introduce abstractions only for an actual need.
- Keep business behavior out of HTTP controllers and frontend presentation; follow the modular boundaries already specified.
- Prefer constructor injection in Java. Use explicit validated request/response DTOs and consistent problem+json errors; do not expose JPA entities through HTTP.
- Put transaction boundaries around the consistency requirements of the use case. Preserve atomic history/idempotency and company-before-payment locking.
- Validate money and states in the backend, and enforce relevant constraints in PostgreSQL. Never replace real database integration checks with an in-memory approximation.
- Parameterize queries, bound page sizes and payloads, inspect N+1 behavior, and justify indexes with real access patterns. Follow docs/guides/performance-guidelines.md for optimization.
- Evolve schema with forward-only Flyway migrations. Do not edit applied migrations or silently change an existing contract.

## Frontend

- TypeScript strict mode; explicit contract types and no unexplained any. Keep presentation, feature state and API access in their agreed locations.
- Follow docs/guides/frontend-guidelines.md for the actual library policy. React Query owns remote data; the URL owns shareable filters; component state owns transient UI.
- Keep form validation consistent with server limits, explicit error/loading/empty states and secure auth/CSRF handling.
- Use docs/design/guidelines.md and docs/design/design-system.md for the selected visual language. Generated mockups do not establish component behavior, accessibility or measured performance.
- Use semantic markup, labels, focus indicators, keyboard navigation and reduced-motion behavior. Preserve critical actions when effects are disabled.

## Delivery and dependencies

- Use pinned stable dependencies and the existing tools. Documentation examples do not authorize replacing the stack.
- Commit small verified changes once Git is established, with documented commands and no credentials/session artifacts.
- Keep CI, env examples, setup instructions and contracts consistent with actual implementation. Do not report a workflow as passing until a run exists.
- Check docs/guides/quality-checklist.md for the relevant risk/phase. Its adaptive policy never reduces the already assigned correctness/security tests.

## Balanced mentorship and evidence

For significant decisions, explain the problem, the chosen approach, one relevant alternative, the tradeoff and a concise concept tied to real files. Tailor this to the owner's year of Java experience and fluent English; communicate in Portuguese and keep public code/docs in English. An exercise can be helpful but does not block delivery.

Separate evidence sources: internal file/symbol, official documentation actually opened in the current task, suggested reading not checked now, and inference. Cite only evidence actually obtained; do not invent tests, metrics or consultation claims. Keep routine delivery reports short and expand them when the change needs explanation.
