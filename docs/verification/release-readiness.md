# FlowPay local release readiness

**Date:** 2026-11-10  
**Result:** R23 passed for local portfolio use. This does not mean the application is publicly deployed or production-ready.

## Rebuilt application and database

- `docker compose build --no-cache backend frontend` — passed. The frontend ran `npm ci` and a production build inside the image. npm reported 0 vulnerabilities.
- `docker compose up --detach --force-recreate --wait` — database, backend and frontend all became healthy. The named PostgreSQL volume was retained; Flyway's newest versioned migration was `2`, successful.
- `npm run check:local` — passed all six checks: SPA/direct route, proxied Java health, canonical OpenAPI bytes, same-origin docs/assets, API fail-closed behavior, and CSRF plus login/demo limits.
- The frontend proxy was restored to its normal configuration: login `5r/m`, demo `2r/m`; its `/healthz` returned `200 ok`.

## Full verification

| Check | Result |
|---|---|
| Java 21.0.13 `.\mvnw.cmd -B verify` through `scripts/use-java.ps1` | 12 unit/architecture + 103 PostgreSQL 18.6 integration tests; 0 failures/errors/skips |
| `npm ci` | 354 packages installed; 0 vulnerabilities |
| `npm run lint` | Passed |
| `npm run typecheck` | Passed |
| `npm test` | 15 files, 80 tests passed |
| `npm run build` | Passed; production Vite bundle generated |
| Playwright `npm run test:e2e` against rebuilt Compose | 12 passed; the default-rate case was intentionally run separately |
| Isolated default proxy rate-limit journey | 1 passed; 3 demo entries fit the immediate burst and the next returned 429 |
| Independent read-only R23 review | PASS; no actionable findings |

The routine browser suite temporarily raised the demo proxy rate for its concurrent isolated sessions; its `finally` path recreated the proxy with defaults. The separate 429 case then passed on that restored default proxy. The working browser target remains `http://127.0.0.1:8080`.

## Demo data and reset boundary

The demo-lifecycle integration tests use PostgreSQL 18.6/Testcontainers and a controlled clock to cover the 24-hour expiry, same-session reuse, isolation, transactional seed rollback, expired-write races, scheduled cleanup and preservation of conventional tenants. The browser tests cover isolated workspaces, session expiry handling, reload flows and history. No 24-hour wait was performed during R23.

The local database is a named volume. `docker compose down` removes local containers/network but preserves that data. To deliberately erase this project's local data, first confirm the repository root and Compose project `flowpay`, then run `docker compose down --volumes`; this is destructive and was not run for this delivery. Setup recreates the volume on the next start. Do not use Docker Desktop factory reset or global prune for a project reset.

## Performance evidence

- [API baseline](../performance/api-baseline.md): real PostgreSQL and k6, four profiles, exact synthetic-tenant cleanup. One local sample per profile; directional only.
- [Frontend baseline](../performance/frontend-baseline.md): 18 authenticated Lighthouse runs, three runs across Overview, payment list and detail on mobile/desktop emulation. Local lab evidence only.
- An independent review checked the sanitization, route validation, measurements, and report claims. No before/after optimization or real-user INP is claimed.

## Remaining external or unavailable verification

- GitHub Actions is configured but has not been run remotely.
- There is no public repository release, demo URL, API host, TLS endpoint or deployment. Secure-cookie behavior over real HTTPS is not end-to-end verified.
- Native browser 200% zoom, screen-reader behavior and field-user Core Web Vitals/INP are not verified.
- Lighthouse mobile LCP and the payment-list unused-JavaScript/desktop layout-shift findings remain candidates for a separate evidence-led optimization task.

No public account, paid infrastructure, public repository publication or deployment was created. Local readiness must not be presented as production capacity, uptime, user traffic or real payment processing.
