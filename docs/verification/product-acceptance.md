# Integrated product acceptance

Date: 2026-11-10. Scope: R19 browser and same-origin proxy acceptance for FlowPay, a personal payment simulation. No real money or payment credentials are involved.

## Environment

The suite targets the production-like Compose stack through `http://127.0.0.1:8080`; `FLOWPAY_BASE_URL` overrides it. Playwright 1.65.0 is pinned in `frontend/package.json`, and the suite uses one worker. Routine multi-session tests use `FLOWPAY_DEMO_RATE=1000r/m` only in the isolated E2E Compose environment. The limiter case runs separately against a newly created proxy with the application default `2r/m`, `burst=2`, using `FLOWPAY_TEST_RATE_LIMIT=1`. Nginx accepts the initial token plus two burst requests immediately, so request four is the first expected 429.

## Acceptance evidence

| Check | Status | Evidence / follow-up |
|---|---|---|
| Guided create -> exact replay -> process -> history and reload prompt | PASS | `npm run test:e2e`: real UI/API; same payment ID and replay header; processed state, history and reload prompt. |
| Demo rate limiting | PASS | Dedicated clean-proxy run with default settings: three immediate demo entries succeeded, next request returned 429. |
| Customer creation, payment approval/decline/cancel, filters and direct/reload detail navigation | PASS | `e2e/payments.spec.ts`; payment IDs came from actual create responses and all final histories were displayed. |
| Swagger UI and OpenAPI through same-origin proxy | PASS | YAML returned 200 with YAML MIME and payment route; bundled Swagger UI and assets rendered. |
| Tenant isolation | PASS | Independent real browser contexts provisioned two demo companies, customers and payments; foreign customer/payment reads returned 404. |
| Logout/session guard | PASS | Logout followed by `/auth/me` 401 and protected route redirect. |
| Invalidated session while submitting a payment form | PASS | Server logout invalidated the live session before submit; payment POST returned 401, login showed expiry and no payment was created. This does not wait for the configured 30-minute inactivity timeout. |
| Logout while a read is pending, then new demo entry | PASS | A held old-session payment read crossed logout and a new demo login; new company and payment IDs were distinct and old data did not reappear. |
| Backend failure and retry | PASS | Browser received four controlled 503 responses for the list request, displayed the failure state, then the manual retry reached the real backend and showed its successful empty result. |
| CSRF and HttpOnly cookie | PASS | Missing CSRF on demo entry returned 403; browser JavaScript could not read the session cookie. |
| Keyboard, 375/768/1440 layouts, zoom-equivalent viewport | PASS with limits | Skip-link focus and activation, main focus and keyboard form toggle passed. No page-level horizontal overflow at 375/768/1440 or the 360 CSS-pixel viewport equivalent to 200% on 720 physical pixels. At narrower widths, the payment table uses its horizontal scroll container. No dialog component is used by these journeys. |
| Captures and browser errors | PASS | [375 px](screenshots/r19-overview-375.png), [768 px](screenshots/r19-overview-768.png), [1440 px](screenshots/r19-overview-1440.png); no post-login console/runtime errors or failed requests. |
| HTTPS `Secure` cookie and screen reader | NOT RUN | Local Compose uses HTTP; HTTPS proxy and screen-reader runtime are not configured. Native browser zoom was not automated; the CSS viewport-equivalent check is not a substitute for it. |

## Run status and limits

Local verification on 2026-11-10 used the production-like Compose proxy, Java backend and PostgreSQL 18.6. `npm run test:e2e` passed 12 tests with one deliberately skipped rate-limit test; the rate-limit test separately passed 1/1 against a clean proxy with the default limiter. Frontend lint, typecheck, 80 Vitest tests and production build passed. The Playwright suite was not run on GitHub Actions, so remote CI execution remains unverified.

The current 768/375 captures show the recent-payments table within a horizontally scrollable container; the page itself has no horizontal overflow. The mobile screenshot intentionally represents synthetic demo data. Native browser zoom, the 30-minute inactivity timeout, HTTPS `Secure` cookie behavior and assistive technology remain unverified.

## Reproduction

```powershell
# From the repository root, first raise the proxy rate only in the isolated local test stack.
$env:FLOWPAY_DEMO_RATE = '1000r/m'
docker compose up --detach --force-recreate frontend
Set-Location frontend
npm run test:e2e

# Back at the repository root, recreate the proxy to restore the default and clear its in-memory bucket.
Set-Location ..
Remove-Item Env:FLOWPAY_DEMO_RATE -ErrorAction SilentlyContinue
docker compose up --detach --force-recreate frontend
Set-Location frontend
$env:FLOWPAY_TEST_RATE_LIMIT = '1'
npm run test:e2e -- --grep "returns 429"
```

The Compose database contains only local demo/test records created by these journeys; no payment or financial credentials are used.
