# FlowPay

FlowPay is a financial management SaaS for small Brazilian service businesses. The initial product records transactions completed outside FlowPay; it does not move money or connect to banks.

## Current state

The repository includes a React frontend, Spring Boot API, PostgreSQL schema, and CI workflow. Registration creates a user, a company, and an initial `OWNER` membership; login uses a server session and the SPA has CSRF protection. Passwords use BCrypt, and the session cookie is `HttpOnly`/`SameSite=Lax` and `Secure` outside the local profile.

The authenticated workspace currently supports tenant-scoped contacts, financial accounts and categories, receivable/payable titles, cash transactions, title settlements and reversals, dashboard/monthly reporting, CSV statement import, and reconciliation review and matching. Imported statement lines do not change controlled cash; matching links an existing cash transaction or creates one according to the selected reconciliation action. Reconciliation summaries compare statement activity with recorded cash activity. On import confirmation, users may also provide opening/closing statement balances and dates; when present, the summary compares the closing statement balance with FlowPay's controlled balance and reports a statement roll-forward difference. These balance fields are optional metadata and do not change cash. Migration V14 and balance calculations are covered by PostgreSQL 16.16 Testcontainers integration tests; browser validation of the full journey remains pending.

The product records financial activity completed outside FlowPay; it does not move money or connect to banks. Contact deletion, switching between multiple companies, invitations, email verification, password recovery, and abuse rate limiting are not implemented. Public registration should not be exposed beyond controlled local/demo use until abuse controls are added. The `demo` Spring profile creates a reproducible synthetic dataset; the remaining Portão 10 acceptance criteria are still pending. See the [implementation roadmap](docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md) for status and scope.

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

Open the URL printed by Vite, normally `http://localhost:5173`. PostgreSQL is bound to `127.0.0.1` and uses development-only defaults.

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

Google Chrome must be installed. The default smoke journey signs in with keyboard activation and reads the dashboard, contacts, account, and open titles. The financial settlement journey creates a synthetic company and records a partial receipt; it is skipped unless `FLOWPAY_E2E_ALLOW_WRITES=true`. Enable it only against a fresh, disposable E2E database, never a shared or personal database. Axe scans login, dashboard, and contacts against WCAG 2.1 A/AA rules, but automated scans do not establish WCAG conformance. Failure screenshots and traces are written under the ignored `frontend/test-results/` directory. `FLOWPAY_E2E_BASE_URL` can override the default Vite URL.

The GitHub Actions frontend job provisions its own PostgreSQL 18.7 service, starts the demo API with an ephemeral password, installs Playwright Chromium, and enables both browser journeys against that job-scoped database. The workflow must complete on GitHub before its CI integration is considered verified.

## Validation commands

From the repository root:

```powershell
mvn -f backend/pom.xml verify
node scripts/validate-agent-assets.mjs
```

From `frontend/`:

```powershell
npm.cmd ci
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

Do not put real credentials in `.env`, screenshots, logs, or demo data. `.env` is ignored by Git; `.env.example` contains local development values only.
