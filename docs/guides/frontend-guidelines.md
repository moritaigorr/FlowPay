# FlowPay — frontend construction guide

This guide translates the portfolio goal into frontend implementation rules. The architecture specification remains the source for business behavior. Library choices below are a baseline, not a request to install everything immediately.

For visual work, also read docs/design/guidelines.md and docs/design/design-system.md, adapted from the user's Vibe Coding Kit. The user approved Precision Glass refined on 2026-11-08; its local image and baseline tokens are the current visual target. Earlier concepts remain exploration history.

## Preferred libraries and boundaries

| Concern | Choice | Boundary |
|---|---|---|
| Application | React + TypeScript + Vite | SPA; TypeScript strict mode; backend remains Java |
| Routing | React Router, declarative mode | URL navigation and search parameters; TanStack Query owns remote data |
| Styling | Tailwind CSS | Semantic CSS-variable tokens and a small global stylesheet |
| Components | shadcn/ui with Radix primitives | Official registry; install only used components; one primitive family |
| Remote data | `@tanstack/react-query` | Queries, mutations, cache and invalidation |
| HTTP | Native `fetch` | One typed wrapper for session, CSRF, errors and cancellation; no Axios by default |
| Forms | `react-hook-form`, `zod`, `@hookform/resolvers` | Form state and input feedback; server still validates every request |
| Icons | `lucide-react` | Named imports; one icon family |
| Notifications | `sonner` when needed | Supplement inline feedback; never the sole place for a form error |
| Class composition | `clsx` + `tailwind-merge` through `cn()` | One utility shared by components |
| Component/utility tests | Vitest + React Testing Library + user-event + jest-dom | Observable interactions and nontrivial utilities; configure a compatible DOM environment |
| End-to-end tests | `@playwright/test` | Real backend for critical integrated journeys |
| Code quality | ESLint + typescript-eslint + React Hooks rules; Prettier | Consistent code and hook correctness |

Conditional tools: Recharts for the optional daily-volume chart; TanStack Table only if table behavior outgrows a semantic table with server pagination; MSW for isolated component tests when HTTP mocking is useful. Do not add them in advance. Use native Intl for dates/currency rather than adding a date library for basic display.

No Redux/Zustand/global state framework, second component kit, animation framework, Next.js migration or frontend server by default. Local component state handles transient UI; Query handles server state; URLs hold shareable filters. A demonstrated new need may justify revisiting a choice.

## File organization

```text
frontend/src/
  app/                 # router, providers, application shell
  components/ui/       # installed shadcn primitives
  components/shared/   # reused product components
  features/
    auth/
    dashboard/
    customers/
    payments/          # feature components, hooks, API functions, schemas
    demo/
  lib/                 # HTTP client, query client, small format utilities
  styles/              # tokens and global styles
```

Create directories only when needed. Feature folders own their logic. Avoid giant page components, circular imports and duplicate API clients. Export a feature's public surface explicitly when needed; do not add barrel files everywhere.

## API integration and state

- Use `/api/v1` under the same origin. Configure the development proxy instead of weakening production cookie/CSRF settings to make local development work.
- The HTTP wrapper must handle 204 and empty responses, check HTTP status, parse problem+json errors and retain requestId. Do not assume every error response contains valid JSON.
- Pass AbortSignal into fetch for cancellable queries. Distinguish cancellation from user-visible failure.
- Obtain the CSRF token via the documented endpoint and send it on writes. Follow the actual configured server token/header convention and refresh after authentication changes; never guess it from a template.
- Query keys include all filters, page parameters and relevant identity scope. On logout, expiry or company/session change, cancel in-flight queries and remove the previous session's cached data before showing another session.
- Use explicit bounded retry policies for read failures. Do not retry 400/401/403/404/409 automatically. Configure mutations with retry disabled; a payment creation retry must preserve its original key and canonical request.
- Generate one idempotency key per logical creation attempt. Reuse that key and payload after an uncertain network result; editing the payload starts a new attempt with a new key. Never generate a fresh key automatically for the retry of an uncertain submission.
- Do not optimistically mark a payment approved/cancelled or update financial metrics before confirmation. Invalidate/refetch affected lists, details, history and summary after success.
- Keep list filters/page in URL search parameters. Changing filters resets the page. Handle invalid parameters and do not request unbounded lists to simulate server pagination.
- Do not add polling, WebSockets or SSE to this synchronous MVP. Explicit refresh and cache invalidation are sufficient.
- Never derive authorization from a hidden button. Route and action guards improve UX; the backend independently enforces every permission.

## Forms and money

- Use React Hook Form with Zod for payment/customer forms. Define input schemas near their feature. Match backend limits; show field errors and a useful form-level failure message.
- Associate every control with a label. Use shadcn Field/FieldGroup and accessible descriptions/errors where supported by the installed version. Preserve native semantics and compose primitive components according to their actual documentation.
- Use text-based decimal entry with an appropriate inputMode for payment amount. Keep the canonical value as a decimal string. Specify accepted formatting clearly and reject ambiguous input, exponent notation and extra decimal places.
- Do not use parseFloat, arithmetic on Number or silent rounding for money. If exact display formatting requires minor units, parse the validated decimal string to integer cents with BigInt and format without an intermediate floating-point amount.
- The backend calculates totals, rates and payment outcomes. Display returned values using the documented units. Approval rate without a denominator shows no data, not zero.
- Disable relevant controls while a mutation is pending and retain entered values after recoverable failure. Show progress without layout jumps. Error messages must not require opening developer tools.

## Product screens and scope

1. Overview: defined summary metrics, period control, optional meaningful chart and recent payments.
2. Payments: server-backed filters, pagination, clear statuses and create-payment flow.
3. Payment details: amount, customer, timestamps, current status, chronological events and legal actions.
4. Supporting views: login/demo entry and a minimal customer list/create flow.

Only expose actions implemented by the API. No decorative Export, Settings, Notifications or Search controls that do nothing. Do not add fake trends, fabricated percentage comparisons, revenue labels for approved volume or bank-balance concepts absent from the model.

Demo guide: create -> repeat original request -> verify same ID -> process -> inspect final history. Request/response inspection is an optional developer view, not the main product screen. Clearly identify synthetic sample data and the simulation environment.

## Visual quality

- Implement the approved light/blue Precision Glass refined theme first. A theme toggle/multiple themes remain optional scope; the earlier dark/editorial alternatives are not simultaneous implementation targets.
- Define tokens for background, surface, foreground, muted text, border, accent, focus, success, warning and danger. Reuse them in components instead of arbitrary per-screen colors.
- Use a deliberate accent, readable typography, consistent spacing and a clear hierarchy. The selected direction may use expressive typography and selective glass/chrome/gradient/motion as a purposeful signature; preserve high-contrast financial text, opaque/reduced-motion fallbacks and measured performance. Do not add effects or imagery without a role in the payment experience.
- Give the overview, table and details distinct, intentional layouts. Do not turn every item into a competing card. Use tabular numbers for financial columns and align numeric data consistently.
- Prefer component variants for shared appearance. Use className for layout and feature-specific structure; update shared tokens/variants for cross-screen visual changes.
- Use one icon family. Icon-only buttons need accessible names; decorative icons are hidden from assistive technology. Status uses text plus color.
- Use CSS transitions for short purposeful feedback. Respect prefers-reduced-motion; do not delay real operations to simulate processing.
- Handle long names, large amounts, missing optional descriptions and real error messages without clipping important content. Permit horizontal scrolling inside a table when necessary, not across the entire page.

## Accessibility and responsive behavior

- Target WCAG 2.2 AA behaviors relevant to the screens; verify concrete checks rather than claiming certification.
- Use semantic headings, landmarks, buttons, links and table headers. Every dialog has a title, keyboard support, managed focus and a working close action. Restore focus appropriately.
- Keep focus indicators visible, text contrast adequate and controls usable by touch. Do not make a tooltip the only source of necessary information.
- Show loading, empty, filtered-empty, error, success and session-expired states. Announce important asynchronous feedback accessibly.
- Verify at approximately 375, 768 and 1440 CSS pixels; test 200% zoom and keyboard navigation on critical flows. Avoid fixed viewport heights that hide mobile controls.
- Make English UI copy concise and consistent. Explain that all payments are simulated and no real money is processed.

## Verification and completion

### Performance

Follow `docs/guides/performance-guidelines.md`. Use the React-relevant Vercel React Best Practices during implementation and review; apply Vite/SPA equivalents and keep TanStack Query as the remote-data solution.

- Paginate and filter on the backend. Never load the entire payment history for a table or dashboard.
- Set explicit Query staleTime/refetch behavior appropriate to each resource; retain mandatory invalidation after mutations and session cache clearing. Avoid redundant calls and parallelize genuinely independent reads.
- Load optional charts and heavy secondary screens on demand with React.lazy/dynamic import where useful. Preserve accessible loading states and avoid turning every small component into a separate chunk.
- Use named icon imports, appropriately sized assets and stable layout dimensions. Inspect the production bundle before adding substantial UI dependencies.
- Investigate rendering bottlenecks with browser/React profiling before adding manual memoization. React Compiler remains an optional evaluated choice, not a mandatory new dependency.
- Run Lighthouse against the production build once core screens work, using authenticated pages and documented mobile/desktop settings. A login screen score alone does not verify the dashboard.
- Recheck core interactions in the browser; Lighthouse scores alone do not establish responsiveness of the complete application.

R03 configured lint, typecheck, test, build, dev and preview in frontend/package.json. The real integrated test:e2e workflow remains R19; root Playwright is development-only browser QA tooling, not evidence of a completed integrated journey.

Before delivering a functional feature, verify its success path and meaningful failure cases. Exercise session expiration, network/server error, duplicate submission and the relevant business conflict. A mocked test does not prove backend integration.

For rendered changes, inspect screenshots or the running application at mobile/desktop sizes, check for console errors and verify keyboard interaction. Keep useful visual evidence in the delivery when available. Do not call a screen verified merely because the build passed.

Use unit/component tests for validation, decimal handling, nontrivial interaction and error handling. Use Playwright against the backend for the demo/payment journey. Avoid large brittle snapshots and tests that only reproduce class names or static text.

## Official references

Consult documentation matching the installed version; these URLs are entry points, not instructions to upgrade automatically.

- React: https://react.dev/learn
- Vite: https://vite.dev/guide/
- React Router declarative setup: https://reactrouter.com/start/declarative/installation
- Tailwind CSS: https://tailwindcss.com/docs
- shadcn Vite setup: https://ui.shadcn.com/docs/installation/vite
- shadcn forms: https://ui.shadcn.com/docs/forms/react-hook-form
- TanStack Query retries: https://tanstack.com/query/latest/docs/framework/react/guides/query-retries
- React Hook Form: https://react-hook-form.com/get-started
- Zod: https://zod.dev/
- Lucide: https://lucide.dev/guide/react
- Vitest: https://vitest.dev/guide/
- Testing Library: https://testing-library.com/docs/react-testing-library/intro/
- Playwright: https://playwright.dev/docs/intro
- Vercel React Best Practices: https://github.com/vercel-labs/agent-skills
- Lighthouse: https://developer.chrome.com/docs/lighthouse/overview
- Performance measurement plan: `docs/guides/performance-guidelines.md`
