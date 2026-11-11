# R03 shell visual verification

The implementation was compared with the approved [Precision Glass refined concept](../concepts/precision-glass-refined.png) using `view_image` on the concept and the real desktop/mobile/tablet screenshots. This verifies fidelity of the scoped foundation: rail, brand direction, palette, heading hierarchy, simulation notice and opaque main surface. The populated Payments composition remains intentionally deferred to the real API tasks; this is not a claim of pixel-identical reproduction of the concept's synthetic table.

## Browser evidence

Coordinator-run local Playwright fallback used installed **Chrome 156.0.8078.81**, an isolated headless context and the actual Vite app at `http://127.0.0.1:5175/payments`. CUA was attempted first: inventory returned no browser providers, and selecting native Chrome was blocked because the tool could not confidently determine its current URL on Windows. The required CUA completion summary was issued. No unrelated browser session was operated.

| Evidence | Checked |
|---|---|
| [375px](shell-375.png) | Three usable navigation links, legible title/subtitle, calm simulation notice, contained main panel, no horizontal page overflow |
| [768px](shell-768.png) | Compact left rail, header reflow, correct selected Payments route, readable content, no horizontal page overflow |
| [1440px](shell-1440.png) | Full rail, single-line topbar, heading hierarchy, selected navigation material, opaque main panel |
| [200% equivalent reflow](shell-200-percent-equivalent.png) | 720 CSS-pixel viewport at DPR 2 (1440 physical pixels), readable reflow and no overflow; **this is not native Chrome 200% zoom** |

The approved concept is 1487×1058; primary desktop inspection used the required 1440px width at 900px height, rather than exact native concept dimensions. This is a recorded viewport difference. The task's native-browser 200% zoom check remains unverified; the 720 CSS-pixel/DPR2 check provides reflow evidence only.

The coordinator's actual browser checks passed: title/main content, no horizontal overflow at all three sizes, visible skip-link keyboard focus, Enter navigation to Customers with H1 focus and `aria-current`, unknown-route recovery, reduced-motion emulation, and no console/page errors. The unavailable pages expose no payment or customer mutation action. Direct browser access does not imply a valid server session.

## Fidelity ledger

| Comparison point | Concept evidence | Render evidence / decision |
|---|---|---|
| Layout/container | Left white rail, subtle divider, blue-tinted page and one opaque main surface | Preserved in desktop/tablet captures; mobile rail reflows to three visible links. No nested card grid added. |
| Heading/copy | Payments plus “Create and track simulated payments.” | Exact title/subtitle retained. R03 unavailable panel and personal-project copy intentionally replace sample rows/metrics/actions. |
| Palette/material | Light blue/white, restrained active-nav depth and blue primary | Semantic baseline preserved; active navigation has a subtle blue gradient and white edge; main content stays opaque. No blanket blur/glass introduced. |
| Typography | Strong dark title and restrained metadata hierarchy | System Segoe UI/Arial produces a readable similar hierarchy. Platform font differences are intentional, with no font download. |
| Navigation/icons | Overview, Payments, Customers; outline symbols, selected Payments | Same order/metaphors; named Lucide outline icons, explicit current route and keyboard focus. No speculative destinations. |
| Brand treatment | Compact blue flow mark beside FlowPay | Small original vector adaptation preserves the approved direction and scales cleanly. It is code-native vector artwork, not an exact raster extraction. |
| Identity/footer | Acme Studio and Alex Morgan are sample concept identities | Replaced by honest “Project preview” and “Personal project”; no session or company is fabricated. Initial long preview label wrapped unnecessarily; shortened and reduced to 14px in compact rail. |
| Populated composition | Filters, six sample rows, metrics, Create payment and pagination | Deliberately absent until implemented API behavior exists. R15–R17 will implement those compositions with authoritative data. |
| Responsive/motion | Desktop concept only | Mobile/tablet adaptations are implementation decisions verified in rendered screenshots. 140ms state transitions become 0ms for reduced motion. |

Above-the-fold copy review: retained FlowPay, Workspace, Payments, Overview, Customers and the exact Payments subtitle. The simulation notice conveys the same simulation/no-real-money statement. Removed sample company/user/counts/table/filter/create/pagination text and added explicit preview/unavailable/personal-project copy as required by R03. No unsupported operational claim or dead action was added.

No material mismatch remains within the approved shell scope after the preview-label repair. Fidelity is intentionally limited to this foundation; a populated financial dashboard cannot be verified before real API integration. Static token contrast and behavioral check details are in [UI foundation](../ui-foundation.md). Lighthouse and real payment/session acceptance remain later roadmap tasks.

R04 additionally verified the production Compose build at http://127.0.0.1:8080, capturing shell-compose-1440.png and shell-compose-375.png. Navigation/focus and no-overflow checks passed. docs-compose-1440.png records Swagger rendered through the same origin with execution disabled, no console errors and no external requests. See the separate foundation verification record for integration checks and remaining limits.

R14 adds the real session-based login/logout UI. Curated empty-form captures: [desktop](login-1440.png) and [mobile](login-375.png). Root inspected both and the authenticated workspace, keyboard/focus and no-overflow behavior against the real packaged backend. Login uses existing tokens and restrained blue depth; company identity is now server-derived. Historical R03/R04 preview descriptions above remain evidence for those stages. See [auth verification](../../verification/2026-11-09-frontend-auth.md) for checks and limits.
