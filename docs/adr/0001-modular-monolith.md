# ADR 0001: A modular Java monolith

Date: 2026-11-08. Status: accepted design baseline; implementation pending.

FlowPay needs company isolation, atomic payment/history/idempotency persistence, and a clear portfolio explanation without distributed operations. Use one Java 21/Spring Boot application and one PostgreSQL database, with explicit company, identity, customer and payment modules enforced by Spring Modulith tests. Root-package facades expose immutable records; entities and repositories stay internal. Security and demo orchestration live outside domain modules.

Dependencies flow identity -> company, customer -> company, payment -> company/customer. Trusted actor/company IDs enter through infrastructure; payment does not depend on identity. One database transaction can commit a payment, its event and its original replay response, and PostgreSQL constraints arbitrate concurrent creation. Public facade contracts and lock ordering are fixed in [module-contracts.md](../api/module-contracts.md).

A conventional layered monolith would be simpler initially but gives weaker feature ownership boundaries. Microservices would require network failure handling and distributed consistency without a demonstrated requirement; they are outside scope. This choice costs some boundary tests and public facade discipline while retaining ordinary Spring debugging and transactions. Shared tables are not permission to access another module's repository.

Verify absence of dependency cycles/internal-package access with Modulith, then verify constraints, rollback and simultaneous requests using PostgreSQL Testcontainers. These checks are planned, not results of this ADR. Reconsider service extraction only if an actual independently deployed/scaled requirement emerges; no such requirement exists for this personal simulator.
