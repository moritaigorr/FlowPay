# Customer API verification — 2026-11-09

Scope: R06 customer creation, listing/search and internal owned-customer access. Source base: `809fcf8` plus the R06 change. Customer UI, payments and demo entry remain planned.

## Implemented behavior

- `POST /api/v1/customers` creates a normalized customer for the authenticated company; duplicate customer emails are allowed.
- `GET /api/v1/customers` provides bounded pagination and case-insensitive literal substring search over name/email. Ordering is `createdAt DESC, id DESC` using PostgreSQL UUID ordering.
- Public module facades return immutable views. Owned access scopes by company and returns the same not-found exception for foreign and missing IDs.
- Demo creation acquires the company lock before counting and inserting. The 100-customer quota includes fixtures. Expiry is checked again after lock acquisition; conventional companies have no customer quota.
- Lock acquisition has a transaction-local three-second timeout; contention returns `503 CONTENTION_TIMEOUT` with `Retry-After: 1`. Failed transactions leave no new customer.

JDBC uses the existing datasource and Spring transaction manager alongside the company module's JPA lock. This avoids adding a customer entity solely for these operations. Integration tests prove outer rollback and that a competing connection waits until the outer transaction commits. A single listing statement returns items and totals from one PostgreSQL snapshot, including empty and out-of-range pages. Materializing matching rows favors coherent results; query optimization awaits measured evidence.

## Actual checks

| Check | Result |
|---|---|
| Initial RED | Four customer HTTP tests failed on missing routes, without test harness errors |
| Focused integration | Initial nine customer tests passed on PostgreSQL 18.6 Testcontainers |
| Additional RED | Empty pagination incorrectly defaulted; numeric JSON text fields were coerced. Both assertions failed before their fixes |
| Final Maven Wrapper `verify` | 43 tests passed: 1 architecture and 42 integration, including 13 customer tests; zero failures, errors or skips; BUILD SUCCESS |
| OpenAPI | `npm run validate:api` passed with zero warnings |
| Packaged runtime | `docker compose up --build --detach --wait` built both images; database, backend and frontend became healthy |
| Same-origin smoke | `npm run check:local` passed SPA routing, health, canonical schema/docs/assets, protected API responses, CSRF and proxy limits |

Customer tests exercise Unicode whitespace/codepoint boundaries, strict JSON string fields, required/invalid values, unknown fields, duplicate email, CSRF/authentication, tenant-separated pages, literal search metacharacters, maximum page offsets, stable UUID ties, PostgreSQL constraints, owned access, expiry, outer rollback, concurrent quota consumption, expiry while waiting and actual HTTP contention timeout. Synthetic fixtures are confined to disposable Testcontainers databases.

Reproduce from `backend/`, after the Java helper and Docker setup described in [local development](../development/local-development.md):

```powershell
.\mvnw.cmd -B '-Dit.test=CustomerIT' verify
.\mvnw.cmd -B verify
```

The coordinator used the repository's local Maven cache/toolchain configuration. Ignored raw logs are `.tools/r06-red.log`, `.tools/r06-focused.log`, `.tools/r06-pagination-red.log`, `.tools/r06-scalar-red.log` and `.tools/r06-verify.log`; these local files are not durable public artifacts. This report records their observed results.

## Limits and learning

The expected lock-timeout test emits Hibernate SQLSTATE 55P03 warnings. Existing Mockito dynamic-agent and JVM class-sharing warnings also appear; a passing suite does not mean warning-free output. Local sandbox access initially denied the Docker pipe and localhost HTTP; authorized checks were rerun outside that restriction and passed.

Authenticated customer HTTP behavior was verified against real PostgreSQL in Testcontainers. The packaged smoke verifies the rebuilt application's shared environment; it does not create an authenticated customer in the persistent local database. No frontend change, remote CI execution, performance benchmark or deployment is claimed.

Interview explanation: tenant identity comes from the session, and every customer query includes it. A database company lock serializes demo quota decisions across concurrent requests; disabling a UI button could not provide that guarantee.

Official references consulted for the implementation choice: PostgreSQL 18 [lock timeout](https://www.postgresql.org/docs/18/runtime-config-client.html), [transaction-local configuration](https://www.postgresql.org/docs/18/functions-admin.html) and [LIMIT/OFFSET ordering](https://www.postgresql.org/docs/18/queries-limit.html). The three-second timeout is an implementation choice, not a measured service guarantee.
