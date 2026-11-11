# Frontend foundation

R03 implements the responsive workspace shell, not an authenticated payment product. Payments, Overview and Customers are explicitly unavailable planned pages. There are no mock rows, financial metrics, user/company identities or creation buttons. API authentication, session handling, forms and remote data belong to later roadmap tasks.

## Run and integrate

Use Node 24.18.0 and npm 11.16.0, then run from `frontend/`:

```sh
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
npm run preview
```

The default development URL is `http://127.0.0.1:5173`. The production output is `frontend/dist/`. The SPA host must fall back to index.html for frontend routes; `/api` must retain its own routing/error behavior. Same-origin proxy/container integration is R04.

| Route | Current behavior | Later owner/integration |
|---|---|---|
| `/` | Redirect to `/payments` | Main owns route integration after R03 |
| `/payments` | Unavailable page, no data or payment actions | Replace with R15 PaymentsPage |
| `/overview` | Unavailable page, no metrics | Replace with R17 OverviewPage |
| `/customers` | Unavailable page, no customer actions | Replace with R15 CustomersPage |
| Any other path | Not found with working Payments recovery link | Details/login/demo routes added by main when accepted |

`AppProviders` contains BrowserRouter only. No Query, auth, form or notification packages are installed before their first actual feature. `WorkspaceShell` owns responsive navigation and persistent simulation notice. `AppRoutes` owns declarative route configuration and Suspense loading fallback. The unavailable page is code-split as one small module, not one chunk per tiny component. Native links preserve normal browser routing; URL changes update the title and focus the new H1. Initial load preserves keyboard access to the skip link. Main content is programmatically focusable.

## Visual system and sources

The approved [Precision Glass refined concept](concepts/precision-glass-refined.png) supplies the left rail, light/blue palette, flow mark, selected navigation material, typography hierarchy and opaque main surface. Exact CSS values are implementation decisions rather than sampled pixel claims. Tokens and responsive rules live in `frontend/src/styles/globals.css`; repeated components use those tokens.

Font: **Segoe UI** (Microsoft proprietary system font under the host operating system license; no font files redistributed or downloaded), falling back to **Arial, sans-serif**. This avoids a third-party font request and layout shifts from loading fonts; typography differs slightly by platform. We use no second font. Numbers use tabular figures in status/data primitives and must use tabular figures in future financial cells. Logo: a small original vector adaptation of the accepted flow-mark direction, authored locally in `frontend/public/flowpay-mark.svg`; no external stock artwork. Icons: named Lucide React imports, ISC licensed, outline 1.6–1.7px treatment. Button: adapted from the official MIT [shadcn new-york registry source](https://ui.shadcn.com/r/styles/new-york/button.json), using Radix Slot, cva and the shared `cn()` utility. The source was read before adaptation; semantic CSS variants preserve the agreed design. No component catalog or registry script was installed.

The shell uses opaque white surfaces. Depth is limited to the compact mark, active navigation and primary button; no backdrop blur or motion library is needed. Hover/focus transitions last 140ms, reduced to zero with `prefers-reduced-motion`. A forced-colors fallback preserves selected navigation and component borders. Focus uses a 3px blue ring with a 4px offset.

Components available for integration: `Button` (`default`, `secondary`, `ghost`, `destructive`, `asChild`, native disabled); `StatusBadge` (all four server status names, explicit label plus dot); `RouteLoading`; `NotFoundPage`. Loading buttons should compose disabled and progress feedback when real mutations exist. There is no UI showcase route with pretend payment data. Future form, dialog and table primitives should be installed individually from the official shadcn registry when needed.

## Intended real screens

Overview will use backend-defined summaries and recent payments, with no invented chart/trends. Payments will preserve the approved compact filters, opaque semantic table, aligned amounts and row access, plus a real Create payment action only after integration. Details will use amount/customer, server state, legal actions and a chronological event timeline. The current unavailable panel occupies the main surface intentionally; rendering the concept's sample rows or company/user would falsely imply an authenticated API flow.

## Verification and tradeoff

Meaningful RTL navigation tests cover keyboard Enter navigation, focus transfer, skip-to-content and unknown-route recovery. Browser inspection and evidence are recorded under `docs/design/implementation/`. These tests establish shell behavior only; they do not establish authentication, payment correctness, tenant isolation or backend availability. Those checks remain mandatory in their assigned later tasks.

Token contrast was calculated from the actual sRGB hex values using WCAG relative luminance: foreground/white 15.97:1, muted/white 6.35:1, primary solid/white 6.70:1, Pending 5.65:1, Approved 5.96:1, Rejected 5.70:1, Cancelled 6.98:1, active navigation on its darker background 6.19:1, simulation notice 8.78:1. These static color checks do not establish full accessibility conformance.

Tradeoff: always-visible navigation reflows into three horizontal links on small screens instead of adding a hamburger dialog. All current destinations remain one keyboard/touch action away, with no overlay/focus trap dependency. More destinations would require revisiting the navigation pattern.

Official implementation references consulted during R03: [Vite guide](https://vite.dev/guide/), [React Router declarative installation](https://reactrouter.com/start/declarative/installation), [Tailwind Vite setup](https://tailwindcss.com/docs/installation/using-vite), and the shadcn Button source linked above. Exact dependency engine/peer metadata is recorded by the coordinator in the local build evidence; actual lint/typecheck/tests/build determine compatibility for this slice.

Dependency compatibility decision: TypeScript is pinned to 6.0.3 because the installed typescript-eslint 8.72.1 declares support below 6.1; this preserves the supported lint API range instead of forcing the initially catalogued TypeScript 7.0.2. The coordinator verified and added a scoped minimatch 11.0.1 override for the developer lint dependency chain to resolve 10 moderate audit findings. The override must preserve the consuming API/peer contract; final lint, typecheck, tests and build passed with it. Remove the override when upstream dependencies resolve to patched compatible versions, then regenerate the lockfile and rerun those checks and npm audit. The coordinator's final full and production dependency audits reported zero findings; this does not establish application security.
