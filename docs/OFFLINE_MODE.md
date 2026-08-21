# Offline Mode

In **Local Only** mode, Autonova never selects a remote endpoint. A user-imported compatible MediaPipe `.task` model can answer ordinary chat using local Room memory and locally indexed text-document excerpts. Projects, queued tasks, personal memories, activity, and selected document-tree files remain on-device.

Local document retrieval is a bounded lexical chunk index, not a vector embedding index. It supports readable text imported from a user-selected folder. PDF, Office parsing, vision analysis, web research, hosted image generation, GitHub synchronization, and remote task orchestration are unavailable unless compatible local providers are added or the user changes modes.

## Scoped workspace and storage controls

The Files & Storage screen reports the approximate size and document/chunk count of the local retrieval index. Clearing that index removes only Room-held retrieval chunks; it does not alter the original document in the owner-selected Android folder and does not transmit content. The screen also exposes a separately confirmed local activity-cache clear action. It does not clear tasks, memories, source documents, or protected remote account records.

Within the selected document tree, Autonova can search file and folder names recursively and edit supported text files of up to 1 MB after review and a second save confirmation. The editor remains local. For portability, Autonova can copy a selected file or folder to a second Android folder selected by the owner. It deliberately does **not** claim arbitrary atomic moves: Android document providers can differ in write and cross-provider support, so exports preserve the original and deletion stays a separate confirmed action. Android does not grant Autonova unrestricted shell execution, arbitrary source-code execution, or broad file-system access through this workflow.
