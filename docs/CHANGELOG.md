# Changelog

## 0.5.0 — Scoped workspace and local storage controls

- Added local document-index usage reporting and confirmation-gated clearing that preserves source documents.
- Added a confirmation-gated local activity-cache clear action with a narrow, visible scope.
- Added recursive filename search, constrained local text editing, and separate review-before-save behavior inside owner-selected document trees.
- Added copy-based workspace and file export to a second Android folder selected by the owner, plus local ZIP archive creation within the selected tree; original content remains intact because Android does not guarantee safe atomic cross-provider moves.
- Expanded Android regressions for unavailable local models, Room-backed recovery, local index management, storage confirmations, and explicit remote/GitHub error frames.

## 0.4.0 — Local-first reconciliation

- Removed the packaged mandatory remote-agent default.
- Added explicit Local Only, Local Plus Internet, and Optional Remote Agent operating modes.
- Routed disconnected ordinary chat through an imported local model with local memory context.
- Added Room-backed local-first project, task, and memory flows.
- Added user-triggered local text chunking and retrieval for offline local-model context.
- Added source-level PDF compliance audit and required operating documentation.
