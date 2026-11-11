# Module and HTTP implementation contracts

Status: full MVP design contract, not a blanket implementation claim. R05 implements authentication/company APIs; R06 implements customer creation, listing and owned access. R08 implements atomic/idempotent payment creation and its original-response replay. R09 implements payment list/detail/history reads; R10 implements transactional process/cancel transitions; R11 implements company-scoped dashboard summary aggregation. Demo lifecycle operations remain planned. The canonical HTTP schema is [openapi.yaml](openapi.yaml), OpenAPI 3.0.3, API version 1.0.0. The detailed product specification remains the scope authority. This document settles its implementer choices; it does not add product features.

## Wire decisions

All business IDs are UUIDs; parse case-insensitively and output lowercase standard UUID strings. All timestamps represent `Instant`; input requires a timezone offset and output uses UTC `Z`. The company/user never comes from a request field. DTOs reject unknown fields. Only documented query parameters and body shapes are part of this contract; clients must not rely on undocumented parameters or send bodies to bodyless operations. Invalid values for documented parameters return `400 VALIDATION_FAILED`. Fixed list ordering is `createdAt DESC, id DESC` (PostgreSQL UUID ordering for ties). Page is zero-based, size defaults to 20, maximum 100; empty results have `totalPages=0`, and a valid page beyond the last has empty `items` and unchanged totals. Counts are JSON integers representable by Java `long`; the frontend must not perform monetary arithmetic with them.

Names are stripped of outer Unicode whitespace, nonblank, maximum 120 Unicode code points. Emails are stripped, lowercased with `Locale.ROOT`, maximum 254 code points and validated after normalization. Customer emails may repeat; conventional login emails are globally unique. Passwords are neither trimmed nor normalized, 1–128 code points. Descriptions are stripped, at most 500 code points; missing, null and stripped empty all become null. Company names obey the same name limit. Demo identity email is an internal unique synthetic address returned in the session; it cannot authenticate by password.

Money input accepts decimal **strings** such as `"1"`, `"1.0"`, `"1.00"`; all canonicalize to `"1.00"`. Positive maximum is `"99999999999999999.99"`, matching `NUMERIC(19,2)`. No signs, leading zeroes (except the single zero integer part), whitespace, exponent, separators, numeric JSON or more than two fraction digits. Reject `"0"`/`"0.00"`; never round. Responses have exactly two fraction digits. Aggregate sums have no individual-payment upper bound and use decimal strings; PostgreSQL `sum(numeric)` and Java `BigDecimal` must not be narrowed to the payment column precision.

Dashboard `from` and `to` are required UTC-instants with `from < to`. The UI sends the last 30 days by default. Every metric uses creation `[from,to)` and **current** status. `approvalRate` is a ratio string at four decimal places, `HALF_UP`, from `approvedCount/(approvedCount+rejectedCount)`. Both counts are returned for transparency. No denominator returns JSON null; display “No data”, never “0%”. `"0.0000"` is a measured zero with a nonzero denominator. `totalPayments` includes all states; pending/cancelled do not enter approval denominator. No chart endpoint is introduced.

Search `q` is at most 200 code points before stripping; empty means no filter. U+0000 is rejected as `400 VALIDATION_FAILED` with `q/INVALID_FORMAT` before binding PostgreSQL text. Search is case-insensitive literal substring over customer name/email, escaping SQL `%` and `_`. Payment search additionally ORs exact parsed payment UUID against payment ID; all other supplied filters are ANDed. `createdFrom` is inclusive, `createdTo` exclusive; either may appear alone; both require ascending range. Foreign/unknown `customerId` list filters produce an empty page; direct owned-object lookups return 404.

## Sessions, errors and documentation

The opaque browser-managed cookie is `SESSION`, `Path=/`, HttpOnly, SameSite=Lax, Secure in production; inactivity timeout is 30 minutes. Login/demo success rotates its identifier, clears the old CSRF token and returns `{user, company}`. Obtain `GET /api/v1/auth/csrf` before entry; it may establish an anonymous session and returns `{token, headerName:"X-CSRF-TOKEN", parameterName:"_csrf"}`. Send that token unchanged on every POST, including anonymous login/demo. Re-fetch after login/demo/logout. Keep tokens in memory, credentials and session values out of browser storage. Session identity is captured by web infrastructure into trusted Java IDs, never accepted from HTTP fields.

`POST /demo/sessions` returns 201 for a newly provisioned company and 200 for reuse of the current valid demo. Reuse preserves expiry and data. Explicit entry may replace an expired demo or conventional session with a new demo; business requests never silently replace expired sessions. Capacity admission is globally serialized within the single-instance deployment, checks active demos and provisions in one transaction; session authentication follows commit. Demo lifetime is 24 hours independent of session inactivity. Expired authenticated demo requests return 401 `DEMO_EXPIRED` and invalidate the session. Logout itself requires an active authenticated session and valid CSRF; 401/403 handling still clears the client identity.

Java API errors use `application/problem+json`; `type` is `about:blank`, `title` the standard HTTP reason phrase, `status` the actual status, `detail` a safe English explanation, `instance` the path without query, `code` the stable machine code and `requestId` the generated UUID. `fieldErrors` is present for field validation, absent otherwise; fields use camelCase JSON paths, query/header names or `$` for malformed body. Codes are `REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `TOO_LONG`, `UNKNOWN_FIELD`. Never echo password/body/SQL details. `X-Request-Id` is returned on every Java API response; replay uses the new request's ID. API JSON uses `Cache-Control: no-store`. HTTP 405 includes `Allow`; API 503 includes positive integer-seconds `Retry-After`. Proxy 429 uses `Retry-After` where configured, but proxy/network failures may be non-JSON and lack requestId; the frontend must preserve the HTTP status and handle missing/invalid problem bodies safely. Normalizing every proxy failure is not an R01 requirement.

| Status | Stable problem codes |
|---|---|
| 400 | VALIDATION_FAILED |
| 401 | UNAUTHENTICATED, INVALID_CREDENTIALS (login only), DEMO_EXPIRED |
| 403 | CSRF_INVALID |
| 404 | CUSTOMER_NOT_FOUND, PAYMENT_NOT_FOUND |
| 409 | IDEMPOTENCY_KEY_REUSED, PAYMENT_FINAL_STATE, DEMO_LIMIT_REACHED |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 405 | METHOD_NOT_ALLOWED |
| 429 | RATE_LIMITED (login/demo proxy) |
| 503 | CONTENTION_TIMEOUT, DEMO_CAPACITY_REACHED (demo entry only) |
| 500 | INTERNAL_ERROR |

Security filters execute before business handling; a missing CSRF token may produce 403 before missing authentication produces 401. After security, validate syntax/canonicalize, then active-company check; invalid request syntax does not reserve a key. New payment references use 404; replay/hash conflict precedes reference and quota checks. Quotas are 100 customers/500 payments per demo; existing fixtures count toward them.

Public GET `/api/openapi.yaml` serves the packaged canonical file, not generated competing documentation. GET `/api/docs` serves Swagger UI directly with HTTP 200 using the pinned compatible integration; its schema URL is `/api/openapi.yaml`, `supportedSubmitMethods=[]`, `persistAuthorization=false`. Permit public GET `/swagger-ui/**` for its assets and redirect only GET `/swagger-ui.html` to `/api/docs`. No authentication redirects for these routes; proxy forwards them to Java instead of the SPA. Disable generated `/v3/api-docs` endpoints. R02 must prove exact pinned integration configuration and assets with an actual application test. No sample secrets or prefilled credentials. No publication is implied.

## Implemented customer subset

R06 implements customer creation, listing and owned access using the existing JdbcTemplate and Spring transaction manager. Customer writes and the company module JPA lock share one transaction; PostgreSQL integration tests demonstrate outer rollback and lock retention through outer commit. Demo quota counting occurs after company locking and active-status revalidation. Customer listing uses one SQL statement for coherent page items/totals; this is a correctness choice, not a performance claim. See [customer verification](../verification/2026-11-09-customers.md). Customer deletion and demo lifecycle remain planned.

## Implemented payment domain subset

R07 implements internal PaymentAmount string validation/canonicalization and PaymentRules final-state/scenario invariants, reusing the public enums. It does not implement the payment facade signatures below. R08 must separately validate the HTTP JSON type and BigDecimal command boundary; R10 must invoke state transitions under the owned database lock and commit history atomically. See [payment domain verification](../verification/2026-11-09-payment-domain.md).

## Java boundaries

Package root `com.flowpay`; explicit Spring Modulith modules are `company`, `identity`, `customer`, `payment`. Public facade interfaces, immutable records/enums and documented exceptions live at each module root; everything else is `internal`. `config` contains security/web adapters and provisioning orchestration and is excluded from module detection. No domain module depends on config. Allowed dependencies: identity -> company; customer -> company; payment -> company, customer. No domain module imports identity: trusted `actorUserId` is passed by infrastructure.

The signatures below are Java 21 contracts, not code already compiled. Each displayed public top-level type will have its own file. Enum names match OpenAPI. HTTP output DTOs remain inside web adapters; neither facade returns an entity, repository, servlet type or Spring Security principal.

```java
// com.flowpay.company
public record CompanyView(UUID id, String name, Instant createdAt,
                          boolean demo, Instant expiresAt) {}
public record NewCompany(String name, boolean demo) {}
public interface CompanyAccess {
    CompanyView requireActive(UUID companyId);
    CompanyView lockActive(UUID companyId);
    CompanyView lockForCleanup(UUID companyId);
}
public interface CompanyProvisioning {
    CompanyView create(NewCompany command);
    void deleteExpiredDemo(UUID companyId, Instant cutoff);
}

// com.flowpay.identity
public record OperatorView(UUID id, UUID companyId, String email, Instant createdAt) {}
public record NewOperator(UUID companyId, String email, String passwordHash, boolean demo) {}
public interface IdentityProvisioning {
    OperatorView create(NewOperator command);
    void deleteDemoOperators(UUID companyId);
}
public interface IdentityAccess {
    OperatorView authenticate(String email, String password);
    OperatorView requireOperator(UUID userId, UUID companyId);
}

// com.flowpay.customer
public record CustomerView(UUID id, String name, String email,
                           Instant createdAt, Instant updatedAt) {}
public record NewCustomer(String name, String email) {}
public record CustomerPage(java.util.List<CustomerView> items, int page, int size,
                           long totalElements, long totalPages) {}
public interface CustomerAccess {
    CustomerView requireOwned(UUID companyId, UUID customerId);
}
public interface CustomerOperations {
    CustomerView create(UUID companyId, NewCustomer command);
    void deleteDemoCustomers(UUID companyId);
}
public interface CustomerListing {
    CustomerPage list(UUID companyId, int page, int size, String query);
}

// com.flowpay.payment
public enum PaymentStatus { PENDING, APPROVED, REJECTED, CANCELLED }
public enum SimulationScenario { APPROVE, DECLINE }
public record NewPayment(UUID customerId, BigDecimal amount, String currency,
                         String description, SimulationScenario simulationScenario) {}
public record PaymentView(UUID id, UUID customerId, BigDecimal amount, String currency,
                          PaymentStatus status, String description,
                          SimulationScenario simulationScenario,
                          Instant createdAt, Instant updatedAt) {}
public record CreatedPayment(int httpStatus, String location,
                             String responseJson, boolean replayed) {}
public interface PaymentOperations {
    CreatedPayment create(UUID companyId, UUID actorUserId,
                          String idempotencyKey, NewPayment command);
    PaymentView process(UUID companyId, UUID actorUserId, UUID paymentId);
    PaymentView cancel(UUID companyId, UUID actorUserId, UUID paymentId);
    void deleteDemoPayments(UUID companyId);
}
```

Nullable output fields above are only `expiresAt`, demo `passwordHash`, and payment `description`. Conventional `passwordHash` is required and already encoded by orchestration using the configured `PasswordEncoder`; demo passwords must be null. `CompanyProvisioning.create` samples its injected Clock once for creation and derives expiry: null for conventional companies, creation+24h for demos. R05 refined `NewCompany` to accept only name/demo, avoiding an impossible caller-supplied timestamp equality or an ignored input. This does not change HTTP schemas. Request clients cannot call provisioning. The R12 orchestrator creates customer/payment fixtures through public facades and transitions them through process/cancel, preserving history and quotas. Internal fixture keys are deterministic printable values unique per new company.

R05 implements active company/identity creation and access APIs. R12 implements restricted demo cleanup/deletion APIs that require an existing transaction, a locked expired demo, and preserve conventional company data. `IdentityAccess.authenticate` returns the verified operator; `requireOperator` rechecks the trusted user/company pair for existing sessions. Web adapters map OperatorView to the smaller HTTP user shape (id/email/createdAt), never expose its internal companyId as an extra user field, and never return password hashes.

`CreatedPayment.responseJson` is the exact committed creation representation, not a serialization of a current entity; status is always 201 and location `/api/v1/payments/{id}`. Web writes it without reserializing, adds `Idempotency-Replayed` true/false and a fresh request ID. No session or request headers are stored in the idempotency row. Cross-module fixture callers may parse the stored JSON to obtain its payment ID; do not add a second serializer-derived payment to this result which could drift from replay.

Public typed exceptions are module-root `CompanyUnavailableException` (missing versus expired-demo reason), `CustomerNotFoundException`, `DemoLimitReachedException`, `PaymentNotFoundException`, `PaymentFinalStateException`, `IdempotencyKeyReusedException`, `ContentionTimeoutException`, `ContractValidationException`. Each carries only safe IDs/reason/field metadata; web maps them to the table above. Active authenticated missing-company state maps to 401 UNAUTHENTICATED, expired demo to 401 DEMO_EXPIRED. Provisioning capacity exception is in config and maps to 503 DEMO_CAPACITY_REACHED. No controller exposes persistence exceptions.

## Transactions and lock ownership

`CompanyAccess.requireActive` is a snapshot read, not proof that a concurrent write/cleanup is safe. `lockActive` acquires a PostgreSQL `FOR UPDATE` company row, validates existence and expiry **after acquiring it**, and returns an immutable snapshot. `lockForCleanup` takes the same lock without rejecting expiry; cleanup rechecks demo=true and expiry<=cutoff under lock. Both locking methods require an existing transaction (`MANDATORY`), and hold their locks until the outer commit/rollback. Do not return AutoCloseable locks or use `REQUIRES_NEW` to release them early.

Customer/payment mutations start/join one READ COMMITTED transaction (`REQUIRED`) and use the company public API. Demo writes always call `lockActive` before any count, key reservation or payment row lock. Conventional payment creates call `requireActive` and rely on unique-key arbitration, preserving a real concurrent idempotency test without a company lock. Process/cancel on conventional companies can use `requireActive` followed by owned payment lock. If any path needs both locks, company always precedes payment. No locks or transactions span external calls. Orchestration starts the outer provisioning/cleanup transaction; facade calls join it. Authentication of a provisioned demo occurs only after that outer transaction commits.

Payment create order: trusted authentication -> DTO syntax/canonicalization -> active company/demo lock -> reserve/read idempotency -> replay or hash conflict -> owned customer -> demo count/quota -> payment + event + original response -> commit. Canonical hash input is UTF-8 compact JSON with keys in exact order `customerId`, `amount`, `currency`, `description`, `simulationScenario`; UUID lowercase, amount scale 2, currency BRL, stripped/null description, uppercase enum, standard JSON escaping and no insignificant whitespace. Hash SHA-256, lowercase hex. Same DTO values always serialize identically, including non-ASCII text; use one canonical serializer configuration. Idempotency operation string is `create-payment`, key case-sensitive; lifetime equals company lifetime. Do not catch a JPA unique-constraint violation and continue a rollback-only transaction: use native `INSERT ... ON CONFLICT DO NOTHING`, read committed conflict in a subsequent statement.

Cleanup locks one expired demo company, then calls `deleteDemoPayments` (events, keys, payments), `deleteDemoCustomers`, `deleteDemoOperators`, deletes associated persisted sessions through config-owned session infrastructure, then `deleteExpiredDemo`; recheck expiry at each externally callable delete boundary and reject conventional companies. Session deletion uses server-side company/user associations, not cookie content. Each deletion API requires an existing transaction and an already locked expired demo, checks that invariant using the company API, and never accepts arbitrary conventional deletion. Use bounded batches of companies; never acquire a payment before a company or hold multiple company locks in inconsistent order. If multiple companies must be locked, ascending UUID order. Session cleanup must preserve a DEMO_EXPIRED response for an expired session still present; sessions already removed naturally produce UNAUTHENTICATED.

## Verification boundary

R01 validates the schema with the root pinned Redocly CLI command `npm run validate:api` (`@redocly/cli` 2.76.0, minimal rules). [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3) was consulted for schema/reference/security semantics. This is static contract validation, not proof of SQL/session/concurrency behavior. R02 verifies packaged docs, schema and module boundaries; R05/R06/R08/R10/R11/R12 implement behavioral tests with PostgreSQL Testcontainers; R13 compares actual route methods and representative success/problem schemas against this canonical document. Its compatible validation approach is to load this YAML with a test-scoped parser, compare Spring request mappings with canonical business method/path pairs, resolve local references, and assert representative JSON bodies against the documented required fields/types/enums using the Boot-managed JSON API. Any third-party schema validator requires a separate version/compatibility check before use; do not add a runtime dependency merely for documentation. R01 does not execute database checks.
