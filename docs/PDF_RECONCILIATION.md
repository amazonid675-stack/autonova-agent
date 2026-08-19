# Autonova Capability Reconciliation

The original PDF describes a hybrid personal agent that combines LLMs, tools, memory, research, files, GitHub, image generation, and Android. The delivered web control plane provides protected workspaces, conversation, task lifecycle, memory, tool policies, S3 files, image generation, provider routing, safe streaming, public GitHub inspection, audit history, and usage visibility.

The Android module provides the primary mobile-client foundation in Kotlin and Jetpack Compose. It includes local task/cache entities, encrypted configuration, a WorkManager synchronization boundary, a responsive command center, and navigation for Chat, Tasks, Projects, Files, Memory, Tools, GitHub, Settings, and Activity.

| Specification area | Current foundation | Next production integration |
| --- | --- | --- |
| Android chat, tasks, projects, memory | Compose routes and local state | OAuth session exchange and REST/tRPC adapter. |
| Voice, camera, screenshot, share sheet | Permission-ready Android module | Add speech, CameraX, MediaProjection, and intent handlers after device testing. |
| Offline data and background work | Room cache schema and WorkManager boundary | Add complete sync queue and conflict resolution. |
| Local inference and RAG | Provider and local-only modes modeled | Connect approved device runtime and secured embedding service. |
| GitHub writes, browser, terminal | Permissioned architecture and public context | Add dedicated OAuth and confirmation-gated sandbox tools. |
| Long-running/multi-agent work | Task lifecycle, activity, cancellation, streaming | Add durable workers before autonomous background execution. |

> Privileged or device-sensitive capabilities are represented as secure boundaries rather than unsafe simulations. No credential is stored in source code or rendered by the UI.
