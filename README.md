# FlowPay

FlowPay is a financial management SaaS for small Brazilian service businesses. The initial product records transactions completed outside FlowPay; it does not move money or connect to banks.

## Current state

The repository contains the React frontend, Spring Boot API, local PostgreSQL configuration, and CI workflow. Registration creates a user, a company, and an initial `OWNER` membership; login uses a server session and the SPA has CSRF protection. Passwords use BCrypt, and the session cookie is `HttpOnly`/`SameSite=Lax` and `Secure` outside the local profile.

After login, the workspace loads the member's company and provides tenant-scoped contact creation, search, detail, and editing for customers and suppliers. A contact can be both. Contact deletion, switching between multiple companies, and permissions beyond the initial owner remain future work. Invitations, email verification, password recovery, and abuse rate limiting are also not implemented. Do not expose public registration before adding controls against abuse. See the [implementation roadmap](docs/FLOWPAY_IMPLEMENTATION_ROADMAP.md).

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
