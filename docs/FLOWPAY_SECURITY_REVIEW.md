# FlowPay security model and review

**Status:** partial source review; not a production security assessment or an ASVS conformance claim.
**Reviewed:** 2026-11-23

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
- Application services derive a single company membership from the authenticated email and access PostgreSQL using tenant-scoped queries. Composite foreign keys provide a second tenant boundary for financial relationships.
- Uploaded CSV bytes are untrusted input. They pass through multipart limits and a strict parser before storage in PostgreSQL; no statement download endpoint exists in the current MVP.

Deployment assumptions such as TLS termination, proxy trust, database network isolation, secret-manager use, backup storage, and log retention have not been verified from this checkout.

## Controls observed in source

| Surface | Current control | Evidence and limits |
|---|---|---|
| Passwords and sessions | BCrypt cost 12; HTTP-only, Secure, SameSite=Lax session cookie; session ID changes after registration/login; request and response records redact `toString`. | `IdentityConfiguration`, `IdentityController`, and `application.yml`; account lockout and request throttling are not present in reviewed source. |
| CSRF | Spring Security uses a cookie token repository with SPA handling; authenticated routes require authentication and unsafe requests require CSRF. | `IdentityConfiguration`; existing PostgreSQL MockMvc coverage rejects login without the header. New multipart CSRF regression coverage has been added but is not run in this environment. |
| Tenant isolation | Tenant is derived from the authenticated user's sole membership; services pass that tenant into queries. Migrations use tenant-scoped unique keys and composite foreign keys for financial relationships. | `JdbcTenantAccess`, service queries, migrations, and existing cross-tenant MockMvc coverage for contacts/dashboard. Multipart cross-tenant coverage has been added but is not run here. |
| CSV ingestion | Spring limits each file to 10 MB and the request to 11 MB; the controller repeats the file limit. The parser requires strict UTF-8 and a fixed header/dialect, caps data rows at 50,000, and uses exact decimal parsing. | `application.yml`, `StatementImportController`, `StatementCsvParser`; oversized multipart regression coverage is added but awaits execution. |
| Statement storage and cash | Statement bytes/lines are stored under tenant and account identifiers with checksum uniqueness. Import and linking an existing movement do not change controlled cash; creating a movement is an explicit, separate reconciliation decision. | `StatementPreviewService`, reconciliation services, and V9+ migrations; retention and deletion rules remain open before real bank data is accepted. |
| Injection and logs | Reviewed SQL uses bound parameters or internally generated fixed fragments/placeholders; React renders data without raw HTML APIs. `RequestCorrelationFilter` generates the correlation ID and logs only method, status, outcome, and duration; it does not log paths, query strings, tenant/user identity, request bodies, CSV contents, or tokens. Statement metrics use fixed operation/outcome labels and are exposed only in the local profile, where the existing security chain requires an authenticated session. | `RequestCorrelationFilter`, `StatementPreviewService`, `application.yml`, `application-local.yml`, and static source inspection. Filter/metrics tests and deployed log pipeline/retention were not exercised. |
| Demo and CI | Demo credentials are supplied through configuration and seed activation is restricted to local mode/loopback PostgreSQL. CI uses a job-scoped PostgreSQL service and generates an ephemeral demo password. | Demo seed configuration and `.github/workflows/ci.yml`; a GitHub Actions run for the current local commits has not been observed. |

## Open security work

- Decide and implement abuse controls for public registration and login before exposing them to untrusted internet traffic. A robust rate limit depends on the deployment and trusted-proxy model; an in-memory limit would not protect a multi-instance deployment.
- Define retention, deletion, and access policy for original statement bytes and parsed rows before accepting real statements. Keep any future download private, tenant-scoped, and audited.
- Verify TLS, trusted proxy headers, database reachability, secret delivery, sanitized production logs, backup retention, and restoration against the actual deployment environment.
- Run the new multipart CSRF, cross-tenant account, and oversize tests with Maven and Docker/Testcontainers. The current machine has no Maven executable or Maven wrapper, so these tests could not be executed here.
- Run the write-enabled Playwright journey on its disposable CI database; its financial path remains unverified until that job completes.
- Complete manual keyboard, zoom, contrast, and screen-reader checks for the critical journeys.

The security reference is [OWASP ASVS 5.0.0](https://asvs.owasp.org/), the current stable release checked on 2026-11-23. This document selects relevant surfaces; it does not claim that every ASVS requirement has been assessed or met.
