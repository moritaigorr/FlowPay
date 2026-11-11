# Payment details UI verification

Date: 2026-11-10. Task: R16. Environment: local Docker Compose, PostgreSQL 18, production-built frontend behind the same-origin proxy. This is a simulated payment portfolio project; no real money is processed.

## Implemented behavior

- The authenticated `/payments/{paymentId}` route loads current payment data and immutable event history from the API. Malformed IDs use the app not-found route; a syntactically valid missing or foreign ID is reported as unavailable by the API.
- Pending payments offer process and cancel. The displayed state changes only after the server confirms it. Final payments expose disabled actions.
- Process and cancel success invalidate the payment, event history, payment lists, dashboard summary and recent activity. A `409 PAYMENT_FINAL_STATE` also refreshes current detail/history and explains the conflict.
- The timeline sorts events by sequence and displays timestamp, status, and the simulated decline reason. The configured scenario is explained as a deterministic expected outcome, separate from current status.
- Payment IDs in the list link to details. Lazy route navigation focuses the rendered page heading, including after the details chunk loads.

## Evidence

- `frontend`: `npm test` — 69 tests across 14 files passed; `npm run lint`, `npm run typecheck`, and `npm run build` passed.
- `docker compose up -d --build` — PostgreSQL and backend reported healthy, frontend container started, and the production SPA served through the proxy.
- `.tools/r16-browser-acceptance.mjs` — real browser/API journey passed: `APPROVE -> APPROVED`, `DECLINE -> REJECTED/SIMULATED_DECLINE`, cancellation -> `CANCELLED`, and a server-side concurrent transition produced `409` then refreshed the stale page to `APPROVED`. A valid-format missing ID produced the not-found UI. The processing action was activated with the keyboard, route focus moved to the loaded heading, and cancellation was activated from the keyboard.
- Browser layout checks passed without horizontal overflow at 1440, 768, 720 and 375 CSS pixels. A page-error listener found no runtime errors. The browser logged expected HTTP responses for initial unauthenticated session probing and intentional 404/409 cases; the UI handled them as specified.
- Screenshots: ignored local artifacts `.tools/r16-payment-details-1440.png`, `.tools/r16-payment-details-768.png` and `.tools/r16-payment-details-375.png`.
- Independent read-only review: no actionable findings.

## Limits and learning

Native browser 200% zoom and screen-reader behavior were not exercised; those remain part of integrated acceptance. The 720 CSS-pixel check is a narrow-layout check, not a claim of native zoom testing.

Interview takeaway: the UI treats the database-backed server response as authoritative. On a conflict, it refetches instead of trying to resolve state locally. The API's row lock and transaction protect the payment/event transition; frontend disabling provides immediate feedback but is not a concurrency guarantee.
