# FlowPay API performance baseline

**Run date:** 2026-11-10  
**Status:** Initial local baseline; not a production capacity claim.

## Question and workload

What latency and correctness does the real FlowPay API show on this local machine for authenticated payment reads, mixed payment lifecycle work, and conflicting state transitions?

The workload used a conventional disposable tenant, one synthetic customer and 60 seeded payments (21 pending, 19 approved and 20 rejected). It used the supported session login and CSRF flow, real PostgreSQL 18.6, and the production-like Docker Compose frontend proxy with its default rate limits. Each VU received its own cookie jar/session. Read and mixed profiles ramped 1 → 2 → 4 → 1 → 0 VUs over 50 seconds. The conflict profile ran four two-request process-versus-cancel races and required one 200 plus one 409 and a matching final event history each time.

The smoke profile also checked list/detail/history, fresh create, exact idempotency replay, process and cancel. All four profiles used conventional tenant data so demo quotas did not alter results. Every run removed its exact generated tenant and verified cleanup.

## Environment and reproduction

- Host: Windows 11 desktop, Docker Desktop local engine; generator and application share host resources.
- Runtime: Docker Compose, Java 21 backend, PostgreSQL 18.6, Nginx frontend proxy.
- Generator: pinned `grafana/k6:2.3.0` image digest in `performance/k6/run-r20.ps1`.
- Dataset: 60 synthetic seed payments per profile plus profile-created payments; removed after each run.
- Source identifier: working tree on 2026-11-10 (not a clean immutable commit).
- Commands from repository root: `./performance/k6/run-r20.ps1 -Profile smoke|reads|mixed|conflict`.
- The script requires an already healthy local Compose stack and `.env`, but does not read or change `.env`. Generated credentials are process-local and omitted from output. Cleanup is scoped to a random generated R20 identity.

## Results

Latency values below are milliseconds from k6 `response.timings.duration`; percentiles are the observed samples for each endpoint, not an SLO. Throughput is total HTTP requests per second, including each VU's initial CSRF/login calls and all API calls. Per-endpoint payment latency trends contain only that endpoint's requests.

| Profile | Load and volume | Endpoint p50 / p95 / p99 | Request throughput | Correctness |
|---|---|---|---:|---|
| Reads | 1–4 VUs, 50 s, 5,381 iterations, 16,155 requests | List 6.00 / 9.13 / 10.05; detail 5.36 / 8.85 / 9.65; history 5.74 / 8.66 / 9.43 | 323.0 req/s | 16,155/16,155 checks; 0 HTTP failures |
| Mixed | 1–4 VUs, 50 s, 1,752 iterations, 14,028 requests | List 6.27 / 7.59 / 8.51; create 8.30 / 9.86 / 11.55; replay 6.58 / 7.67 / 8.89; process 7.75 / 9.09 / 10.93; cancel 7.64 / 9.01 / 10.19 | 280.2 req/s | 14,028/14,028 checks; 0 HTTP failures |
| Conflict | 2 VUs, 4 races, 26 requests | Create 9.30 / 9.96 / 9.98; process-versus-cancel pair 16 / 19 / 19 | 157.5 req/s over 0.2 s | 4/4 races had one commit and one conflict; final state/history matched |
| Smoke | 1 VU, 1 lifecycle, 11 requests | List 8.72; create 9.87; exact replay 9.38; process 9.05; cancel 10.10 | 66.9 req/s over 0.2 s | 11/11 checks; 0 HTTP failures |

Detailed validation and exact k6 summaries were observed in the local execution output. All profiles reported zero unexpected failures and confirmed disposable-tenant cleanup.

## Interpretation and limits

The measured endpoint latencies are in the low single-digit to low tens of milliseconds in this local setup. These values describe only one machine, one short run per profile, one small dataset and a shared application/generator host. There are no repeated three-run samples, independent resource observations, immutable source revision or remote environment comparison, so these results are directional evidence rather than a capacity estimate or release budget. The generator competes with the API and PostgreSQL for local resources. No before/after optimization has been measured and no performance claim should be copied into a CV as production evidence.

The bounded read and mixed workloads use repeated seeded records rather than a large production-shaped database. Authentication, tenant scoping, CSRF, idempotency, transactional transitions and default proxy limits remained enabled. k6 reused one authenticated session per VU with `noCookiesReset`; fixture provisioning and seeding are outside scenario duration, while the initial CSRF/login calls occur once per VU inside it. The endpoint-specific payment latency trends exclude those auth requests, but aggregate throughput and iteration counts include them.

## Reproduction and next work

Start the healthy local Compose stack, then run each profile with the command above. The PowerShell runner prints k6 percentiles and, in `finally`, removes the generated account/company. If a run fails, it still attempts cleanup and reports whether it was verified. Do not run concurrent measurements on the same stack.

Next, repeat each profile at least three times under pinned, recorded resource allocations and summarize the median. R21 separately measures authenticated production-built frontend screens. Optimize only after evidence identifies a bottleneck, then rerun comparable scenarios and correctness checks.
