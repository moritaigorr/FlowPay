# FlowPay

FlowPay is a financial management SaaS for small Brazilian service businesses. The initial product records transactions completed outside FlowPay; it does not move money or connect to banks.

## Current state

The repository includes a React frontend, Spring Boot API, PostgreSQL schema, and CI workflow. Registration creates a user, a company, and an initial `OWNER` membership; login uses a server session and the SPA has CSRF protection. Passwords use BCrypt, and the session cookie is `HttpOnly`/`SameSite=Lax` and `Secure` outside the local profile.

The authenticated workspace currently supports tenant-scoped contacts, financial accounts and categories, receivable/payable titles, cash transactions, title settlements and reversals, dashboard/monthly reporting, CSV statement import, and reconciliation review and matching. Imported statement lines do not change controlled cash; matching links an existing cash transaction or creates one according to the selected reconciliation action. Reconciliation summaries compare activity over the selected period. The current CSV format does not include opening/closing statement balances, so it cannot report an actual bank-balance difference.

The product records financial activity completed outside FlowPay; it does not move money or connect to banks. Contact deletion, switching between multiple companies, invitations, email verification, password recovery, and abuse rate limiting are not implemented. Public registration should not be exposed beyond controlled local/demo use until abuse controls are added. A reproducible demo seed and the remaining Portao 10 acceptance criteria are still pending. See the [implementation roadmap](docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md) for status and scope.

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
