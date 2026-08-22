# Android Functionality Audit — Rebuild Baseline

## Verified blocking findings

The uploaded archive passed archive-integrity validation and contains the same Android module structure as the active project. The current prompt route has a blocking defect: `AgentRepository.submit` selects the remote API only when `forceRemote` is set, so ordinary prompt submission cannot use a configured optional remote agent. A connected user is instead routed into local inference.

When no compatible on-device `.task` model is installed, the local route only saves a message explaining that a model must be imported. This is technically honest but fails the expected primary interaction because the command surface appears to accept a prompt without providing an actionable local result or a direct setup flow.

## Rebuild requirements

The repaired Android app must route ordinary prompts to a connected optional remote agent when that mode is selected, retain local-model inference in Local Only and Local Plus Internet modes, and provide an immediate offline action result when no local model is available. Every primary local workflow must visibly persist its result, and every remote-only action must either execute through an authenticated connection or clearly direct the user to the required setup path.

The audit will be extended with an interaction matrix and emulator evidence before release.
