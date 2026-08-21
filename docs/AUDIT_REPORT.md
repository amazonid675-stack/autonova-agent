# Autonova Source-Level Compliance Audit

**Scope.** This audit compares the currently checked-out Autonova web and Android source with the user-supplied “Personal Sovereign AI” specification. It records what is demonstrably connected in source, what depends on the hosted agent service, what works locally, and what must be completed before the product can be accurately described as local-first.

> **Audit conclusion:** Autonova is a broad, consent-driven hybrid agent platform with a real Android companion, local encrypted configuration, Room cache, scoped storage, local-model import, and hosted AI-agent workflows. It is **not yet phone-first in its core execution path**: normal chat, research, GitHub operations, provider management, image generation, and most task orchestration require the optional-agent endpoint that is presently preconfigured as the app default.

## Verified Capability Matrix

| Area | Verified behavior | Evidence | Audit status |
| --- | --- | --- | --- |
| Android application | Kotlin/Compose Android app with authenticated screens, deep link, Room cache, WorkManager, scoped storage, notifications, camera, screenshot, share intake, voice, research, GitHub, learning, and activity surfaces. | `android/Autonova/app/src/main/...` | **Implemented** |
| Local encrypted state | Access token, endpoint, model path, device identity, scoped tree, and user preferences use `EncryptedSharedPreferences`. | `data/SecureConfig.kt` | **Implemented** |
| Local model import and invocation | A user-selected MediaPipe `.task` model is copied into private app storage and can produce a local response from the Device Capabilities screen. | `data/DeviceServices.kt`, `ui/AutonovaViewModel.kt` | **Implemented, narrow** |
| Offline chat | When disconnected, normal chat stores a local request and returns a fixed guidance message; it does not automatically route ordinary chat through the imported local model. | `data/AgentRepository.kt` | **Partial** |
| Offline memory and queue | Room persists cached messages, tasks, projects, memories, activity, research, learning candidates, and capability grants. | `data/AgentModels.kt`, `data/LocalStore.kt`, `data/AgentRepository.kt` | **Implemented cache; partial local agent** |
| Document RAG | Hosted uploads provide text/PDF context to the server model, but there is no local parse/chunk/embed/index/retrieve/rerank pipeline. | `server/routers/agent.ts`, Android data layer | **Missing locally** |
| Task lifecycle | Server task plans, task steps, retry, cancellation, verification state, and activity are implemented. The `advance` path marks steps complete without executing a general tool bus or independent verifier. | `server/routers/agent.ts` | **Partial** |
| Research | Android browser handoff and server-side public HTTPS source retrieval with citation labels are implemented; research synthesis is hosted. | `ui/AutonovaApp.kt`, `server/mobileApi.ts` | **Implemented online; not offline** |
| GitHub | Public inspection and confirmation-gated issue/branch/pull-request operations use an encrypted server-side token. Direct local clone, editing, commit, push, conflict resolution, Actions, releases, and local repo persistence are absent. | `server/mobileApi.ts`, `ui/AutonovaApp.kt` | **Partial** |
| Files/device actions | User-selected Android document-tree storage supports create/open/share/delete, with visible confirmation and audit records. Upload is a hosted path. | `data/DeviceStorage.kt`, `ui/AutonovaApp.kt` | **Implemented scoped actions; partial analysis** |
| Voice/camera/screenshot | Android speech recognizer, TTS, camera preview, MediaProjection screenshot capture, and file/context handling are implemented with runtime or system consent. Screenshot/camera analysis still relies on a connected agent upload. | `data/DeviceServices.kt`, `ui/AutonovaApp.kt` | **Implemented capture; partial analysis** |
| Learning | Reviewable preference candidates, approval/dismissal, background candidate preparation, capability grants, outcomes, and revocation are implemented. No measurable improvement lab, benchmark, versioned change, or rollback process exists. | `server/mobileApi.ts`, `sync/AgentSyncWorker.kt`, `ui/AutonovaApp.kt` | **Partial** |
| Tool registry | Server tools expose key, label, description, category, risk, and ASK/ALLOW/DENY policy. Full schemas, versions, network metadata, timeout, retry strategy, output contracts, and dynamic plugin registration are not yet represented. | `server/routers/agent.ts` | **Partial** |
| Provider abstraction | Hosted built-in and OpenAI-compatible text routing exist, with encrypted provider credentials. Android local inference is a separate path rather than one unified capability router. | `server/routers/agent.ts`, `data/DeviceServices.kt` | **Partial** |
| Background work | WorkManager honors user-selected network, charging, interval, learning, cancellation, and notification preferences. It explicitly avoids unsafe unrestricted execution. | `sync/AgentSyncWorker.kt`, `data/SecureConfig.kt` | **Implemented within Android limits** |
| Tests and CI | Server Vitest coverage and Android unit/instrumentation tests run; GitHub Actions builds the Android app and runs emulator instrumentation. The full offline/network/provider/recovery matrix has not been implemented. | `server/*.test.ts`, `android/.../androidTest`, `.github/workflows/android.yml` | **Partial** |

## Mandatory Remote Dependency Finding

The Android build injects a hosted default origin in `android/Autonova/app/build.gradle.kts`, and `SecureConfig.apiBaseUrl()` uses it whenever the user has not supplied a setting. `AutonovaViewModel.beginMobileSignIn()` constructs sign-in URLs from that origin. In normal use, `AgentRepository.submit()` calls the mobile API; without a session it stores a local request and returns fixed guidance instead of using the local model.

This means the current app has a **preconfigured remote-agent dependency**, not an explicit opt-in remote-agent mode. The next implementation phase must eliminate that default, make the selected mode visible, and keep supported offline functions available when no remote endpoint exists.

## UI-Only, Simulated, and Unverifiable Areas

The audit found **no intentionally fabricated customer content or hidden fake credentials**. However, several surfaces can look more complete than their underlying execution path. They are listed below so that implementation work can replace them with real behavior or visible unavailable-state feedback.

| Surface or claim | Source evidence | Current classification | Required correction |
| --- | --- | --- | --- |
| Disconnected “local agent” chat reply | `AgentRepository.submit()` writes a fixed guidance response when no API client exists. | **Simulated guidance, not local reasoning** | Route ordinary chat to a compatible imported local model; otherwise show that no local model is installed. |
| Task progression and verification | `agentRouter.tasks.advance` marks current steps complete and reaches `VERIFYING`/`COMPLETED` without executing a general tool call or independent evidence check. | **State-machine simulation of work progression** | Persist tool calls/results, attach verification evidence, and require a verifier before completion. |
| Local-model availability | `LocalModelEngine` validates file size and attempts MediaPipe inference but cannot prove every user-selected `.task` file is compatible, adequate, or capable of requested work. | **Runtime-dependent and unverified until user model test** | Add compatibility probing, resource checks, a model test prompt, and accurate capability labels. |
| Image generation result | Android invokes a protected server endpoint; availability depends on the configured server-side image provider. | **Provider-dependent external result** | Show selected provider/cost/privacy state and an explicit unavailable result when no provider is configured. |
| Voice recognition | Android uses the installed `SpeechRecognizer` implementation; some device implementations may rely on network services. | **Device/provider-dependent** | Display recognition availability and offline/online support before listening. |
| Screenshot/camera analysis | Capture is real, but agent understanding currently depends on uploading the image to the connected service. | **Real capture; external analysis** | Add local vision routing only when an actual compatible local vision model is installed. |
| GitHub repository work | The implemented operations are hosted API calls for issue, branch, and pull-request flows. Source cannot verify third-party token acceptance, remote conflicts, Actions, releases, local clone, or local commit behavior without an authorized integration test. | **Partial and externally unverifiable** | Add scoped test-repository integration coverage and local workspace/Git metadata flows. |
| Hosted model and research quality | Source validates request paths but cannot prove remote provider quality, availability, current facts, or tool results without controlled live tests. | **Externally unverifiable from source** | Preserve source citations, record retrieval metadata, and add controllable provider-failure integration tests. |

> A visible screen, status label, or state transition is not treated as a completed capability unless it is connected to an executable implementation, handles failure, and has test evidence. The migration work below preserves this rule.

## Security and Privacy Findings

| Finding | Assessment | Required action |
| --- | --- | --- |
| Credential storage | Remote access token and device settings are encrypted at rest; GitHub token encryption is server-side and is not returned to Android. | Retain and test. |
| Secrets in source | No source-embedded GitHub or provider credential was identified in the audited paths. | Retain CI secret scanning/dependency checks. |
| Hosted default | The preconfigured hosted origin is a privacy, availability, and user-control concern because it is used before the user consciously selects remote mode. | Remove from the packaged default. |
| External transmission | Device-action audits are persisted locally and, when configured, synchronized to the server. Transfer intent is generally visible in UI, but privacy mode is not unified across every upload/model/provider path. | Add a single privacy mode and pre-send disclosure. |
| Android execution boundary | Arbitrary unrestricted shell access, invisible browser automation, and permanent background work cannot be safely or reliably granted to an Android app. | Use an app-controlled workspace, Android system APIs, scoped storage, WorkManager, and explicit confirmations. |

## Prioritized Migration Plan

| Priority | Workstream | Definition of completion |
| --- | --- | --- |
| P0 | Local-first operating modes | No built-in production endpoint; local-only, local-plus-internet, and optional remote-agent modes are stored securely and exposed in Settings. |
| P0 | Offline core | Imported local model can service normal chat; local message/task/memory data remains usable without a network or account. |
| P1 | Agent lifecycle and router | Structured plan/tool/model/verification/outcome records, honest unavailable states, and explicit escalation replace text-only progression. |
| P1 | Local knowledge | App-managed document import, metadata, local retrieval records, and clearly labelled availability are present; actual embeddings run only if a compatible local embedding provider is installed. |
| P1 | Improvement Lab | Candidate evidence, test result, benchmark, approval/rejection, version, and rollback are persistent and visible. |
| P2 | Workspace and Git | Scoped app workspace supports local project files and safe Git metadata; direct repository work remains confirmation-gated and documents Android execution limits. |
| P2 | Reliability and documentation | Required operating-mode, models, tools, memory, learning, security, testing, troubleshooting, and changelog documents are maintained with automated failure-path coverage. |

## Explicit Platform Limits

Android can provide user-selected scoped folders, private application storage, local model inference, WorkManager background work, system-mediated screen capture, runtime permissions, browser handoff, and selected share/intents. It cannot safely grant an application unrestricted access to all device files, silently control other apps/websites, run an arbitrary Unix shell over the phone, bypass authorization, or guarantee that a small local model matches frontier cloud models. Autonova will expose the strongest supported alternative and label unavailable features rather than simulate them.

## Audit Evidence

The primary source evidence for this report is `SecureConfig.kt`, `DeviceServices.kt`, `AgentRepository.kt`, `AutonovaViewModel.kt`, `AutonovaApp.kt`, `AgentSyncWorker.kt`, `MobileAgentApi.kt`, `server/mobileApi.ts`, and `server/routers/agent.ts`. The evaluation is current as of the attached specification review and must be revised whenever a capability’s implementation or tests change.

## Audit Checklist Review

| Required audit category | Explicitly covered in this report |
| --- | --- |
| Verified working behavior | Verified Capability Matrix |
| Partial behavior | Verified Capability Matrix and migration plan |
| UI-only, simulated, or mock-like surfaces | UI-Only, Simulated, and Unverifiable Areas |
| Remote/backend dependencies | Mandatory Remote Dependency Finding |
| Offline behavior and limits | Verified Capability Matrix and explicit platform limits |
| Security and privacy | Security and Privacy Findings |
| Missing tests and requirements | Verified Capability Matrix, migration plan, and UI-only table |
| Migration plan | Prioritized Migration Plan |
