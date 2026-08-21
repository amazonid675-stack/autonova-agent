# Autonova — Personal AI Agent

Autonova is an Android-first personal AI-agent platform for turning questions and requests into visible, user-controlled work. It combines an on-device local model path, private Room runtime state, scoped Android storage, project workspaces, task plans, permissioned tools, durable memory, file context, activity logging, provider configuration, and an explicitly optional remote agent.

## Product Surface

| Area | Current capability |
| --- | --- |
| Conversation | Markdown rendering, attachment and URL context, server-side LLM execution, and concise agent-status updates while a response is generated. |
| Tasks | Persistent QUEUED, PLANNING, RUNNING, VERIFYING, COMPLETED, FAILED, and CANCELLED states with visible steps and user-controlled progression. |
| Memory | Personal, project, task, and short-term layers with create, edit, list, and delete controls. |
| Tools | Modular registry for research, GitHub, isolated execution, documents, images, and API access, with ASK, ALLOW, and DENY policies. |
| Files | S3-backed storage metadata. Text-like files and PDFs can be supplied as server-authorized model context when attached in chat. |
| Providers | Built-in and OpenAI-compatible model-provider configuration, encrypted API-key storage, cost modes, and model selection. |
| Engineering | Read-only public GitHub repository inspection for branches, issues, pull requests, and commits without requiring a token. |
| Android local mode | Default Local Only mode, user-imported MediaPipe `.task` model execution, Room-backed messages/projects/tasks/memory, and local readable-text chunk retrieval. |
| Android connectivity | Local Plus Internet mode for visible browser handoff, and Optional Remote Agent mode requiring a user-supplied HTTPS endpoint and browser sign-in. |
| Device workspace | Scoped user-selected storage with confirmation-gated note, project-workspace, open, share, delete, local indexing, and upload actions. |

## Architecture

The web control plane uses React, TypeScript, Tailwind CSS, Express, tRPC, Drizzle, and the managed database template. The Android companion uses Kotlin, Jetpack Compose, Room, WorkManager, encrypted preferences, MediaPipe local inference, scoped storage, and Android system-mediated capabilities. Remote agent access is opt-in; no packaged production endpoint is used as a default. The database stores structured remote metadata and application state, while file bytes live in object storage.

> Autonova intentionally presents compact action summaries rather than hidden chain-of-thought. It also requires user-controlled policies for powerful tools and keeps sensitive configuration out of activity records.

## Local Development

Run the following commands from the project root.

```bash
pnpm test
pnpm check
pnpm dev
```

For Android builds, run the following command from `android/Autonova` with Android SDK and Java 17 configured.

```bash
gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

The managed runtime supplies authentication, database, LLM, and object-storage configuration. Do not add secrets to source files or committed environment files.

## Verification

The implementation includes tests for protected agent routes, task and tool input validation, device-consent records, session logout, secret redaction, encrypted local configuration, local knowledge retrieval, and Android confirmation surfaces. The main application is checked with `tsc --noEmit`; Android debug and instrumentation packages are compiled before emulator validation.

## Documentation

The current source-level reconciliation is in [`docs/AUDIT_REPORT.md`](docs/AUDIT_REPORT.md). Operating and capability detail is maintained in [`ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`OFFLINE_MODE.md`](docs/OFFLINE_MODE.md), [`ONLINE_MODE.md`](docs/ONLINE_MODE.md), [`MODELS.md`](docs/MODELS.md), [`TOOLS.md`](docs/TOOLS.md), [`MEMORY.md`](docs/MEMORY.md), [`LEARNING.md`](docs/LEARNING.md), [`GITHUB.md`](docs/GITHUB.md), [`SECURITY.md`](docs/SECURITY.md), [`TESTING.md`](docs/TESTING.md), [`TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md), and [`CHANGELOG.md`](docs/CHANGELOG.md).
