# Autonova Autonomy Capability Audit

## Audit basis

This audit compares the supplied 29-page personal-agent specification with the current Android-first Autonova implementation and Android platform behavior. The PDF calls for a hybrid local-first agent rather than a phone-hosted copy of a frontier model: Android acts as the control center, while a protected server handles heavier planning, tools, and cloud-model work. It also requires explicit confirmation for dangerous actions.

The current Android implementation already provides authenticated cloud chat and task lifecycles, persistent projects and memory, tool permissions, scoped file access, voice, camera, screenshot context, share-sheet intake, clipboard confirmation, notifications, a user-supplied local model, browser handoff, local persistence, and background refresh. The pending expansion focuses on turning these features into a coherent, reviewable autonomy system rather than presenting them as disconnected controls.

## Platform facts affecting the requested behavior

Android’s WorkManager is designed for reliable persistent work and reschedules supported work across app restarts and device reboots. It is appropriate for user-enabled periodic synchronization, memory-review preparation, queued task refresh, and notification delivery; it is not a guarantee of unrestricted continuous execution. Long-running or user-initiated activity must use the Android-supported execution model and remain visible to the device owner. [1]

The user’s requested outcome therefore maps to a hybrid design: the device executes private local work and hosts a selected local model when available; the protected server executes cloud-model, research, GitHub, and tool requests once the user connects and approves the requested action. The app cannot lawfully or reliably bypass Android permissions, operating-system limits, network availability, third-party service authorization, or account controls.

## Initial implementation priorities

| Capability area | Current position | Expansion direction |
|---|---|---|
| Web research | Browser handoff only | Add an approved research-request workflow that records sources, output, and citations. |
| GitHub operations | Public read-only inspection | Add connected-account operation intents with least-privilege approval and a complete audit trail. |
| Learning and memory | Editable synchronized memory | Add reviewable learning candidates, retention controls, and a user-controlled background review cadence. |
| Background work | Network-constrained refresh and terminal notifications | Add configurable schedules, charging/network constraints, pause/revoke controls, and visible outcomes. |
| Local AI | User-supplied MediaPipe model | Add transparent availability, private offline routing, and clear cloud fallback when local inference is unavailable. |
| Consent and governance | Per-action confirmations and tool policies | Consolidate capability grants, scope, expiration, activity logs, and revoke actions in one audit surface. |

## PDF reconciliation for the autonomy expansion

| PDF capability group | Current state | Expansion scope | Constraint that remains visible to the user |
|---|---|---|---|
| Natural conversation, streaming, planning, task lifecycle, verification states | Implemented foundation | Improve task classification and show consent-waiting work as a first-class state. | Cloud-quality answers require an active provider and network connection. |
| Phone inputs: voice, camera, image, screenshot, files, document folder, sharing, clipboard, deep links, local database | Implemented | Consolidate discovery, capability status, and approval records. | Android runtime permissions and MediaProjection consent cannot be bypassed. |
| Local/offline AI | Implemented with a user-supplied MediaPipe model | Add a clearer local-first route, offline fallback messages, and local learning-candidate capture. | An inference runtime does not train itself; model quality and hardware limits depend on the selected `.task` model and phone. |
| Internet research, website extraction, source comparison, citation reports | Partially implemented through protected cloud chat and browser handoff | Add approved research requests, source records, summaries, and citations. | Device browser handoff cannot silently inspect authenticated pages; private pages remain under the user’s browser and account controls. |
| GitHub inspection, changes, issues, pull requests, actions, CI repair | Public repository inspection is implemented | Add user-authorized operation requests and an auditable approval path. | Private GitHub operations require a user-authorized account or token with the minimum needed repository scopes. |
| Personal memory, preferences, project memory, learning over time | Editable synchronized memory is implemented | Add reviewable learning candidates, retention controls, local caching, and optional periodic review. | Autonova will not silently retain sensitive information or self-modify model weights. |
| Long-running background work, monitoring, topic updates | Constrained periodic refresh and notifications are implemented | Add user-configured schedules, network/charging constraints, pause/revoke controls, and observable results. | Android controls when periodic work runs; continuous unrestricted execution is not reliable or appropriate. [1] |
| File organization, document creation, analysis, conversion, backups | Scoped folder and uploads are implemented | Add confirmed job requests for document analysis and file operations through selected folders. | The app can only reach files/folders the user selects; provider and storage limits apply. |
| APIs, custom tools, OAuth, OpenAI-compatible models | Provider configuration and tool policies are implemented | Add credential-free tool request definitions and a configuration path for user-provided service credentials. | External services need user authorization and enforce their own API limits. |
| Frontier-level reasoning and unrestricted independent operation | Not a realistic on-device claim | Route difficult work to an approved configured cloud model and make model/tool choices visible. | No Android application can reproduce a proprietary frontier model locally, bypass operating-system controls, or safely act without required account authorization. |

## Product decision

The implementation target is a **high-capability, Android-first hybrid agent**. It will execute local work and offline inference when a compatible model is available, send difficult work to the protected cloud agent when the user enables it, and keep every consequential action reviewable. The next implementation phase adds the missing records and controls so Autonova can remember proposed learning, collect and cite research, request GitHub work, and run user-configured background jobs without pretending that the phone can bypass Android, GitHub, model-provider, or network restrictions.

## References

[1]: https://developer.android.com/develop/background-work/background-tasks/persistent "Android Developers: Task scheduling"
