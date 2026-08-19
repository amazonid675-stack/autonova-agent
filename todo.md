# Project TODO

- [x] Create protected authenticated workspace shell with responsive navigation and premium visual language.
- [x] Add database models and server contracts for projects, conversations, messages, agent tasks, task steps, tool permissions, memories, activity logs, files, and usage records.
- [x] Complete provider selection end-to-end for built-in and OpenAI-compatible execution paths.
- [x] Implement project workspaces with creation, selection, and context-specific conversation/task data.
- [x] Complete task lifecycle execution through VERIFYING, COMPLETED, FAILED, and cancellation states with visible updates.
- [x] Expose user-controlled memory edit controls in addition to list, create, and delete operations.
- [x] Implement modular tool registry with ASK, ALLOW, and DENY permission policies.
- [x] Implement activity timeline and advanced inspection controls without exposing private reasoning or secrets.
- [x] Implement provider configuration with encrypted-at-rest secret handling, active model selection, and Local Only, Balanced, and Power cost modes.
- [x] Complete secure S3-backed document ingestion and agent-reference workflows without storing file bytes in the database.
- [x] Implement a real public GitHub repository context panel for branches, issues, pull requests, and recent commits without storing credentials.
- [x] Complete cost and usage dashboard with aggregate, session, model, and task-level metrics.
- [x] Implement image-generation tool registration and safe server-side execution path.
- [x] Add route protection and ensure sensitive configuration values are never returned in client payloads, logs, or audit entries.
- [x] Add Vitest coverage for task validation, permission policy handling, secret redaction, and protected core procedures.
- [x] Run type checks, automated tests, visual QA, and responsive QA; refine any issues found.
- [x] Create implementation documentation and a final checkpoint for delivery.
- [x] Add provider-specific token-level SSE transport for OpenAI-compatible providers while retaining safe built-in status streaming and no hidden-reasoning exposure.
