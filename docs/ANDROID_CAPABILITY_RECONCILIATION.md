# Autonova Android Capability Reconciliation

## Release scope

This document reconciles the PDF-derived mobile requirements with the current Android source tree. Its purpose is to record **what the corrected APK actually does today**, the Android permissions that are genuinely exercised, and the safety boundaries applied to local execution. It should be read together with `docs/ANDROID_REBUILD_REQUIREMENTS.md`.

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
| Voice input and output | Implemented | `data/DeviceServices.kt`, `ui/AutonovaApp.kt` | The command screen requests microphone permission before speech recognition and can read the latest agent response through Android text-to-speech. |
| Camera, image, and screenshot context | Implemented | `data/DeviceServices.kt`, `ui/AutonovaApp.kt`, `data/AgentRepository.kt` | Camera use requires Android permission; screenshot capture invokes Android’s system consent screen; selected visual content is uploaded only through an explicit action. |
| Share sheet and clipboard | Implemented | `MainActivity.kt`, `AndroidManifest.xml`, `ui/AutonovaApp.kt` | Android Share accepts text, images, PDFs, and streams; clipboard text is imported only after visible confirmation. |
| Notifications and background refresh | Implemented with constrained boundary | `data/DeviceServices.kt`, `sync/AgentSyncWorker.kt` | Android notification permission is requested in the capability screen; network-constrained periodic refresh can alert only for terminal task state changes. |
| Local AI | Implemented with user-supplied model | `data/DeviceServices.kt`, `data/SecureConfig.kt`, `ui/AutonovaApp.kt` | A compatible quantized MediaPipe `.task` model is copied to private app storage and runs locally on supported hardware. No model is bundled or silently downloaded. |
| Browser handoff | Implemented with confirmation boundary | `ui/AutonovaApp.kt` | A user can open an HTTPS site in the device browser after a confirmation dialog; the app does not silently browse, log in, post, purchase, or bypass browser permissions. |

## Android permission audit

| Permission or Android capability | Current state | Evidence and constraint |
|---|---|---|
| Internet | Implemented and exercised | Declared in `AndroidManifest.xml`; required for the protected mobile API and browser sign-in. |
| Scoped document-folder access | Implemented and exercised | `ActivityResultContracts.OpenDocumentTree` in `ui/AutonovaApp.kt`; `DeviceStorage.kt` persists only the user-selected URI. No broad storage permission is requested. |
| Browser deep link | Implemented and exercised | `autonova://auth` intent filter in `AndroidManifest.xml`; handled by `MainActivity.kt`. |
| Notifications | Implemented and user-gated | `POST_NOTIFICATIONS` is requested only from the capability screen; terminal task updates are delivered through an Android notification channel after the user grants permission. |
| Microphone / voice input or output | Implemented and user-gated | `RECORD_AUDIO` is requested immediately before speech recognition; Android text-to-speech reads agent responses without recording audio. |
| Camera / screenshot capture | Implemented and user-gated | `CAMERA` is requested immediately before capture; screen capture uses Android’s MediaProjection consent; images are explicit agent uploads. |
| Share-target ingestion and clipboard import | Implemented with confirmation | `ACTION_SEND` intent filters route shared items into `MainActivity`; clipboard content uses an in-app confirmation dialog before submission. |
| Background agent visibility | Implemented with safe scope | WorkManager runs network-constrained read synchronization and can notify terminal task status only; it never launches dangerous tools or external actions autonomously. |

## Security and execution boundaries

The corrected client never asks the user to paste an HTTP session cookie. A random verifier is hashed by the app, bound into the server-side browser sign-in state, checked when the one-time grant is exchanged, and then discarded. `SecureConfig.kt` uses encrypted shared preferences for the resulting bearer token. The server validates the expected origin in `server/mobileApi.ts`, stores only encrypted grant token material, rejects expired or consumed grants, and the mobile API sends `Authorization: Bearer` rather than a copied browser cookie.

The agent is not granted unrestricted phone access. Local storage is limited to a document-provider folder selected by the user, and local file creation, sharing, opening, and deletion are confirmation-gated. Controls that call protected cloud APIs show clear feedback if the account is disconnected or a request fails; they are not represented as completing silently.

## Intentional safeguards

The mobile application does not treat broad device access as standing permission to act. Voice begins only after microphone approval. Camera capture, screen capture, file upload, clipboard import, and browser opening are initiated by a visible user action; screenshot capture additionally requires Android’s system-level consent. Local models are user-supplied and stored in private app storage, and the implementation does not claim a bundled model where none exists. Background work is limited to network-constrained refresh and terminal-state notifications, not autonomous browser sessions, posting, purchasing, deletion, or other consequential actions.
