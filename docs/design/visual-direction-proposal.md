# FlowPay — Precision Glass refined

Status: visual direction approved by the user on 2026-11-08 with "pode fazer assim", referring to this refinement. Generated with the built-in image tool on 2026-11-08. This is a static visual target, not application implementation or a verified coded design system.

Preview: [precision-glass-refined.png](concepts/precision-glass-refined.png).

## Intent

Refine the light/blue direction the user previously preferred using the supplied kit's requirements: original contemporary identity, operational clarity, purposeful effects, accessible fallbacks and measurable performance. The subsequent explicit approval selects this refinement over the earlier alternative explorations.

The frontend specialist reviewed the active documents read-only. Its recommendations informed the hierarchy, restrained material placement, neutral customer tiles and component dimensions below. No application, dependencies or tests were produced by that review.

## Concrete changes

- Make payments and their actions the focal point; reduce ornamental header space and remove the large chrome ribbon.
- Reserve depth for the brand symbol, active navigation treatment and primary action. Table/form text belongs on opaque high-contrast surfaces.
- Use consistent neutral customer initial tiles so brand decoration does not compete with semantic status colors.
- Keep one clear Create payment action, compact filters, aligned amounts, quiet pagination and explicit simulation notice.
- Show company as a label, without a switcher implying unsupported multi-company account management.
- Differentiate title, customer, ID and metadata through hierarchy rather than making every element large/bold.

## Implementation baseline

These starting values accompany the approved visual direction and are recorded in docs/design/design-system.md. Generated pixels are not exact token definitions and these combinations have not been contrast-tested in rendered screens. Font choice, detailed component states and responsive/motion adaptation remain implementation work.

| Role | Proposal |
|---|---|
| Background | #F7F9FC |
| Surface | #FFFFFF |
| Foreground | #14213D |
| Muted foreground | #526079 |
| Primary | #1D4ED8 |
| Border | #DDE3EC |
| Typography | One contemporary sans with system fallback; monospace for IDs only; exact family/source/license to confirm |
| Body | 14–16px; title approximately 32–40px; responsive values defined in code |
| Spacing | Coherent 4px-based scale, adjusted to real content |
| Rows / controls | Approximately 56–64px rows and 40–44px controls, preserving touch/keyboard use |
| Material | Restrained navigation lens/brand accent, opaque financial data and fallback |
| Motion | Proposed active-navigation transition and detail-panel transition; immediate reduced-motion alternative |

Keep status foreground/background tokens independent and verify actual contrast combinations. Accent, text and controls require their own keyboard/focus/disabled/loading definitions rather than inferring all states from the image.

## Required next design work

The choice is recorded in docs/design/design-system.md. Derive consistent button/input/badge/table/dialog states in R03 and carry the approved language into payment detail, overview and mobile layouts without adding unrelated product features. Do not re-open broad style selection for routine work within this approved direction.

Implementation and validation belong to the roadmap: actual components, desktop/mobile/zoom inspection, keyboard/focus/contrast checks, reduced-motion behavior, backend integration and production performance measurement. None of those checks is established by this mockup.

## Sources used

- Active docs/design/guidelines.md, docs/design/design-system.md, docs/product/prd.md and docs/guides/frontend-guidelines.md.
- Prior Prism Flow image as content/material context.
- Previously captured Glass3D, 21st, Sitereel and GSAP screenshots attached to the generation as visual references. No new visit or technical documentation validation was performed in this refinement.
- Prompt intent: refine a single light/blue operational payments screen, preserve six synthetic payment rows and supported actions, remove oversized decorative material, improve hierarchy/semantic separation and keep all financial text legible on opaque surfaces.
