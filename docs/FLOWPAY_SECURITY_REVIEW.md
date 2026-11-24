# FlowPay security model and review

**Status:** partial source review; not a production security assessment or an ASVS conformance claim.
**Reviewed:** 2026-11-24

This document records the current security boundaries and evidence for the MVP. It should be updated when an open control is implemented or the runtime architecture becomes available.

The Codex Security Standard scan of commit `598e459` reported zero reportable findings for its targeted surfaces. Coverage was partial, the scan was source-only, and subsequent test/documentation edits were outside that immutable scan snapshot.

## System model

FlowPay is a React SPA served with a same-origin Spring Boot API and PostgreSQL. It records financial operations performed outside FlowPay; it does not move money or connect directly to banks in the MVP.

### Assets

- Company identities, memberships, contact details, titles, settlements, cash movements, and reports.
- Original statement file bytes, parsed statement lines, review history, and reconciliation links.
- User password hashes, authenticated session cookies, CSRF tokens, and database credentials.
- Integrity of tenant-scoped balances and the audit history of confirmed financial operations.

### Actors and trust boundaries

- An unauthenticated client can submit registration, login, CSRF bootstrap, and crafted HTTP or multipart requests.
- An authenticated company member can manipulate request IDs, fields, and uploaded CSV contents within their session.
- A member from another company can attempt horizontal reads, writes, and account or statement-line associations.
- The browser and API share the application's origin. Spring Security validates session and CSRF state before mutating handlers.
- Application services resolve the authenticated identity and either the requested tenant membership or the sole available membership; the requested tenant header never grants access by itself.
- Uploaded CSV bytes are untrusted input. They pass through multipart limits and a strict parser before storage in PostgreSQL; no statement download endpoint exists in the current MVP.

Deployment assumptions such as TLS termination, proxy trust, database network isolation, secret-manager use, backup storage, and log retention have not been verified from this checkout.

## Controls observed in source

| Surface | Current control | Evidence and limits |
|---|---|---|
| Passwords and sessions | BCrypt cost 12; HTTP-only, Secure, SameSite=Lax session cookie; session ID changes after registration/login; request and response records redact `toString`. Login allows five requests per 15 minutes per source and normalized identifier; registration allows five per hour per source. Successful login clears the login bucket. | `IdentityController`, `AuthenticationRateLimiter`, migration V16; counters use PostgreSQL and HMAC fingerprints. Integration tests were added but not run on this checkout. Proxy CIDRs still require deployment verification. |
| CSRF | Spring Security uses a cookie token repository with SPA handling; authenticated routes require authentication and unsafe requests require CSRF. | `IdentityConfiguration`; `mvn verify` passed existing login CSRF coverage and authenticated multipart regression coverage for missing CSRF. |
| Tenant isolation | Authenticated identity plus membership validates every requested company; queries and composite foreign keys scope business records. | `JdbcTenantAccess`, services, and migrations; membership switching and multi-company test scenarios were added. The new PostgreSQL integration test was compiled but not run on this checkout. |
| CSV ingestion | Multipart limits, strict UTF-8 and fixed dialect, a 50,000-row cap enforced during parsing, and exact decimal parsing are enforced. | `application.yml`, `StatementImportController`, `StatementCsvParser`; the oversized multipart regression passed in `mvn verify`. Five focused parser tests, including rejection immediately after the row cap, passed on 2026-11-24. |
| Statement storage and cash | Statement bytes/lines are stored under tenant and account identifiers with checksum uniqueness. Import and linking an existing movement do not change controlled cash; creating a movement is an explicit, separate reconciliation decision. | `StatementPreviewService`, reconciliation services, and V9+ migrations; retention and deletion rules remain open before real bank data is accepted. |
| Balance adjustments | Only `OWNER`/`MANAGER` may record a positive amount against an active tenant account; the cash transaction stores the authenticated member and required reason. Idempotency prevents duplicate effects. | `CashTransactionService`, migration V17, CSRF protected endpoint and UI. The migration passed on an empty PostgreSQL 16.15 database and the real API browser journey passed; Testcontainers and current CI remain pending. |
| Injection and logs | Reviewed SQL uses bound parameters or fixed fragments; React avoids raw HTML. `RequestCorrelationFilter` generates correlation IDs and logs only method/status/outcome/duration. Statement metrics use fixed labels and are exposed only under the authenticated local profile. | `mvn verify` passed filter, timer, replay, and Testcontainers integration coverage. Production log pipeline/retention remain unverified. |
| Demo and CI | Demo credentials are supplied through configuration and seed activation is restricted to local mode/loopback PostgreSQL. CI uses a job-scoped PostgreSQL service and generates an ephemeral demo password. | Demo seed configuration and `.github/workflows/ci.yml`; a GitHub Actions run for the current local commits has not been observed. |

## Open security work

- Partial (2026-11-24): login and registration use atomic PostgreSQL counters and HMAC fingerprints, not in-memory state. The API uses the socket peer as trust anchor and reads X-Forwarded-For only from configured trusted proxy CIDRs. Seven focused proxy resolver tests and 13 PostgreSQL integration tests in `DemoSeedPostgresIntegrationTest` passed locally with Testcontainers; the integration suite includes concurrent limit reservations and 429/`Retry-After` behavior. Deployment CIDRs and proxy append behavior, distributed credential-stuffing defenses, and production thresholds remain open; this is not production approval.
- Define retention, deletion, and access policy for original statement bytes and parsed rows before accepting real statements. Keep any future download private, tenant-scoped, and audited.
- Verify TLS, trusted proxy headers, database reachability, secret delivery, sanitized production logs, backup retention, and restoration against the actual deployment environment.
- Observe the GitHub Actions workflow on the remote branch; the full local backend `mvn verify` passed with Docker/Testcontainers, but no current remote CI run has been observed.
- The write-enabled financial Playwright journey passed locally against an isolated PostgreSQL 16.15 database; observe the same journey in the disposable GitHub Actions database after syncing the local commits.
- Complete manual keyboard, zoom, contrast, and screen-reader checks for the critical journeys.

The security reference is [OWASP ASVS 5.0.0](https://asvs.owasp.org/), the current stable release checked on 2026-11-23. This document selects relevant surfaces; it does not claim that every ASVS requirement has been assessed or met.
