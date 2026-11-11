# ADR 0002: Same-origin sessions with CSRF protection

Date: 2026-11-08. Status: accepted; session-authentication/company access implemented and verified locally in R05. Public TLS and demo entry remain later work.

FlowPay has one browser dashboard, one Java API and a same-origin proxy. Use Spring Security authentication and Spring Session JDBC in the existing PostgreSQL database. The opaque SESSION cookie is HttpOnly, SameSite=Lax, Secure in production, with a 30-minute inactivity timeout. Rotate its identifier on authentication and invalidate it at logout. Demo company expiry at 24 hours is an additional server-side access check, independent of session inactivity.

Keep CSRF enabled on every write, including login/demo entry. GET `/api/v1/auth/csrf` initializes an anonymous session when needed and returns a token for `X-CSRF-TOKEN`; clients keep it in memory and fetch a fresh token after login/demo/logout. JSON session responses contain user/company information, never the session secret. The server derives tenant identity from authentication and scopes every object access; foreign IDs return 404. Exact wire and error semantics are in [OpenAPI](../api/openapi.yaml) and [module contracts](../api/module-contracts.md).

A bearer JWT would support independent clients but adds token storage/revocation concerns with no MVP need. In-memory server sessions would avoid JDBC writes but lose sessions across restarts and do not share the existing durable cleanup mechanism. JDBC sessions cost database reads/writes and require compatible migrations; no Redis or separate infrastructure is justified. SameSite is useful browser protection but does not replace the explicit CSRF token.

Login/demo attempt limiting belongs to the same-origin proxy; its responses may be non-JSON and lack requestId, so clients must handle these failures safely. Public API documentation is read-only with Swagger execution disabled. Development bootstrap credentials come from environment and are not enabled for the public demo. No conventional public signup is added.

Verify session rotation, logout, CSRF rejection/refresh, tenant isolation, expired demos and cleanup/write races in backend security/integration tests, followed by real browser/proxy checks for cookies and caching. This ADR is a design decision and contains no executed authentication results.

R05 evidence is recorded separately in [identity verification](../verification/2026-11-09-identity.md): real PostgreSQL sessions, ID/CSRF rotation, idle/company expiry, protected cookie attributes and browser/proxy login/logout passed. Demo cleanup/write races and complete UI flows remain their future tasks.
