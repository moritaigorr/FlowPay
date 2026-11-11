# FlowPay project agents

These six reusable project roles support the portfolio goal in the root AGENTS.md. The main agent remains responsible for architecture decisions, task ownership, integration and communication with the owner.

Root AGENTS.md also integrates the supplied Vibe Coding Kit: use docs/guides/coding-standards.md for implementation, docs/design/guidelines.md and docs/design/design-system.md for UI and docs/guides/quality-checklist.md for evidence. docs/product/prd.md and docs/architecture.md summarize existing requirements; they do not replace detailed task contracts. Raw archived kit files are references only. No agent model or permission change accompanies this integration.

## Roles

| Agent | Use for | Default ownership |
|---|---|---|
| flowpay_backend | Java API, persistence, security and payment correctness | Assigned backend files and related tests |
| flowpay_frontend | Dashboard, accessibility and API integration | Assigned frontend files and related tests |
| flowpay_quality | Independent verification and reproducible defects | Existing checks; assigned test files only |
| flowpay_performance | Lighthouse, k6, profiling and measured improvements | Assigned measurement scripts/reports |
| flowpay_reviewer | Independent architecture/code review | Read-only |
| flowpay_portfolio | English documentation, demo and interview explanations | Assigned README/docs files |

## Coordination

Implementation assignments are defined by task ID in `docs/plans/2026-11-08-flowpay-implementation-roadmap.md`. Read the live `docs/implementation-status.md` before dispatch. The roadmap defines dependencies, file ownership, interfaces, acceptance checks and evidence; the tracker records actual progress. Creating these documents does not begin execution.

- Use a specialist only when the task benefits from it; do not start all roles for every change. Keep short or sequential tasks in the main agent.
- Before delegation, specify the objective, exact owned files, relevant contracts, acceptance criteria and expected report. Workers do not delegate again.
- Parallelize independent work only after shared API contracts are settled. Each file has one writer at a time; shared configuration, dependencies, migrations and OpenAPI need explicit ownership.
- The coordinator integrates changes and verifies the complete frontend/API/data journey. Reviewer findings are evidence for action, not permission to silently change the user-approved scope.
- The owner should receive a concise explanation of the technical reasoning and verification, preserving their ability to understand and defend the project.

## Configuration and invocation

The TOML files in this directory are the maintained templates. Project-local discovery uses copies under `.codex/agents/`, following the official standalone custom-agent format with name, description and developer_instructions. All six roles and `.codex/config.toml` select `gpt-6-luna` with low reasoning effort to use the lower-cost model consistently. Codex resolves explicit spawn settings before project defaults; agents already running keep the model chosen at their start. No API key or agent service is needed merely to define these local roles.

After changing a template, synchronize its corresponding project-local file before claiming the active definition was updated. Do not change personal/global Codex configuration. Client support and agent discovery must be confirmed before claiming a role has executed; creating these files does not start background workers or implement the application.

Example requests:

- "Use flowpay_backend to implement the agreed payment creation task and flowpay_reviewer to review the result."
- "Have flowpay_quality verify the complete demo journey and report reproducible defects."
- "Use flowpay_performance to establish a baseline, then flowpay_portfolio to document the measured findings in English."

If the active client cannot select a custom role, the coordinating agent can supply the corresponding developer_instructions as a scoped subagent task, subject to available tools and the user's authorization. Do not pretend that the custom role was natively loaded.

Official format: https://learn.chatgpt.com/docs/agent-configuration/subagents
