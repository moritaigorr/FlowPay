# FlowPay dependency and toolchain baseline

Checked on 2026-11-08 during R00. These are concrete implementation pins, not a claim that the application or every dependency combination has already passed its integration tests.

## Local tools observed

| Tool | Actual result |
|---|---|
| Git | 2.55.0.windows.2; new local branch codex/flowpay-implementation; author already configured |
| Node | 24.18.0; pinned in .nvmrc |
| npm | 11.16.0 |
| Existing Java | PATH resolves an obsolete Oracle Java 8 shim; installed Adoptium JDK is 25.0.4.1 |
| Project Java | Temurin 21.0.13+9 portable runtime downloaded, SHA-256 verified and executed successfully |
| Maven | 3.9.16 portable download, SHA-512 verified; mvn --version successfully used project Java 21 |
| Docker CLI | 29.6.2, desktop-linux context |
| Docker Compose | 5.3.1 |
| Docker engine | 29.6.2; user opened Desktop successfully; PostgreSQL disposable smoke passed |

No global Java installation or persistent Windows PATH was changed. Portable JDK/Maven archives and extracted directories are in ignored .tools/. Fresh clones need their own JDK 21; those binaries are intentionally not versioned.

Use the process-local helper in PowerShell:

```powershell
. .\scripts\use-java.ps1
# Or use an already installed JDK 21:
. .\scripts\use-java.ps1 -JavaHome 'C:\path\to\jdk-21'
```

Once R02 creates the wrapper, use backend/mvnw.cmd with this environment. A globally installed Maven is not required for a fresh clone after the wrapper is available. CI will provision Java 21 explicitly rather than inherit the host PATH.

## Backend pins

| Component | Pin / policy | Basis |
|---|---|---|
| Java | Temurin 21.0.13+9, language/runtime major 21 | Official Adoptium asset metadata and executed binary |
| Maven Wrapper distribution | 3.9.16 | Maven Central artifact and executed portable distribution |
| Spring Boot | 4.0.9 | Stable published 4.0 line; Java 21 is supported |
| Spring Modulith BOM | 2.0.9 | Published 2.0 maintenance line for Boot 4.0; compile/test verification belongs to R02 |
| springdoc webmvc UI | 3.0.3 | Published compatible 3.0 line for Boot 4.0 |
| Spring Session JDBC | Boot-managed 4.0.5 | Boot 4.0.9 dependency management |
| JUnit Jupiter | Boot-managed 6.0.3 | Compatibility correction to the original JUnit 5 sketch; same Jupiter testing approach, no test-framework override |
| Mockito / JPA / Flyway / PostgreSQL driver | Exact versions resolved through Boot 4.0.9 BOM | Avoid conflicting independent transitive pins; retain dependency-tree evidence in R02 |
| Testcontainers | Boot-managed 2.0.5 | Use published 2.x module names and PostgreSQL integration; prove behavior in R02 |
| PostgreSQL image | postgres:18.6-alpine | Pulled and executed PostgreSQL 18.6; digest sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395 |

The original idea/spec named JUnit 5 before concrete Boot compatibility checks. Boot 4.0.9 manages JUnit Jupiter 6.0.3. Use its tested dependency management instead of forcing an older major into the new Spring stack; this changes a test dependency version, not product architecture or acceptance criteria. Tests still use org.junit.jupiter APIs.

## Frontend and contract tooling pins

Versions and engine requirements were read directly from the public npm registry. Node 24.18.0 satisfies the returned engine constraints. Install only when its assigned task needs the package; do not put all optional libraries in the first scaffold.

| Package | Version |
|---|---|
| react / react-dom | 19.3.1 |
| vite | 8.3.6 |
| typescript | 6.0.3; R03 corrected initial 7.0.2 candidate for typescript-eslint compatibility |
| tailwindcss / @tailwindcss/vite | 4.3.5 |
| react-router | 8.5.1 |
| @tanstack/react-query | 5.107.1 |
| vitest | 5.1.0 |
| lucide-react | 1.70.0 |
| @redocly/cli | 2.76.0, development-only OpenAPI validation during R01; Node >=22.12 supported |

R01 installation surfaced the official deprecation notice for the initially considered swagger-cli 4.0.4. Replaced it immediately with pinned Redocly CLI 2.76.0, following the package's maintenance recommendation and checking its published engine requirements and official installation documentation. This affects development tooling only. Run npm run validate:api; schema lint does not prove runtime conformance, which requires later backend checks.

Record exact versions of React/Vite plugins, types, lint tools, Radix components and test helpers in the relevant package manifest/lockfile when installed. Check compatibility then; do not substitute a second framework or server-data library to accommodate a generic template. Schema validation is tooling, not an application runtime service.

R03 compatibility check: typescript-eslint 8.72.1 supports TypeScript >=4.8.4 <6.1.0 and ESLint10, confirmed in its published metadata and [official supported versions](https://typescript-eslint.io/users/dependency-versions/). Selected stable TypeScript6.0.3 instead of the initial7.0.2 candidate. React19.3 types align with runtime; Node24 types align with the local Node24 line. Vite plugin6.2.1 declares Vite8 compatibility; compiler/Babel peers are optional and are not installed to enable a speculative compiler. jsdom31 supports Node24.15+; local24.18 satisfies this. Exact needed test/lint/component dependencies are pinned in frontend/package.json and package-lock.json. No Prettier dependency is added merely for formatting.

The initial lint dependency tree reported ten moderate advisories through one minimatch issue, [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249). Added an explicit minimatch11.0.1 override after checking the patched release, Node compatibility and CommonJS/ESM exports; lint, typecheck, seven behavior tests and production build passed afterward. npm install/audit then reported zero vulnerabilities, including production-only audit. Revisit/remove the override when upstream packages adopt the fixed major. This is a dependency check, not a complete security audit. Root @playwright/test1.65.0 is development-only browser QA, used because the integrated browser was unavailable.

## Container preparation for R04

R04 built with node:24.18.0-alpine, maven:3.9.16-eclipse-temurin-21-alpine, eclipse-temurin:21.0.13_9-jre-alpine and nginx:1.30.6-alpine. The initially inspected Nginx1.30.2 tag was replaced with the current stable patch1.30.6 identified in the official image documentation. Both application images built successfully and the packaged Java/static frontend ran with PostgreSQL18.6. Compose uses explicit local application image tags0.1.0. Read [local development](local-development.md) and the [foundation verification](../verification/2026-11-08-foundation.md) for reproduction and limits.

CI actions were resolved through official GitHub release/tag APIs and pinned by commit: checkout v7.0.1 (3d3c42e5aac5ba805825da76410c181273ba90b1), setup-node v7.1.0 (949feb2413d6458794dcd2491c4babbbce0c15c1), setup-java v6.2.0 (77dd5b2ce225ea11469928ac317850245a74ae78). Their official release pages were checked. The workflow is defined; no remote run has occurred.

## Docker readiness and resolved startup issue

Initially, starting Docker Desktop through its installed executable and official CLI did not establish an engine. Local host logs identified Inference manager startup failure at the runtime reparse-point file dockerInference. The named pipe for the Linux engine was absent. WSL reported docker-desktop as its default WSL2 distribution.

A user-approved attempt to rename only that zero-byte runtime artifact to a backup failed with an OS file-access error. No reset, deletion of the artifact, volume/image deletion or Docker settings change was performed. Never replace PostgreSQL integration tests with mocks because of an environment issue.

The user subsequently opened Docker Desktop successfully. Docker server query returned 29.6.2. Pulled postgres:18.6-alpine and ran an isolated --rm container with a random temporary password, no published ports and no mounted volumes. pg_isready passed; psql returned PostgreSQL 18.6 and SELECT 1 succeeded. Stopped the container in a finally block, triggering automatic removal. R00b environment smoke is now verified; application migrations, Testcontainers and concurrency checks remain R02 and feature work, not covered by this smoke.

R00 was split into R00a (repository/toolchain baseline) and R00b (Docker readiness) to preserve independent work while the external issue was unresolved. That dependency split remains valid.

The startup dialog recurred later while a second engine instance still answered queries. Desktop CLI reported stopped despite an active engine. Following the narrowly scoped workaround reported in [Docker issue 554](https://github.com/docker/desktop-feedback/issues/554), gracefully stopped Desktop, verified no Desktop/backend processes remained and renamed only its runtime run directory to a preserved sibling backup. Recreated an empty run directory and restarted Desktop. Both desktop status=running and engine version=29.6.2 then passed. No images, volumes, WSL distributions or settings were reset. This is an environment recovery; recurrence remains possible and is not an application defect.

## Sources actually consulted

- [Boot 4.0 requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html).
- [Boot 4.0 managed coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html).
- [Spring's Boot 4 generation announcement](https://spring.io/blog/2025/09/02/road_to_ga_introduction/) includes the JUnit 6 transition.
- [Modulith appendix](https://docs.spring.io/spring-modulith/reference/appendix.html) for explicit module detection/compatibility context; verify the pinned line in the actual build.
- [springdoc compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
- [Vite guide](https://vite.dev/guide/) and package engine metadata.
- [PostgreSQL version policy](https://www.postgresql.org/support/versioning/).
- [Docker start command](https://docs.docker.com/reference/cli/docker/desktop/start/).
- Public Maven Central metadata/artifacts and npm registry metadata were fetched from official endpoints during R00. Docker pull/image inspect resolved the PostgreSQL image; the runtime smoke confirmed the database version.

Temurin Windows x64 archive SHA-256: a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9. Downloaded from the official adoptium/temurin21-binaries GitHub release jdk-21.0.13+9. Maven archive checksum was compared against Maven Central's accompanying SHA-512 file before extraction.
