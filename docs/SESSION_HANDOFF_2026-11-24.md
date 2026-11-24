# FlowPay handoff — 2026-11-24

## Current increment

Implemented company selection for identities with multiple memberships.

- `GET /api/auth/session` returns the authorized membership list while preserving the legacy single-company fields.
- The SPA automatically uses a sole membership, remembers a valid selection locally, prompts when the identity has multiple memberships, and exposes an active-company selector.
- Business API calls send `X-FlowPay-Tenant`; `JdbcTenantAccess` validates the requested ID against the authenticated email and membership for each operation. A client-supplied ID never grants access.
- Unselected multi-company calls fail closed with 409, invalid IDs return 400, and IDs without a membership return 403.
- Added a PostgreSQL integration scenario for two memberships, per-company data isolation, missing/invalid selection, and unauthorized selection.

## Verification

- `npm.cmd run lint` — passed.
- `npm.cmd run build` — passed (TypeScript and Vite production build).
- Compiled the changed Java API and integration test with `javac --release 21` using the local Maven dependency classpath — passed.
- `git diff --check` — passed.
- The Testcontainers integration test was not executed in this increment; Maven is not on PATH and Docker availability was not verified. The compile check does not prove migration/runtime behavior.

## Next work

1. Run the new Testcontainers case with the repository's Maven verify command in an environment with Docker.
2. Exercise the active-company selector in a browser using a multi-membership fixture, then check that switching clears previous-company view state.
3. Continue the roadmap with abuse limits for local registration/login before any public exposure, then current-revision CI and remaining manual accessibility work.

## Working tree / commits

This increment is recorded in the local commit `Add authorized multi-company selection`. The branch is nine commits ahead of `origin/main`; no push was made. Do not push without a request.
