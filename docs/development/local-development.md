# Local development

FlowPay is a personal payment simulation. The implemented product includes a modular Java API, session authentication and company isolation, customer and payment APIs, idempotent payment creation, payment reads/history/transitions, dashboard metrics, isolated demo entry and cleanup, plus customer/payment/Overview UI and a guided replay walkthrough. R19 browser acceptance and local R20/R21 performance baselines are recorded in [implementation status](../implementation-status.md); their limits are documented in the linked evidence.

## Run the production-like foundation

Install/start Docker Desktop with its Linux engine and Compose. From the repository root, on PowerShell:

```powershell
.\scripts\setup-local.ps1
docker compose config --quiet
docker compose up --build --detach --wait
```

The helper creates an ignored `.env` with a random local database password and preserves an existing file. It does not print the password. On other operating systems, copy `.env.example` to `.env` and provide a unique password before starting. Never commit that file or paste resolved Compose configuration into reports.

Default local URLs:

- Frontend: http://127.0.0.1:8080/payments
- Maintained OpenAPI: http://127.0.0.1:8080/api/openapi.yaml
- Read-only Swagger UI: http://127.0.0.1:8080/api/docs
- Bounded backend health: http://127.0.0.1:8080/actuator/health

Only the frontend/proxy port is published, bound to loopback. PostgreSQL and Java stay on the Compose network. If port 8080 is occupied, set `FLOWPAY_HTTP_PORT` in `.env`; do not stop an unrelated process. `docker compose ps` shows readiness. Protected API requests without a session return a problem response and must never be replaced by SPA HTML.

PostgreSQL 18 stores data in a named volume mounted at `/var/lib/postgresql`, matching the image's version-specific data directory. Flyway applies the core and Spring Session migrations. The Java process runs as an unprivileged container user. Both application images use multiple build stages; the frontend runtime contains static files and Nginx, without Node or development dependencies.

Stop without removing local data:

```powershell
docker compose stop
docker compose start --wait
# Or remove only the project's containers/network while preserving its named volume:
docker compose down
```

To deliberately erase this project's local database, first verify the current directory and project name `flowpay`, then use `docker compose down --volumes`. This destroys its synthetic local data and sessions. Normal development, upgrades and troubleshooting do not require that command; it was not used for the initial verification. After erasing/recreating the volume, a new local password may be configured. Changing `.env` alone does not change an existing PostgreSQL user's password.

## Edit locally

Node 24.18.0/npm 11.16.0 are pinned. Java 21 is required for local Maven; on Windows the process-local helper accepts `-JavaHome` or uses the ignored portable JDK described in [dependencies](dependencies.md).

```powershell
# Repository tooling
npm ci
npm run validate:api

# Frontend
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run dev
```

Vite binds to 127.0.0.1 and proxies `/api`, Swagger assets and health to 127.0.0.1:8080. It can therefore target the Compose proxy while retaining browser same-origin requests. If the Compose port changes, update the explicit Vite target accordingly; no CORS or cookie-security relaxation is required. An occupied dev port may be replaced with `npm run dev -- --port 5175 --strictPort`.

Backend, from the repository root:

```powershell
. .\scripts\use-java.ps1
cd backend
.\mvnw.cmd -B verify
```

On Linux/macOS with JDK 21, use `./mvnw -B verify` inside backend. Docker must be available for PostgreSQL Testcontainers. Tests create disposable databases and do not use the Compose volume. On restricted Windows shells, run local development commands in a normal terminal or grant the required network/process permissions; a sandbox's socket access error is not evidence of an application failure.

With Compose healthy, run `npm run check:local` from the root for the foundation's real HTTP/proxy/docs/CSRF/attempt-limit smoke. It makes no authenticated business writes. It is scoped to shared foundation behavior; authenticated flows have separate checks. A custom local port can be supplied through `FLOWPAY_BASE_URL`.

The Docker build packages previously tested Java sources with tests skipped inside the image build; the separate wrapper `verify` and CI job execute the real tests. A successful image build alone does not establish payment correctness.

## Provision a local operator

There is no public conventional signup. To create a local conventional company/operator, configure `FLOWPAY_BOOTSTRAP_COMPANY_NAME`, `FLOWPAY_BOOTSTRAP_EMAIL` and `FLOWPAY_BOOTSTRAP_PASSWORD` in your ignored `.env`, then run from the root:

```powershell
.\scripts\provision-local.ps1
```

The helper starts the database, builds the backend image and invokes the optional Compose `tools` service once, without publishing a port. Its Spring profile is `local-bootstrap`, web mode is `none`, and explicit bootstrap enablement applies only to that command. The company and operator commit in one transaction. Reusing an existing normalized email refuses the command and rolls back the new company; it does not reset a password or adopt an existing account. `prod`, `public` and other nonlocal profile combinations are refused before provisioning writes. The normal backend does not enable bootstrap.

On Linux/macOS, after building the backend and starting its database, invoke `docker compose --profile tools run --rm --no-deps bootstrap` with those same explicit values configured. There is no platform-specific account format or default password.

Passwords are not trimmed or normalized; use 1–128 Unicode code points. Email is trimmed for Unicode whitespace and lowercased. The encoded password uses Spring Security's versioned PBKDF2 format, without a new cryptographic dependency or BCrypt's 72-byte input limitation. The session principal is a UUID pair; credentials are absent from session responses and persisted authentication.

Authentication API endpoints are GET `/api/v1/auth/csrf`, POST `/api/v1/auth/login`, GET `/api/v1/auth/me`, POST `/api/v1/auth/logout` and GET `/api/v1/company`. First obtain CSRF; send its named header on writes, including login; fetch a new token after authentication. Login rotates the session ID and clears the old CSRF token. Logout invalidates the JDBC session. Missing authentication returns 401, invalid CSRF returns 403 and bad credentials return 401. Expired demo-company identity is rejected even when an HTTP session still exists; demo entry is implemented and covered by R19 browser acceptance.

After provisioning, run `npm run check:auth` from the root with Node 24.18.0. The local-only check reads the configured bootstrap credentials from `.env`, exercises session/CSRF rotation, own-company access and logout, and prints only check results. It does not create customers/payments or place credentials in browser storage. Configure your own credentials; there is no shared default account. Sign in at `/login` with your provisioned operator credentials; protected routes return to login without a valid session. Sign out is available in the workspace header. Customer, payment, detail/history and Overview screens are implemented; see [integrated acceptance](../verification/product-acceptance.md) for browser evidence and limits.

## Local HTTP versus production

Compose is a local HTTP environment and does not activate the `prod` profile. Session cookies are HttpOnly/SameSite=Lax; `prod` additionally requires Secure cookies over HTTPS. R05 tests verify session behavior and the production Secure attribute; real HTTPS deployment/cookie behavior is not established by those tests. Public deployment/TLS and production secrets require an explicitly authorized release task.

The proxy limits login and demo entry per client address. `.env` controls `FLOWPAY_LOGIN_RATE` (default 5r/m) and `FLOWPAY_DEMO_RATE` (default 2r/m), with small bursts. Proxy 429 returns `Retry-After` and plain text; client code must handle non-JSON proxy errors. Java problem responses pass through unchanged. The proxy resolves the backend through Docker DNS, so a backend recreation does not leave a permanently stale address.

## CI and verification limits

`.github/workflows/ci.yml` defines Java verification with PostgreSQL Testcontainers and frontend lint/typecheck/tests/build plus canonical OpenAPI validation. Actions are pinned to verified official commit SHAs. The workflow has not been run remotely; local command results and Compose smoke evidence are recorded separately. Integrated payment browser journeys, API k6 and authenticated Lighthouse baselines have passed locally; native browser 200% zoom and screen-reader behavior remain unverified. Performance reports are [API](../performance/api-baseline.md) and [frontend](../performance/frontend-baseline.md).

Docker Desktop on this machine previously failed at the internal dockerInference runtime endpoint. The successful narrowly scoped recovery preserved a backup of the runtime directory and did not reset volumes/images/settings. See [dependency environment notes](dependencies.md) and [Docker issue554](https://github.com/docker/desktop-feedback/issues/554) if the same startup error recurs. Do not use factory reset or prune as a routine project setup step.
