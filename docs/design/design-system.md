# FlowPay design system

Status: visual direction approved by the user on 2026-11-08 with "pode fazer assim", referring to the immediately preceding document-aligned refinement. The approved target is [Precision Glass refined](concepts/precision-glass-refined.png). R03 adds the coded responsive shell and shared visual primitives; R14 adds the real login/logout and session-guard UI; real payment screens and API integration remain later work. Verification evidence and limits are recorded in [UI foundation](ui-foundation.md) and `docs/design/implementation/`.

The selected language is light/blue, contemporary and operationally precise, with selective material depth, a compact flow mark, neutral customer tiles and opaque financial data surfaces. Earlier [three-direction concepts](concepts/README.md) remain historical explorations, not competing implementation targets.

The [direction record](visual-direction-proposal.md) explains the rationale and baseline values. The image supplies composition and visual intent; it is not a screenshot of a working application or an exact CSS measurement source.

## Approved direction and implementation work

| Decision | Current state | Owner / task |
|---|---|---|
| Selected visual concept and theme | Precision Glass refined; light theme with blue primary accent | Main + frontend / R03 |
| Brand mark and asset source | Original compact SVG adaptation at frontend/public/flowpay-mark.svg | Frontend / R03 |
| Base, text and accent | Baseline values below; verify rendered combinations and refine where needed | Frontend / R03 |
| Font families, origin, license and fallbacks | Segoe UI system font, Arial/sans-serif fallbacks; no font redistribution or remote font request | Frontend / R03 |
| Layout/navigation and responsive behavior | Left navigation, concise header, one primary action, compact filters and opaque table; adapt mobile without losing actions | Frontend / R03 |
| Radius, shadows, glass and spacing values | Cohesive scale; selective navigation/action depth; avoid blanket blur and oversized ornamental shapes | Frontend / R03 |
| Motion gesture and reduced alternative | 140ms color/background transitions; 0ms for reduced motion; no artificial processing animation | Frontend / R03 |
| Component examples/catalog | Button variants, status badges, loading and not-found primitives; no public fake-data showcase | Frontend / R03 onward |

## Semantic token contract

Baseline values carried from the approved proposal:

| Role | Baseline |
|---|---|
| background | #F7F9FC |
| surface | #FFFFFF |
| foreground | #14213D |
| muted-foreground | #526079 |
| primary | #1D4ED8 |
| primary-foreground | #FFFFFF |
| border | #DDE3EC |

Centralize these as semantic CSS variables/Tailwind tokens. Verify contrast for actual states/surfaces; the image does not establish tested ratios. Remaining roles and scales must be defined consistently in R03:

- background, foreground, surface, surface-foreground.
- primary, primary-foreground, secondary, secondary-foreground.
- muted, muted-foreground, border, input, ring.
- success, warning, destructive and their readable foreground/background variants.
- Consistent radius, spacing, type, shadow and motion scales.

Use these roles through actual component variants. If a selected aesthetic needs material tokens such as a glass tint/edge or backdrop blur, define them once with an opaque fallback. Keep financial information readable without blur, animation or decorative assets.

R03 implementation values live in `frontend/src/styles/globals.css`: heading #080E25; secondary #EAF1FF / #123AAB; muted #EDF1F7; input #C9D3E4; focus ring #2458E4. Approved status #146B32 on #E8F7ED; Pending #935000 on #FFF3DE; Rejected #B42330 on #FFEBEE; Cancelled #455169 on #EDF0F5. Status always includes a text label and dot. Primary text is white; destructive text is white. White opaque surfaces require no backdrop-filter fallback.

Scale: 4/8/12/16/20/24/28/36/44/52px spacing; 9px badge, 14px navigation, 16px main-panel and 24px pill-button radii. Desktop heading 46px, mobile 34px; body 15–18px, navigation 18px (13px mobile), caption 12px; all use a single system-font stack. Focus is a 3px outline offset by 4px. Shadow is deliberately subtle; only mark, active navigation and primary action use selective blue material treatment.

The foundation replaces the concept's synthetic populated table, company/user identity and creation controls with explicit unavailable-page content. This is a scoped R03 deviation required by the roadmap: sample content is not an implemented API. At 640px and below, the rail becomes three always-visible horizontal navigation links. At intermediate widths it remains a narrower rail; no hamburger overlay is needed for three routes.

## Layout and interaction rules for the selected target

- One left navigation with Overview, Payments and Customers; company is a label, not a multi-company switcher.
- A concise Payments heading/subtitle and one Create payment action. Keep the simulation notice visible and calm.
- Uniform compact filters, customer names more prominent than IDs/metadata, right-aligned tabular amounts, approximately 56–64px rows and 40–44px controls as initial implementation guidance.
- Neutral customer tiles; semantic colors are reserved for payment status. Keep all four status labels explicit.
- No giant glass ribbon, decorative hero, balance/revenue card or unsupported action. Use depth sparingly in the small brand mark, active navigation and primary action.
- On smaller screens, preserve search, status/date filters, creation and detail access through content-driven reflow. A bounded table scroll must not cause whole-page overflow.
- Button/input/navigation states include keyboard focus, hover, active, disabled and loading where relevant. Error/empty/expiry states must use the same visual language.
- Proposed motion signature: active navigation indicator and detail entry/exit, without artificial processing delays; provide an immediate reduced-motion alternative. Exact durations and implementation remain task-level decisions.

## Initial component inventory

| Component/pattern | Required variants or behavior |
|---|---|
| Button | Primary, secondary, quiet, destructive; hover/focus/active/disabled/loading |
| Form fields | Label, description, validation, error, disabled; amount stays a decimal string |
| Status badge | Pending, Approved, Rejected, Cancelled; readable text plus color |
| Payments table | Real server pages, filters, row navigation, aligned values and stable empty/error/loading states |
| Navigation | Current route, visible focus, keyboard and usable mobile arrangement |
| Dialog/sheet | Named title, focus management, Escape/close and correct focus return |
| Feedback | Inline form errors, alerts, optional toast, skeleton and filtered-empty state |
| Payment timeline | Real ordered events, timestamps, reason codes and legal pending-state actions |
| Summary | Defined backend metrics; no invented trends, balances or revenue |

Use the agreed shadcn/Radix base and React stack. Do not add another kit or Storybook as an incidental requirement. A lightweight local component showcase is enough initially; choose extra tooling only for a demonstrated need.

## Acceptance before visual completion

- Inspect actual screens around 375/768/1440 CSS pixels and at 200% zoom; adjust breakpoints to content.
- Check real contrast combinations, semantic structure, keyboard/focus/dialog behavior, long content and full state coverage.
- Verify reduced motion and usable fallbacks for any material effects.
- Run the actual frontend checks and measure production screens under docs/guides/performance-guidelines.md when available.
- Record evidence in the assigned task report; no accessibility/performance claim follows from a mockup alone.
