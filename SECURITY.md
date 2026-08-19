# Security Model

Autonova is designed around the principle that tool use and configuration are subject to user control. All non-login application routes use the established protected-procedure layer and are scoped to the authenticated user ID.

## Credential Handling

OpenAI-compatible API keys are encrypted with AES-256-GCM before persistence. The application does not return encrypted credential fields to clients; settings responses expose only whether a key is present. Common API key and bearer-token patterns are redacted from persisted assistant text, and activity entries intentionally contain concise action summaries rather than prompts, chain-of-thought, or credentials.

## Tool and Action Controls

The tool registry uses per-user **ASK**, **ALLOW**, and **DENY** policies. Sensitive operations should use ASK by default. The current public GitHub view is read-only and does not collect or retain a personal access token. High-impact repository writes, financial operations, and destructive external actions are intentionally outside the automatic workflow until an explicit confirmation-gated integration is configured.

## File and Provider Boundaries

File bytes are uploaded to object storage; database rows contain names, types, sizes, and storage references only. Text-like files and PDFs are supplied to the model through short-lived server-authorized URLs when the user explicitly attaches them. External provider endpoints must use public HTTPS and are rejected for localhost, local domains, and common private-network address ranges.

## Known Operational Constraint

The managed built-in language-model proxy returns complete responses rather than token-level SSE. While it is processing, the interface shows concise status updates such as context review and response composition. A future provider-specific SSE adapter can add token-level streaming where a configured provider supports it, without exposing hidden reasoning.
