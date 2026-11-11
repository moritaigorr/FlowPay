# ADR 0003: Atomic payment creation and original response replay

Date: 2026-11-09. Status: Accepted implementation decision within the approved R08 contract; verification results are recorded in the task report.

## Context

A client can lose a successful HTTP response and retry. Concurrent retries must create one payment, one creation event and one original response. A repeated key with different payment data must return a conflict. A retry must still return the creation representation after the payment changes status or the demo reaches its quota.

## Decision

`PaymentOperations.create` starts or joins a Spring transaction with READ COMMITTED isolation. It validates and normalizes the command, then checks the active company. Demo creation acquires the company lock and revalidates expiry before reserving a key. Conventional creation does not take the company quota lock, so the unique index arbitrates concurrent same-key requests directly.

The reservation uses PostgreSQL `INSERT ... ON CONFLICT (company_id, operation, key) DO NOTHING`. A conflict is read in a **subsequent statement**. At READ COMMITTED that statement sees the result committed by the competing transaction. A matching hash returns the stored HTTP status, location derived from the original payment ID and exact response JSON. A different hash returns `IDEMPOTENCY_KEY_REUSED`. Both decisions precede customer ownership and demo quota checks.

A new reservation checks the owned customer and the 500-payment demo quota, inserts the PENDING payment and sequence-one creation event, and completes the reservation with status 201 and the original JSON. All writes share the transaction and roll back together. The web adapter receives the result only after the proxied transaction commits; it writes the JSON directly, adds `Idempotency-Replayed`, and generates request headers independently. It never stores session headers or serializes a current payment for replay.

The canonical hash is SHA-256 of UTF-8 compact JSON in explicit order: `customerId`, `amount`, `currency`, `description`, `simulationScenario`. UUIDs use lowercase; amount is a scale-two decimal string; currency is BRL; description is stripped with Unicode whitespace and empty becomes null. A dedicated Jackson tree serializer fixes field insertion order and escaping independently of the web mapper. Command validation also rejects BigDecimal values with more than two fractional places, including trailing zeroes; HTTP additionally rejects numeric JSON and unsupported decimal syntax before conversion.

Transaction-local `lock_timeout` bounds company locking and unique-index arbitration to three seconds. Success restores the caller's previous setting. Lock timeout or deadlock exits the failed transaction; HTTP returns 503 `CONTENTION_TIMEOUT` and `Retry-After: 1`, instructing the client to retry the same key and payload.

## Alternatives and consequences

Catching a JPA uniqueness exception and then reading the previous result in the same transaction is unsafe: the persistence transaction may be marked rollback-only, and PostgreSQL rejects further statements in an aborted transaction. `ON CONFLICT DO NOTHING` treats the expected race as ordinary arbitration instead of an error. A separate reservation transaction would leave stranded reservations after failures and require recovery rules. Keeping reservation, event and response in one transaction avoids that state.

Demo creation intentionally serializes writes within one company to enforce the quota with a count under the company lock. Conventional companies avoid that serialization. The short transactions perform no external calls. Replay still requires an active company; it is not authorization bypass. Keys remain for the company lifetime, so storage cleanup belongs to the later demo cleanup task.

## Verification design

PostgreSQL 18.6 Testcontainers tests use independent connections and observe actual database lock waits. Tests cover original snapshot replay after a persisted status change, canonical hashing, tenant and case-sensitive keys, different-payload conflicts, simultaneous conventional same-key creation, the demo quota boundary, expiry after a lock wait, and both timeout paths. Test-only PostgreSQL triggers inject failures before event insertion and before response completion; assertions verify zero surviving payment/event/reservation rows and successful retry with the original key. No production failure hook is introduced.

## Official references consulted

- [PostgreSQL 18 INSERT](https://www.postgresql.org/docs/18/sql-insert.html) and [transaction isolation](https://www.postgresql.org/docs/18/transaction-iso.html), consulted by the coordinator for unique-index arbitration and statement snapshots.
- [Spring Framework 7.0.10 Transactional](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/transaction/annotation/Transactional.html) and [JdbcTemplate](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/jdbc/core/JdbcTemplate.html), matching the resolved backend version.
- Jackson 3.1.7 official source: [JsonMapper](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-3.1.7/src/main/java/tools/jackson/databind/json/JsonMapper.java) and [ObjectNode](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-3.1.7/src/main/java/tools/jackson/databind/node/ObjectNode.java), matching the resolved backend version.
