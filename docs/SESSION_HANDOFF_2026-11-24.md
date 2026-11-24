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
- Only `HttpServletRequest.getRemoteAddr()` is trusted. Forwarded headers are ignored. A trusted-proxy model is required before deployment behind a reverse proxy; distributed credential stuffing remains out of scope.
- Added integration cases for login throttling, registration cap, and concurrent PostgreSQL reservations.

## Verification and limits

- `npm.cmd run lint` and `npm.cmd run build` passed during the preceding frontend increment; this increment does not change frontend code.
- Java 21 compilation of the changed API and integration test sources passed.
- Testcontainers were not run: Maven and Docker are unavailable in this environment. Migration/runtime behavior and the concurrency test remain unverified.
- Do not expose registration publicly before trusted-proxy handling and deployed limits are verified.

## Next work

1. Run migration V16 and authentication Testcontainers tests, including concurrent reservations.
2. Define trusted-proxy client-address handling for the actual deployment.
3. Check current-revision GitHub Actions and complete the remaining manual accessibility review.
