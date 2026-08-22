# Android Functionality Audit — Rebuild Baseline

## Verified blocking findings

The uploaded archive passed archive-integrity validation and contains the same Android module structure as the active project. The current prompt route has a blocking defect: `AgentRepository.submit` selects the remote API only when `forceRemote` is set, so ordinary prompt submission cannot use a configured optional remote agent. A connected user is instead routed into local inference.

When no compatible on-device `.task` model is installed, the local route only saves a message explaining that a model must be imported. This is technically honest but fails the expected primary interaction because the command surface appears to accept a prompt without providing an actionable local result or a direct setup flow.

## Rebuild requirements

The repaired Android app must route ordinary prompts to a connected optional remote agent when that mode is selected, retain local-model inference in Local Only and Local Plus Internet modes, and provide an immediate offline action result when no local model is available. Every primary local workflow must visibly persist its result, and every remote-only action must either execute through an authenticated connection or clearly direct the user to the required setup path.

The audit will be extended with an interaction matrix and emulator evidence before release.

## Local-first interaction matrix

| Surface | Local Only outcome |
| --- | --- |
| Command | Persists the prompt and returns an immediate local working plan; a compatible model adds generated answers. |
| Tasks | Creates a planning task, records observations, selects and approves a local step, verifies completion, and records activity. |
| Projects and Memory | Stores, edits, and removes Room-backed local data with visible feedback. |
| Files and device inputs | Requires Android consent, then saves selected input privately or performs the confirmed scoped storage action. |
| Research | Stores a local browser-handoff research brief for selected HTTPS sources. |
| Learning and grants | Stores, approves, dismisses, and revokes local review records; approved learning becomes local memory. |
| Tools and Usage | Changes local Ask/Allow/Deny policy and reports Local Only usage. |
| Image and GitHub | States the exact optional remote-agent setup requirement rather than silently attempting a remote operation. |
