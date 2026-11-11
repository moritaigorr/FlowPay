# Frontend authentication verification — 2026-11-09

Scope: R14 same-origin HTTP, login/logout, guarded routes and session-safe cache. No business screens or demo entry are implemented by this task. R14 accepted after independent review fixes and final real-backend browser verification.

## Behavior and implementation

The typed native-fetch client preserves status, problem code/request ID and field errors, handles empty/204 and non-JSON failures, and passes AbortSignal through requests. CSRF stays in memory and is discarded after authentication changes; the next write obtains the replacement. Cookies remain browser/server owned, with no browser-storage credentials or session material.

TanStack Query 5.107.1 is pinned for remote-data/cache ownership. Registry metadata confirmed React 18/19 peers and official installation/cancellation documentation was consulted before installation. Read retries are bounded; mutation retries are disabled. Session keys include a lifecycle generation and server identity. Logout, expiry and identity changes cancel queries and remove old cache entries, including late promises that ignore cancellation. The tradeoff favors isolation over retaining a warm cache across sessions.

Login uses the existing Precision Glass tokens, accessible labels/pending feedback and focused inline errors. Anonymous protected routes redirect to login and preserve a local destination. Logout failure explicitly offers retry rather than claiming success. Demo access and financial success states are not fabricated.

## Actual checks

- Initial tests failed to resolve the absent HTTP/session modules; existing seven navigation tests passed. Later tests exposed asynchronous test-wait assumptions, corrected to wait for the actual expiry feedback.
- Root independently ran lint, typecheck and 32 tests after the lifecycle correction, then reran the final 33-test suite after the Unicode fix: zero failures. Final production Compose frontend build/typecheck passed; all services were healthy. Worker also ran final lint/typecheck/build successfully.
- Independent review reproduced a focus/authentication race: pending login could be aborted by identity revalidation, and a delayed identity response could reopen the shell after logout. Three new tests failed before the correction. Synchronous transition ownership now suppresses revalidation during auth writes, guards generations and rejects overlapping writes. Scoped re-review independently replayed both sequences and confirmed the fix.
- Root used regular Playwright with actual Chrome against the packaged same-origin backend because the Browser plugin is unavailable. At 1440px and 375px, protected Customers redirected to login; wrong-password feedback, successful login, identity 200, logout and identity 401 passed. Pending login/logout requests were held while focus events were dispatched; no identity revalidation started during either transition.
- Chrome checks confirmed HttpOnly/SameSite=Lax session protection, inaccessible SESSION through document.cookie, empty local/session storage, keyboard order, no whole-page overflow and no runtime errors. Desktop/mobile login and workspace screenshots were visually inspected.
- The same packaged check created a real customer and pending payment via authenticated API writes, then verified byte-identical 201 replay, Location and replay headers. It did not claim a payment form exists.

Reproduce the frontend checks from `frontend/`: `npm run lint`, `npm run typecheck`, `npm test -- --reporter=dot`, `npm run build`. The ignored local Playwright script `.tools/r14-browser-check.mjs` uses a generated isolated operator; credentials are never printed. The coordinator retained that synthetic fixture temporarily for subsequent integrated checks; it is not a default account or public demo. Original `.env` was preserved.

## Limits

A separate Unicode regression failed before removing HTML maxLength, which counts UTF-16 units rather than the contract code points. The corrected field submits 65 emoji, spaces and a decomposed accent unchanged; the server retains the authoritative 128-codepoint limit. Scoped re-review passed and preserved the earlier lifecycle fix. Root rebuilt the final frontend and reran the complete desktop/mobile Chrome check successfully after this correction. Unit/regression tests exercise expiry and old-session reads; a real demo expiry journey is deferred until the demo exists. Native 200% zoom, comprehensive accessibility, cross-tab session replacement, performance, remote CI and public TLS/deployment are not established here. Bundle size output is not a performance benchmark.
