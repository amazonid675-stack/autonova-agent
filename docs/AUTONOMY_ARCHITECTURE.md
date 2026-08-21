# Autonova Consent-Driven Autonomy Architecture

## Purpose

Autonova is extended as an Android-first hybrid personal agent. The phone remains the user’s control surface and private-data boundary; the protected backend performs cloud-backed research, repository operations, and multi-step work only after the user has connected and approved the requested capability. This design deliberately separates **asking**, **approval**, **execution**, **observation**, and **retention** so an action can be reviewed, revoked, and explained.

## Execution modes

| Mode | Where it runs | Examples | User control |
|---|---|---|---|
| Offline local | Android device | Local model inference, local learning-candidate detection, scoped-folder inspection, Room cache access | User selects the model/folder and can turn the mode off. |
| Device-assisted online | Android browser and share sheet | Web search in the user’s browser, sharing a URL or selected page content back to Autonova | Browser launch and sharing remain visible Android actions. |
| Protected cloud action | Autonova backend | Public-page extraction, research source records, cloud-model synthesis, GitHub API operations | Requires authenticated connection plus per-operation confirmation or an explicit scoped grant. |
| Deferred background work | Android WorkManager | Synchronization, candidate preparation, result notification, locally queued refresh | User controls enablement, network/charging constraints, schedule cadence, and pause/revoke. |

## New domain records

| Record | Storage | Purpose |
|---|---|---|
| Learning candidate | Local Room cache, then optional memory record | A proposed preference, project fact, or reusable instruction discovered from user-owned context. It remains pending until approved or dismissed. |
| Capability grant | Local Room cache | Records scope, rationale, status, optional expiry, and revoke state for device-assisted capabilities. Existing server tool policy remains the authoritative policy for backend tools. |
| Research session and sources | Protected backend | Captures the request, URLs, extracted title/text excerpt, source status, and user-visible citations for reproducible research. |
| GitHub connection | Protected backend, encrypted token material | Stores user-provided credential material encrypted at rest; the client receives only connection metadata. |
| GitHub operation request | Protected backend activity/task record | Tracks the confirmed request, repository, operation type, result, and error summary without exposing the token. |
| Background profile | Encrypted Android configuration plus Room audit entries | Holds enablement, cadence, network/charging constraints, last-run outcome, and a pause/revoke switch. |

## Approval model

Every sensitive operation follows the same state model:

> **Proposed → user review → approved or declined → executed → recorded → revocable**

The app never interprets a broad Android permission, a connected GitHub account, or an internet connection as standing permission to delete files, push commits, create pull requests, post content, purchase, or access private browser content. A user can grant a narrow reusable policy for low-risk scopes where supported, but each consequential action remains visible in the activity log.

## Research path

1. The user enters a question in the research surface.
2. Autonova opens a visible web search in the device browser after confirmation, or the user supplies public HTTPS source URLs directly.
3. The user shares selected browser URLs/content to Autonova.
4. The backend validates public URLs, extracts permitted public text, records source metadata, and creates citations.
5. A connected cloud model may synthesize the source set; a local model can summarize user-provided content offline when available.

## GitHub path

1. The user adds a fine-grained GitHub token through the Android encrypted-credential field, choosing only the repositories and permissions they intend Autonova to use.
2. Autonova validates the connection without returning the token to Android.
3. The user proposes a repository operation such as reading a file, creating an issue, creating a branch, or opening a pull request.
4. The app displays the exact repository, action, and target details for confirmation.
5. The backend performs the GitHub API request, records the result in activity/task history, and returns a safe summary.

## Learning and background path

1. Local candidate extraction looks for explicit preference and instruction signals in content the user has already placed in Autonova.
2. Candidates appear in a review queue with an approve, edit, or dismiss decision.
3. Approval creates a persistent Autonova memory; dismissal removes the candidate.
4. An optional periodic background profile only refreshes protected state and prepares pending candidates under Android constraints. It does not train model weights, silently browse, or act on external services.

## Known constraints

Android’s background work system schedules reliable work but may defer it for system health. Continuous autonomous execution is not guaranteed. Local MediaPipe inference is a runtime for a user-supplied model, not a training system. GitHub, search providers, cloud models, and private pages require their respective user authorization and network access. These limitations are presented in-app rather than hidden behind unsupported claims. [1]

## Reference

[1]: https://developer.android.com/develop/background-work/background-tasks/persistent "Android Developers: Task scheduling"
