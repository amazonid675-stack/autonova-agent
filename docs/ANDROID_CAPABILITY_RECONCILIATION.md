# Autonova Android Capability Reconciliation

## Release scope

This document reconciles the PDF-derived mobile requirements with the current Android source tree. Its purpose is to record **what the corrected APK actually does today**, the Android permissions that are genuinely exercised, and the native features that remain deferred. It should be read together with `docs/ANDROID_REBUILD_REQUIREMENTS.md`.

The rejected behavior has been removed. Android no longer opens to an empty cookie-entry setup page: the command center offers a clear browser-based **Connect Autonova** action and remains capable of saving a local plan before sign-in.

## Requirement audit

| PDF/mobile requirement | Status | Evidence in implementation | User-visible behavior |
|---|---|---|---|
| Primary Android agent workspace | Implemented | `ui/AutonovaApp.kt` | Command, Tasks, Projects, Memory, and More navigation are available at first launch. |
| Secure account connection without copied cookie | Implemented | `ui/AutonovaViewModel.kt`, `MainActivity.kt`, `data/SecureConfig.kt`, `server/mobileApi.ts`, `server/_core/oauth.ts` | The app opens browser sign-in, receives `autonova://auth`, exchanges a one-time verifier-bound grant, and encrypts the bearer token. |
| Protected cloud chat and task-oriented requests | Implemented | `data/MobileAgentApi.kt`, `data/AgentRepository.kt`, `ui/AutonovaApp.kt` | Connected users receive streamed agent messages. Before connection, a request is saved as a visible local plan with a prompt to connect for execution. |
| Task lifecycle control | Implemented | `data/AgentRepository.kt`, `server/mobileApi.ts`, `ui/AutonovaApp.kt` | Users create, retry, and cancel protected tasks and see server status values. |
| Projects and memory | Implemented | `data/AgentRepository.kt`, `data/LocalStore.kt`, `ui/AutonovaApp.kt` | Users create workspaces and create, edit, or remove synchronized persistent memory. |
| Tool permissions | Implemented | `ui/AutonovaApp.kt`, `server/mobileApi.ts` | Tool policies are shown as `ASK`, `ALLOW`, or `DENY` and may be changed by the authenticated owner. |
| Agent activity, usage, provider, image, GitHub | Implemented | `ui/AutonovaApp.kt`, `data/MobileAgentApi.kt`, `server/mobileApi.ts` | The More area presents activity, usage, provider settings, image generation, and read-only public GitHub inspection. |
| Local file access and document workflow | Implemented with scoped boundary | `data/DeviceStorage.kt`, `ui/AutonovaApp.kt` | The user chooses a document-provider folder; create, open, share, delete, and upload are explicit actions. Create/open/share/delete require confirmation. |
| Local persistence and offline visibility | Implemented foundation | `data/LocalStore.kt`, `sync/AgentSyncWorker.kt`, `data/AgentRepository.kt` | Room caches agent data and a local plan can be shown before an account connects. |
| Transparent errors and execution feedback | Implemented | `ui/AutonovaApp.kt`, `ui/AutonovaViewModel.kt` | A workspace-wide banner reports connection requirements, work in progress, completion, and failure for protected actions. |

## Android permission audit

| Permission or Android capability | Current state | Evidence and constraint |
|---|---|---|
| Internet | Implemented and exercised | Declared in `AndroidManifest.xml`; required for the protected mobile API and browser sign-in. |
| Scoped document-folder access | Implemented and exercised | `ActivityResultContracts.OpenDocumentTree` in `ui/AutonovaApp.kt`; `DeviceStorage.kt` persists only the user-selected URI. No broad storage permission is requested. |
| Browser deep link | Implemented and exercised | `autonova://auth` intent filter in `AndroidManifest.xml`; handled by `MainActivity.kt`. |
| Notifications | Declared, not yet wired | `POST_NOTIFICATIONS` exists in the manifest, but no runtime permission prompt or push-delivery client is implemented in this release. |
| Microphone / voice input or output | Declared, not yet wired | `RECORD_AUDIO` is declared, but there is no runtime microphone permission request, recorder, speech-to-text, or text-to-speech workflow. |
| Camera / screenshot capture | Declared, not yet wired | `CAMERA` is declared, but there is no runtime camera permission request, capture flow, or screenshot-analysis flow. |
| Share-target ingestion and clipboard automation | Deferred | No share receiver or clipboard contract is currently implemented. |
| Background autonomous execution | Foundation only | `AgentSyncWorker.kt` supports scheduled refresh/retry foundations; it does not bypass Android lifecycle rules or execute dangerous actions autonomously. |

## Security and execution boundaries

The corrected client never asks the user to paste an HTTP session cookie. A random verifier is hashed by the app, bound into the server-side browser sign-in state, checked when the one-time grant is exchanged, and then discarded. `SecureConfig.kt` uses encrypted shared preferences for the resulting bearer token. The server validates the expected origin in `server/mobileApi.ts`, stores only encrypted grant token material, rejects expired or consumed grants, and the mobile API sends `Authorization: Bearer` rather than a copied browser cookie.

The agent is not granted unrestricted phone access. Local storage is limited to a document-provider folder selected by the user, and local file creation, sharing, opening, and deletion are confirmation-gated. Controls that call protected cloud APIs show clear feedback if the account is disconnected or a request fails; they are not represented as completing silently.

## Deferred roadmap

Native microphone conversation, text-to-speech, camera and screenshot ingestion, share-target handling, runtime push-notification enrollment, browser/clipboard automation, and local on-device models remain separate capabilities. They must be implemented with runtime permission requests, confirmation where the action is consequential, lifecycle-safe background rules, secure server contracts, and automated Android coverage before they are considered available.
