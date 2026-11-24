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
- Current local branch is eleven commits ahead of `origin/main`; no changes have been pushed. This browser test and its documentation update are pending a local commit.

## Next work

1. Run migration V16 and authentication Testcontainers tests, including concurrent reservations.
2. Configure and verify the actual trusted proxy CIDRs and upstream header behavior.
3. Check current-revision GitHub Actions and complete the remaining manual accessibility review.
