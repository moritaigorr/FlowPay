# FlowPay frontend performance baseline

**Run date:** 2026-11-10  
**Status:** Authenticated local Lighthouse baseline; no application optimization was made from these measurements.

## Question and scenarios

How does the production-built FlowPay frontend render the authenticated Overview, payment list and payment details at Lighthouse mobile and desktop emulation settings?

All three routes were audited three times per device (18 Lighthouse runs). A temporary conventional operator and 60 synthetic payments were provisioned against the local Compose stack. The runner verified login and an authorized payment read, passed the short-lived HttpOnly session cookie to Lighthouse through a temporary header file, and checked Lighthouse's final displayed route. The cookie file and raw Lighthouse JSON were removed after each run; only sanitized per-run summaries and medians remain. The fixture tenant and data were removed with the bounded R20/R21 cleanup script, and cleanup was verified.

## Environment and method

- Host: Windows build 26300; Docker Desktop local Linux engine. Host CPU and memory details could not be read in this session. The frontend container had no explicit CPU or memory limit (`NanoCpus=0`, `Memory=0`); the local k6/Compose evidence must not be compared as isolated hardware capacity.
- Frontend: production-built Vite bundle served by the local Compose Nginx proxy over HTTP at `127.0.0.1:8080`; local frontend image ID `sha256:9659b4330005c898ec6af4e9719ea01d59374d13b76d9fd4da90944eb73af2db`.
- Backend/data: Java API and PostgreSQL 18.6 in the same Compose stack; a conventional synthetic tenant with 60 seeded payments.
- Tools: Lighthouse 13.6.0 (exact dev dependency), Node 24.18.0, Google Chrome 157.0.8103.50.
- Source: working tree based on Git HEAD `2e188c2a8bf7e0bba326534933d7088a5d944dd9`; frontend source is uncommitted in that tree, so the image ID is the immutable identifier for the measured frontend artifact.
- Mobile emulation: 412 × 823 CSS pixels, device scale factor 1.75, simulated mobile Slow 4G / Lighthouse CPU throttling. Desktop emulation: 1350 × 940 CSS pixels, scale 1, simulated desktop Dense 4G / Lighthouse CPU throttling. Lighthouse uses emulated user agents; this is lab evidence, not field-user or INP evidence.
- Each Lighthouse invocation used a fresh temporary Chrome profile and normal Lighthouse cache/storage clearing. No deliberate warm-cache scenario was measured. Backend, proxy and database stayed local.
- Reproduction from repository root: `./performance/lighthouse/run-r21.ps1`. Default execution collects 18 runs, checks each requested route, writes sanitized summaries under `performance/lighthouse/`, and removes temporary credentials, raw reports and the generated tenant.

Metrics are Lighthouse lab values. FCP, LCP and Speed Index are milliseconds; TBT is milliseconds; transfer is Lighthouse's total byte-weight estimate. Scores are the median Lighthouse performance score (0–1) over three runs. The retained individual values are in [per-run results](../../performance/lighthouse/results-2026-11-10.json), and computed medians are in [median results](../../performance/lighthouse/medians-2026-11-10.json).

## Median results

| Screen | Emulation | Score | FCP | LCP | Speed Index | TBT | CLS | Total transfer | JS transfer |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Overview | Mobile | 0.72 | 3,340 | 4,886 | 4,306 | 134 | 0.000 | 379 KiB | 352 KiB |
| Payments | Mobile | 0.67 | 3,315 | 5,717 | 4,637 | 199 | 0.000 | 516 KiB | 482 KiB |
| Payment details | Mobile | 0.67 | 3,300 | 5,692 | 4,782 | 189 | 0.000 | 382 KiB | 356 KiB |
| Overview | Desktop | 1.00 | 243 | 745 | 273 | 0 | 0.021 | 379 KiB | 352 KiB |
| Payments | Desktop | 0.96 | 247 | 853 | 288 | 0 | 0.109 | 516 KiB | 482 KiB |
| Payment details | Desktop | 1.00 | 245 | 747 | 277 | 0 | 0.000 | 382 KiB | 356 KiB |

The mobile `unused-javascript` audit estimated 141 KiB of savings for Overview and payment details, and 237 KiB for the payment list. Lighthouse's estimate is an investigation lead, not proof that all bytes can safely be removed. The repeated payment-list desktop CLS median was 0.109; the other desktop routes were lower, and all mobile samples measured zero CLS.

## Findings and next steps

1. **Investigate mobile LCP and JavaScript transfer first.** Mobile LCP medians ranged from 4.9 to 5.7 seconds, while route samples transferred approximately 352–482 KiB of JavaScript. Lighthouse estimated unused JavaScript, especially on the payment list. Inspect the actual chunk graph and LCP element before choosing a code-splitting or dependency change; route components are already lazy-loaded, so do not assume another route split will address it.
2. **Inspect the payment-list desktop layout shift.** CLS was 0.109 in all three desktop samples for that route. Use the Lighthouse layout-shift-elements diagnostic and a browser trace to identify the shifting element before adjusting layout.
3. **Do not infer production capacity or user-experienced INP.** These are one local measurement set, with browser, API and database on one host. There is no before/after comparison or real-user telemetry.

## Limits and interpretation

All 18 audits completed on their requested authenticated route and the browser session was created using the supported CSRF-protected login flow. The report does not retain cookies or raw Lighthouse artifacts. Three repetitions provide a first median, but only one measurement session and host were used; browser/OS version changes, background processes and Docker contention can move results. Desktop score 1.00 is a lab score, not a guarantee of fast real-user rendering. No Lighthouse CI budget is set from this baseline.

No app code was changed based on these findings. Any performance fix should first inspect the route chunk graph, LCP and layout-shift elements, then repeat the same 18-run protocol and relevant functional/browser checks.
