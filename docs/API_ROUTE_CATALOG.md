# API Route Catalog

This catalog is a readable inventory of the routes in the [OpenAPI contract](openapi.json). The specification is a manually maintained snapshot; verify controller mappings and DTOs when changing the API.

## Cross-cutting behavior

- API base URL in local development: `http://localhost:8080`.
- Authenticated routes use the `JSESSIONID` session cookie. Unsafe requests require the `XSRF-TOKEN` cookie value echoed as `X-XSRF-TOKEN`; fetch a token from `GET /api/auth/csrf`.
- Business routes accept optional `X-FlowPay-Tenant`. The server derives membership from the authenticated identity and validates any requested tenant. A tenant header is required when the user has multiple memberships.
- Errors use `application/problem+json`; see [API Error Contract](API_ERROR_CONTRACT.md). Responses include `X-Request-Id`.
- Routes marked **Idempotency-Key** require a UUID header for write replay protection. Consult the corresponding request DTO before sending a body.

## Identity

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/auth/csrf` | Anonymous | Issues/returns the CSRF token |
| GET | `/api/auth/config` | Anonymous | Registration availability |
| GET | `/api/auth/session` | Session | Current user and tenant memberships |
| POST | `/api/auth/register` | Anonymous + CSRF | Creates the initial identity/company |
| POST | `/api/auth/login` | Anonymous + CSRF | Starts a session |
| POST | `/api/auth/logout` | Session + CSRF | Spring Security logout endpoint |

## Contacts and finance

| Method | Path | Access | Idempotency |
|---|---|---|---|
| GET, POST | `/api/contacts` | Session + tenant | No |
| GET, PUT | `/api/contacts/{id}` | Session + tenant | No |
| GET, POST | `/api/financial-accounts` | Session + tenant | Create requires key |
| GET, PUT | `/api/financial-accounts/{id}` | Session + tenant | No |
| GET, POST | `/api/financial-categories` | Session + tenant | No |
| GET, PUT | `/api/financial-categories/{id}` | Session + tenant | No |
| GET, POST | `/api/financial-titles` | Session + tenant | Create requires key |
| GET, PUT | `/api/financial-titles/{id}` | Session + tenant | No |
| POST | `/api/financial-titles/{id}/cancel` | Session + tenant | No |
| GET, POST | `/api/financial-titles/{id}/settlements` | Session + tenant | Create requires key |
| POST | `/api/financial-titles/{id}/settlements/{settlementId}/reversals` | Session + tenant | Requires key |
| GET, POST | `/api/cash-transactions` | Session + tenant | Create requires key |
| POST | `/api/cash-transactions/transfers` | Session + tenant | Requires key |
| POST | `/api/cash-transactions/adjustments` | Session + tenant | Requires key |

## Reporting

| Method | Path | Access |
|---|---|---|
| GET | `/api/dashboard` | Session + tenant |
| GET | `/api/reports/monthly` | Session + tenant |

## Statements and reconciliation

| Method | Path | Access | Idempotency |
|---|---|---|---|
| POST | `/api/statement-imports/preview` | Session + tenant + CSRF | No; preview is non-persistent |
| POST | `/api/statement-imports/confirm` | Session + tenant + CSRF | Import checksum deduplicates confirmation |
| GET | `/api/statement-imports` | Session + tenant | — |
| GET | `/api/statement-imports/{importId}/lines` | Session + tenant | — |
| GET | `/api/statement-imports/{importId}/reconciliation-summary` | Session + tenant | — |
| POST | `/api/statement-lines/{lineId}/review` | Session + tenant + CSRF | Requires key |
| GET | `/api/statement-lines/{lineId}/suggestions` | Session + tenant | — |
| POST | `/api/statement-lines/{lineId}/match` | Session + tenant + CSRF | Requires key |
| POST | `/api/statement-lines/{lineId}/match/reversal` | Session + tenant + CSRF | Requires key |
| POST | `/api/statement-lines/{lineId}/candidate-ignores/{movementId}` | Session + tenant + CSRF | Requires key |
| POST | `/api/statement-lines/{lineId}/candidate-ignores/{movementId}/restore` | Session + tenant + CSRF | Requires key |
| GET | `/api/statement-lines/matches` | Session + tenant | — |

## Data-format invariants

- Money fields that may exceed JavaScript's safe integer range are serialized as decimal strings containing integer centavos. Currency is BRL.
- Business dates use `YYYY-MM-DD`; report responses include the requested period, timezone, and `asOf` reference.
- CSV preview accepts UTF-8 (optional BOM), semicolon-delimited columns `data;descricao;valor;direcao`, `dd/MM/yyyy` dates, Brazilian decimal formatting, and `ENTRADA`/`SAIDA` direction. Limits and validation details are enforced by the import API; consult its implementation before building a client.
- Import preview does not change cash. Confirming an import stores statement lines; reconciliation is the operation that links a line to an existing cash transaction or creates one according to the selected decision.
