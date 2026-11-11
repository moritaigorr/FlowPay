# Guided demo verification

Date: 2026-11-10. Task: R18. Environment: local Docker Compose, PostgreSQL 18 and the production frontend behind the same-origin proxy. FlowPay remains a personal payment simulation; no real money or payment credentials are used.

## Implemented behavior

- The Overview links to `/demo`. An authenticated non-demo operator can explicitly enter a separate demo workspace; the login page continues to offer the normal isolated demo entry.
- The guide creates a real pending payment, retains the exact normalized request and idempotency key only in React memory, and replays both unchanged.
- Replay evidence is accepted only when the actual response has the same payment ID and `Idempotency-Replayed: true`. It then processes the payment and reads the current event history from the API.
- The UI explains that replay returns the original 201/PENDING creation response while separate API calls show the current final state and history.
- A `sessionStorage` boolean is used only to detect an interrupted guide. The payload/key are never stored. After reload, the screen explicitly offers a fresh restart and does not claim prior replay evidence.
- Optional request/response disclosure shows the captured request, original response, replay header and replay response. Quota recovery signs out and routes through LoginPage, which owns the fresh-session request and displays 429/503 failures.

## Evidence

- `frontend`: `npm test` — 80 tests across 15 files passed; `npm run lint`, `npm run typecheck`, and `npm run build` passed.
- `docker compose up -d --build frontend` — PostgreSQL/backend reported healthy and frontend started; latest production frontend image served through the proxy.
- `.tools/r18-browser-acceptance.mjs` — two separate browser contexts completed create -> exact replay -> process -> history. The contexts had distinct company, customer and payment IDs; one approved, the other deterministically declined. Both responses returned HTTP 201, the replay returned the same ID and `Idempotency-Replayed: true`. Reload displayed the explicit restart prompt; no replay button or evidence was fabricated. Mobile/desktop overflow checks passed at 375 and 1440 CSS pixels; no runtime errors.
- Component/auth tests verify replay body/key equality, response header and ID, restart prompt, quota handoff/marker clearing, and visible 401/409/429/503 messages. The fresh-session recovery test confirms logout occurs before a new demo request, with 429/503 failures remaining visible on LoginPage.
- Screenshots: ignored local artifacts `.tools/r18-guided-demo-1440-session-1.png` and `.tools/r18-guided-demo-375-session-2.png`.
- Independent review found and led to correction of a quota-recovery handoff issue; follow-up review found no remaining actionable findings.

## Limits and learning

The browser run directly exercised success flows and isolation. 401/409/429/503 UI mappings and fresh-session failure behavior were component/auth tests; this run did not fill the demo quota or intentionally saturate capacity/rate limits. Native 200% zoom and screen-reader testing remain in integrated acceptance.

Interview takeaway: idempotency is demonstrated by replaying the exact original POST and key, then comparing the returned ID and server header. The stored create response remains immutable; current payment state comes from a separate read after the transaction.
