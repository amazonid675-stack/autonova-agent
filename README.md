# Autonova — Personal AI Agent

Autonova is a protected personal AI-agent workspace for turning questions and requests into visible, user-controlled work. It combines conversational AI, project workspaces, task plans, permissioned tools, durable memory, file context, activity logging, provider configuration, and usage visibility in a responsive web application.

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

## Architecture

The application uses React, TypeScript, Tailwind CSS, Express, tRPC, Drizzle, and the managed database template. Browser clients call protected tRPC contracts. LLM and image requests run server-side; provider credentials never reach the browser. The database stores structured metadata and application state, while file bytes live in object storage.

> Autonova intentionally presents compact action summaries rather than hidden chain-of-thought. It also requires user-controlled policies for powerful tools and keeps sensitive configuration out of activity records.

## Local Development

Run the following commands from the project root.

```bash
pnpm test
pnpm check
pnpm dev
```

The managed runtime supplies authentication, database, LLM, and object-storage configuration. Do not add secrets to source files or committed environment files.

## Verification

The implementation includes tests for protected agent routes, task and tool input validation, session logout, and common secret-redaction patterns. The main application has been checked with `tsc --noEmit` and visually reviewed across the command, task, memory, tools, GitHub, activity, and settings surfaces.
