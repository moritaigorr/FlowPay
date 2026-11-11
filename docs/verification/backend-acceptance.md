# Independent backend acceptance

Date: 2026-11-10  
Scope: R13 public HTTP behavior, tenant isolation, demo lifecycle/resource limits, and OpenAPI parity.

## Result

**PASS.** The independent acceptance journeys passed against the application started by Spring Boot with PostgreSQL 18.6 Testcontainers. No production code or runtime dependencies were changed for this task.

## Checks and evidence

| Check | Result | Evidence |
|---|---|---|
| Anonymous write CSRF | PASS | `PaymentSecurityAcceptanceIT` confirms missing CSRF returns 403 `CSRF_INVALID` before mutation. |
| Customer and payment journey | PASS | Public login, customer creation, payment creation, same-key replay with exact original body/Location, process, ordered event history, and conflicting final transition (409 `PAYMENT_FINAL_STATE`). |
| Tenant isolation | PASS | Two independent authenticated company sessions; foreign payment detail, history and cancel all return 404, while the owning session can still read it. |
| Demo session lifecycle | PASS | Two isolated companies/operators, valid-session reuse, password login denial, and expired-session 401 `DEMO_EXPIRED`. |
| Mid-provisioning failure rollback | PASS | A PostgreSQL trigger causes a seed payment insert to fail during public `POST /api/v1/demo/sessions`; sanitized 500 `INTERNAL_ERROR` is returned and no demo company, operator, customer or payment fragment remains. The trigger is removed in `finally`. |
| Demo resource limits | PASS | After preparing 100 customers, the next public write is rejected with 409; payment 500 is created and replayed, payment 501 is rejected with 409. HTTP capacity exhaustion returns 503 `DEMO_CAPACITY_REACHED` / `Retry-After: 60`, without a partial demo operator/company. Boundary filler rows are prepared directly in the disposable test database; asserted outcomes use HTTP. |
| OpenAPI mapping parity | PASS | Exact set equality between all documented `/api/v1` method/path operations and Spring MVC business mappings. |
| OpenAPI response schemas | PASS | Live session, customer, payment create/detail, paged payment list, event history, dashboard summary and final-state problem responses are checked against the matching operation response schema and resolved component definitions, including required fields, types, enums, UUID/date-time formats and prohibited extra object properties. |
| Frontend / Compose proxy | NOT APPLICABLE | R13 backend acceptance runs the app directly; it does not re-run browser journeys or proxy rate limits. R12 records actual Compose proxy 429 checks; R19 owns integrated browser acceptance. |

## Commands and totals

- Focused acceptance: `.\scripts\use-java.ps1; .\backend\mvnw.cmd -B -f backend/pom.xml '-Dit.test=PaymentSecurityAcceptanceIT,DemoAcceptanceIT,OpenApiContractAcceptanceIT' verify` — **8 tests, 0 failures, 0 errors, 0 skipped**. Final log: `.tools/r13-targeted-final.log`.
- OpenAPI operation response-schema rerun: `.\scripts\use-java.ps1; .\backend\mvnw.cmd -B -f backend/pom.xml '-Dit.test=OpenApiContractAcceptanceIT' verify` — **2 tests, 0 failures, 0 errors, 0 skipped**. Log: `.tools/r13-openapi.log`.
- Full backend gate: `.\scripts\use-java.ps1; .\backend\mvnw.cmd -B -f backend/pom.xml verify` — **12 unit/architecture tests + 102 PostgreSQL integration tests; 114 total, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Final log: `.tools/r13-full-verify-final.log`.

The OpenAPI test uses the YAML reader already available on the existing Spring Boot test classpath; it adds no dependency. The route test intentionally compares the business `/api/v1` mapping set and does not treat documentation/actuator endpoints as business API operations.

## Limits

- OpenAPI response validation covers representative success and problem responses, not every status/parameter/header combination in every operation. Static Redocly validation remains the separate full-document lint check.
- The capacity test fills active company rows in the disposable database to reach the global limit quickly; the rejection, HTTP problem and absence of partial operator creation are checked over HTTP. Mid-seed rollback is independently verified through the public endpoint and a temporary PostgreSQL trigger. R12 separately verifies seed rollback, quota races, cleanup and last-slot concurrency.
- These results establish local application/database behavior. They do not establish production deployment, proxy operation beyond the separately recorded R12 check, or performance capacity.

## Interview takeaway

Acceptance tests sit at the public HTTP boundary: they verify that authentication, tenant scoping, idempotency, state transitions and error contracts work together instead of only checking individual services. PostgreSQL-backed fixtures let the suite construct expensive boundary states quickly while keeping the observable decision under test on the real API.
