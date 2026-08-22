# Independent Agent Experience Audit

## Audited baseline

The Android app already persists messages, tasks, projects, memories, lexical document chunks, activity, research briefs, learning candidates, capability grants, and private device-context files locally. The repository also provides Local Only implementations for task lifecycle changes, tool-policy changes, research briefs, learning review, grants, usage totals, and device-context storage.

The main usability defect is that several UI handlers still call a remote-only action guard before reaching these local implementations. This creates connection errors for local actions that the repository can already perform.

| Surface | Verified baseline problem | Required redesign outcome |
| --- | --- | --- |
| Tasks and tools | Lifecycle and policy actions are blocked by a remote-only view-model guard. | Use local-first actions and show a clear local activity/evidence result. |
| Device inputs | Camera, image, screenshot, and shared input can be saved locally, but the UI says upload and invokes a remote-only guard. | Save private device context locally by default; offer deliberate remote sharing separately. |
| Research | Browser handoff exists and the repository can retain local research briefs, but synthesis is presented as a remote-only action. | Make browser research and local brief creation independent; request optional provider synthesis after source review. |
| Coding workspace | Scoped folder create, edit, search, export, and archive operations exist but are buried under generic storage copy. | Expose a first-class local code workspace and artifact flow. |
| Image, GitHub, and providers | Some functions require a configured provider or remote agent. | Present an explicit capability card and setup route, not an empty form or a connection failure. |

## Capability truth

Autonova can provide durable offline workspaces, local retrieval, local planning responses, a user-imported on-device model, and Android-consented local file actions. It cannot honestly claim equivalent unrestricted cloud reasoning, image generation, browser automation, coding execution, or external service access without a compatible local model, a configured provider, or the user’s visible consent. The implementation will make those boundaries actionable rather than leave them as blank states.

## Interaction model for the refined app

The Command tab becomes the primary work surface. Every prompt is classified locally into one or more visible work cards: **Answer locally**, **Create a plan**, **Create a code artifact**, **Prepare research**, and **Use an optional online provider**. The classification is deterministic until the user imports an on-device model; it never pretends to have searched, compiled, generated, or sent data when it has not.

| User goal | Offline outcome | Optional online outcome |
| --- | --- | --- |
| Ask a question | Local working answer, local document evidence, and model-readiness action. | User-approved remote response after provider setup. |
| Build or code | A scoped local code workspace, a named source-file plan, and editable artifact templates. | User-approved research, GitHub operations, or provider-assisted code discussion. |
| Research a topic | A query brief, browser-search handoff, and a local source-review record. | Synthesis of owner-selected public HTTPS sources through the configured provider. |
| Create an image | A saved prompt brief with style, size, and intended use. | Provider-backed generation after explicit connection and prompt confirmation. |
| Use a device input | Private on-device context saved with an audit entry. | Deliberate sharing to the configured agent only after the owner asks. |

The More tab becomes a capability catalog organized by **Create**, **Learn**, **Connect**, and **Control**. Each destination presents a capability badge: **Works offline**, **Needs model**, **Needs consent**, or **Needs optional provider**. This makes the next action obvious and prevents blank forms or undifferentiated setup errors.
