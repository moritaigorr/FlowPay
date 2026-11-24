# FlowPay handoff — 2026-11-24

## Completed increment: multi-company selection

Local commit: `Add authorized multi-company selection` (branch was nine commits ahead of `origin/main`; not pushed).

- Auth session lists authorized memberships; the SPA prompts/selects and switches company.
- Business requests send `X-FlowPay-Tenant`; the API revalidates identity plus membership for every request.
- Integration coverage added for two-tenant isolation and invalid/unselected IDs.

## Current increment: authentication throttling

Recorded in local commit `Add persistent authentication throttling`; the branch is ten commits ahead of `origin/main` and has not been pushed.

- Added migration V16 and an atomic PostgreSQL-backed rate limiter shared across application instances.
- Login allows five attempts per 15 minutes for socket peer plus normalized identifier; success clears the bucket. Registration allows five attempts per hour per socket peer. Throttling returns 429 with `Retry-After`.
- Bucket identity is stored as HMAC. `FLOWPAY_RATE_LIMIT_HMAC_KEY` is required outside `local` and needs at least 32 UTF-8 bytes. A scheduled cleanup deletes buckets older than two days.
- `ClientAddressResolver` uses `getRemoteAddr()` as the trust anchor; forwarding headers are consumed only from a peer in `FLOWPAY_TRUSTED_PROXY_CIDRS`. It combines repeated headers, walks right to left, limits header size/hops, and fails back to the peer when parsing is invalid. Seven focused resolver test methods passed. Actual CIDR ranges and upstream append behavior must be checked against deployment. Distributed credential stuffing remains out of scope.
- Added integration cases for login throttling, registration cap, and concurrent PostgreSQL reservations.

## Verification and limits

- `npm.cmd run lint` and `npm.cmd run build` passed during the preceding frontend increment; this increment does not change frontend code.
- Java 21 compilation of the changed API and integration test sources passed. Seven focused proxy-resolution unit tests passed using a local reflective runner.
- Testcontainers were not run: Maven and Docker are unavailable. A read-only PostgreSQL probe reached localhost but authentication with the documented local defaults failed; no schema or application data was changed. Migration/runtime behavior and the concurrency test remain unverified.
- Do not expose registration publicly before trusted-proxy handling and deployed limits are verified.

## Additional verified increment: company switching in the browser

- Added an isolated Playwright journey for choosing a company, switching to another authorized company, and rendering a `READER` workspace.
- The test confirms no business API call occurs before company selection and checks the tenant header on dashboard requests. Axe scans passed for the chooser and read-only workspace.
- The API is mocked in this browser test; this proves the SPA flow, not server authorization or PostgreSQL behavior. `npm.cmd run lint`, `npm.cmd run build`, and the focused Playwright case passed.
- At that checkpoint, the local branch was eleven commits ahead of `origin/main`; the browser test and its documentation are now committed locally.

## Completed increment: audited balance adjustment

- Added migration V17, a tenant-scoped idempotent `ADJUSTMENT` cash movement, and a required reason plus authenticated actor FK on the immutable cash record. Only `OWNER`/`MANAGER` can create it; the account must be active.
- Added the UI form and transaction-history reason display. `READER` sees the workspace in read-only mode with write actions hidden. Adjustments change controlled balances but are excluded from operating inflow/outflow and category totals.
- Added PostgreSQL integration coverage for manager/reader access, actor/reason persistence, idempotent replay/conflict, and report totals, plus a real-API E2E scenario. The focused mocked browser journey also passed and verifies the adjustment action is hidden for `READER`.
- Java 21 compilation of changed service/controller/reporting, tenant access and integration test sources passed. Frontend lint/build passed. Flyway applied all 17 migrations to an empty PostgreSQL 16.15 database. Real API/PostgreSQL 16.15 Playwright journeys passed for title filters and the complete financial flow, including adjustment and report totals. The Testcontainers suite and current GitHub Actions run remain pending.
- The financial E2E also exposed a missing newline between the optional tenant filter and `ORDER BY` in `JdbcTenantAccess`; the fix was compiled and the real selected-tenant journeys passed afterward. A stale double-encoded title assertion and broad status locator in Playwright were corrected based on their runtime snapshots.
- Local branch was 13 commits ahead of `origin/main` before this uncommitted fix; no push was performed.

## Next work

1. Run the Testcontainers migration and adjustment authorization/idempotency cases in CI; local runtime behavior and browser flow passed against PostgreSQL 16.15.
2. Observe current GitHub Actions after synchronizing local commits; nothing was pushed from this workspace.
3. Configure actual trusted proxy CIDRs and complete the remaining manual accessibility review.
