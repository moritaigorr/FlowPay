# FlowPay

FlowPay is a financial management SaaS for small Brazilian service businesses. The initial product records transactions completed outside FlowPay; it does not move money or connect to banks.

## Current state

The repository includes a React frontend, Spring Boot API, PostgreSQL schema, and CI workflow. In the `local` profile, registration creates a user, a company, and an initial `OWNER` membership; registration is disabled by default in other profiles, and the SPA hides the registration form when the API reports it disabled. Login uses a server session and the SPA has CSRF protection. Passwords use BCrypt, and the session cookie is `HttpOnly`/`SameSite=Lax` and `Secure` outside the local profile.

The authenticated workspace currently supports tenant-scoped contacts, financial accounts and categories, receivable/payable titles, cash transactions, title settlements and reversals, dashboard/monthly reporting, CSV statement import, and reconciliation review and matching. Internal transfers between active company accounts are recorded atomically as paired cash movements and excluded from operating inflow/outflow totals. Tenant role policy allows `OWNER` and `MANAGER` to write operational data; `READER` is read-only. Member administration and invitations are not exposed yet. Imported statement lines do not change controlled cash; matching links an existing cash transaction or creates one according to the selected reconciliation action. Reconciliation summaries compare statement activity with recorded cash activity. On import confirmation, users may also provide opening/closing statement balances and dates; when present, the summary compares the closing statement balance with FlowPay's controlled balance and reports a statement roll-forward difference. These balance fields are optional metadata and do not change cash. PostgreSQL Testcontainers coverage includes transfers, statement balances, and reconciliation concurrency. Backend `mvn -o -f backend/pom.xml verify` passed locally against PostgreSQL 16.16 with all 73 tests passing; the frontend typecheck, lint, and production build also pass. On 2026-11-24, all five Playwright E2E tests passed in Chrome against a disposable PostgreSQL 16.15/API setup, including real tenant switching, mobile navigation, and financial reconciliation. The latest observed GitHub Actions run (`78836f7`) passed backend verification but failed frontend typecheck on that older remote commit. A fresh remote CI result for the latest local commits still needs to be observed.

The product records financial activity completed outside FlowPay; it does not move money or connect to banks. Contact deletion, invitations, email verification, and password recovery are not implemented. Login attempts are limited to five per 15 minutes for each HMAC-protected combination of socket source address and normalized account identifier; successful login clears that bucket. Registration is limited to five attempts per hour per socket source. Counts are atomic in PostgreSQL and expired rows are removed hourly. The API uses `getRemoteAddr()` as its trust anchor and reads `X-Forwarded-For` only when that peer belongs to `FLOWPAY_TRUSTED_PROXY_CIDRS`; it walks the chain from the nearest proxy and fails back to the socket peer on malformed headers. Configure narrow actual proxy ranges and verify that the proxy appends the real peer address before public deployment. Supply `FLOWPAY_RATE_LIMIT_HMAC_KEY` with at least 32 bytes outside the local profile. The SPA lets users select among their companies; `X-FlowPay-Tenant` is validated against the authenticated identity membership on every business request and is never authorization by itself. The `/api/auth/register` endpoint is disabled by default (`flowpay.identity.registration.enabled=false`); `application-local.yml` enables it for local development. Do not expose registration publicly until trusted-proxy handling and deployed limits are verified. The `demo` Spring profile creates a reproducible synthetic dataset; Portão 10 still has manual accessibility and current-revision CI acceptance work open. See the [implementation roadmap](docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md), [API route catalog](docs/API_ROUTE_CATALOG.md), and [API error contract](docs/API_ERROR_CONTRACT.md) for current status and contracts.

## API contract

The current API snapshot is [docs/openapi.json](docs/openapi.json). Check its operation coverage, schema references, tenant headers, CSRF requirements, and idempotency responses with `node scripts/validate-openapi.mjs` from the repository root.

## Prerequisites

- Java 21
- Apache Maven 3.10.1 or a compatible Maven 3.x release
- Node.js 26 LTS and npm
- Docker Desktop with Docker Compose

On Windows, `npm.cmd` avoids PowerShell execution-policy issues with `npm.ps1`.

## Local development

Copy the development environment template. Its local-only defaults match the backend configuration:

```powershell
Copy-Item .env.example .env
```

Start PostgreSQL from the repository root:

```powershell
docker compose up -d db
```

Start the API in one terminal:

```powershell
mvn -f backend/pom.xml -Dspring-boot.run.profiles=local spring-boot:run
```

Start the frontend in another terminal:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`. PostgreSQL is bound to `127.0.0.1` and uses development-only defaults. The local Spring profile exposes the Actuator metrics endpoint to authenticated sessions; other profiles expose health only by default.

Compose reads `.env`; the backend process does not. The committed database defaults are activated only with the `local` Spring profile. If you change the database port or credentials in `.env`, set matching `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` variables in the backend terminal. Outside the local profile, database settings are required through environment variables.

To stop the database:

```powershell
docker compose down
```

### Demo account and data

The demo seed runs only when the backend starts with the explicit `demo` profile and verifies that the `local` profile is active with a loopback PostgreSQL URL for the `flowpay` database. It fails startup otherwise. It creates a synthetic company, contacts, account with a fictitious opening balance, categories, and open titles. Re-running it is idempotent. It does not delete data, create bank statement imports, or record settlements.

Set `FLOWPAY_DEMO_PASSWORD` in the backend process environment before starting with both `local` and `demo` profiles. Start the API from the repository root with:

```powershell
mvn -f backend/pom.xml -Dspring-boot.run.profiles=local,demo spring-boot:run
```

The password must be 12–72 UTF-8 bytes. The demo email is `demo@flowpay.local`; no password is committed or supplied by default. The company name shown after login is `FlowPay Demonstração`.

The seed uses stable identifiers and fails startup if its demo user or company identifiers already belong to a different record. For a full reset, stop the app and remove the Compose database volume only when it contains disposable data for this checkout; `docker compose down --volumes` erases the local database for this project. Do not point the demo profile at a shared, personal, staging, or production database.

### Browser E2E

The Playwright smoke journey uses the local `local,demo` API and synthetic database. Keep PostgreSQL and the API running; Playwright starts Vite if it is not already available. Set `FLOWPAY_E2E_DEMO_PASSWORD` in the frontend process environment, then run from `frontend/`:

```powershell
npm.cmd run test:e2e
```

Google Chrome must be installed. The default smoke journey signs in with keyboard activation and reads the dashboard, contacts, account, open titles, and statement import screen. The financial journey creates a synthetic company, records a partial receipt and an off-platform payable payment, imports and reconciles a matching statement line, checks replay without duplicate cash, verifies monthly inflow/outflow totals, and imports a CSV with only a rejected row to check reported balance differences. It is skipped unless `FLOWPAY_E2E_ALLOW_WRITES=true`. Enable it only against a fresh, disposable E2E database, never a shared or personal database. Axe scans login, dashboard, contacts, statement import, payment, saved statement lines and summary, and the monthly report against WCAG 2.1 A/AA rules. Automated scans do not establish WCAG conformance. The statement screen and reconciliation tables are also checked at a 390 px viewport for horizontal overflow. Failure screenshots and traces are written under the ignored `frontend/test-results/` directory. `FLOWPAY_E2E_BASE_URL` can override the default Vite URL.

The GitHub Actions frontend job provisions its own PostgreSQL 18.7 service, starts the demo API with an ephemeral password, installs Playwright Chromium, and enables the browser journeys against that job-scoped database. CI opts into an extra synthetic reader-only company so Playwright can verify real tenant switching and isolated balances against the API and PostgreSQL; this fixture is disabled in the normal local demo. The latest observed remote run passed frontend checks but failed the backend integration test on an older remote commit; the current local checkout fixes its test web environment. Observe a new run after synchronizing the local commits.

### Local PostgreSQL backup and restore drill

These scripts default to the Compose `db` service whose database is named `flowpay`. On Windows, `-ConnectionMode Local` can use installed PostgreSQL client tools against a loopback PostgreSQL server; that mode is restricted to the database named `flowpay` for backup and loopback addresses for restore. The restore user needs permission to create a database. Supply `-PostgresBinDirectory` when the client tools are not on `PATH`. Both modes are local-development tools, not a production backup service.

Create a custom-format dump under the ignored `backups/` directory:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/backup-postgres.ps1
```

To back up a local PostgreSQL instance without Docker:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/backup-postgres.ps1 -ConnectionMode Local -PostgresBinDirectory 'C:\Program Files\PostgreSQL\16\bin' -DatabasePort 55432
```

Restore a dump into a new database without overwriting `flowpay`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/restore-postgres.ps1 -BackupPath ./backups/flowpay-YYYYMMDD-HHMMSS.dump -TargetDatabase flowpay_restore_drill
```

Restore into a new database on a local PostgreSQL instance:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/restore-postgres.ps1 -ConnectionMode Local -PostgresBinDirectory 'C:\Program Files\PostgreSQL\16\bin' -DatabasePort 55432 -BackupPath ./backups/flowpay-YYYYMMDD-HHMMSS.dump -TargetDatabase flowpay_restore_drill
```

The restore script accepts only new database names prefixed with `flowpay_restore_`, refuses an existing target, validates the archive, restores it in a separate database, and checks for successful Flyway migrations. It retains the restored database for inspection and never drops the source or target. Dumps contain the database's financial and personal data; protect and delete local copies deliberately. This procedure does not define production retention, encryption, RPO, or RTO, and does not back up object storage.

## Validation commands

From the repository root:

```powershell
mvn -f backend/pom.xml verify
node scripts/validate-agent-assets.mjs
node scripts/validate-openapi.mjs
```

From `frontend/`:

```powershell
npm.cmd ci
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

Do not put real credentials in `.env`, screenshots, logs, or demo data. `.env` is ignored by Git; `.env.example` contains local development values only.
