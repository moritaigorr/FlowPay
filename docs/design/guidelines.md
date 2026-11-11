# FlowPay design guidelines

Adapted from the supplied Vibe Coding Kit and the user's FlowPay references. This governs design exploration together with docs/guides/frontend-guidelines.md; it does not select a final style on the user's behalf.

## Brief

- Product: an operational payment simulation dashboard, not a banking product or a marketing landing page.
- Users: fictional company operators and portfolio visitors evaluating a working Java backend.
- Personality: precise, contemporary, expressive. These are a working brief, not an approved palette.
- Main screen action: create a simulated payment; supporting tasks are finding payments, processing/cancelling pending payments and inspecting history.
- The user requested a substantially more modern look after the earlier conventional blue/light dashboard iterations.
- The user approved Precision Glass refined on 2026-11-08. Its light/blue, operationally focused composition is the implementation target; see docs/design/design-system.md and docs/design/concepts/precision-glass-refined.png. Earlier alternatives remain reference history.

## User-supplied visual references

The seven sites were opened and their visible screens captured during the earlier design exploration. They are visual inspiration, not technical validation or approval to install their tooling. Captures represent that session, not a fresh check on every future task.

| Reference | Proposed principle to adapt | Local capture |
|---|---|---|
| [60fps](https://60fps.design/) | Crafted state transitions and purposeful interaction feedback | docs/design/references/60fps.jpg |
| [Glass3D](https://glass3d.dev/) | Selective layered material/edge lighting while keeping data legible | docs/design/references/glass3d.jpg |
| [v0](https://v0.app/) | Clear hierarchy and restrained composition | docs/design/references/v0.jpg |
| [CTA Gallery](https://www.cta.gallery/) | A focused, distinctive primary action | docs/design/references/cta-gallery.jpg |
| [21st templates](https://21st.dev/community/templates) | Contemporary component and layout vocabulary | docs/design/references/21st-templates.jpg |
| [Sitereel](https://sitereel.ai/) | Expressive composition and a memorable brand gesture | docs/design/references/sitereel.jpg |
| [GSAP](https://gsap.com/) | Confident type/contrast and intentional animation direction | docs/design/references/gsap.jpg |

Do not copy identities, proprietary assets, logos or whole screens. Static screenshots do not demonstrate animation quality. A reference to GSAP or Glass3D is not a runtime dependency decision.

## Process and visual constraints

1. Read the PRD, architecture, current design-system status and relevant existing screens.
2. Use the approved Precision Glass refined target indexed in docs/design/concepts/README.md. Preserve its direction for routine refinements; broader redesigns are a new scope decision.
3. Maintain the selected concept, principles, tokens, component states and asset sources in docs/design/design-system.md. Mark implementation assumptions explicitly.
4. Implement a bounded screen/component task within the roadmap; inspect real desktop/mobile output and fix observed issues.
5. Report the decision, a meaningful tradeoff and actual evidence. Do not call an image a working interface.

Use strong hierarchy, deliberate grids, readable tabular numbers and one clear primary action. Prefer at most two type families. Glass, gradients, chrome and motion may provide a purposeful visual signature; critical financial text stays on stable high-contrast surfaces. Avoid competing effects, nested cards and scroll-jacking.

Do not add charts, balances, revenue claims or unsupported controls for visual completeness. Always identify simulation; statuses use text beyond color. Forms, focus, errors, empty states and expiry feedback are designed with the normal state.

Motion must help orientation, state or interaction. Provide an immediate/reduced alternative, avoid artificial waits and check cost in the target environment. Prefer existing browser/CSS capabilities for simple effects; only add a motion/3D package after a concrete need and compatibility review.
