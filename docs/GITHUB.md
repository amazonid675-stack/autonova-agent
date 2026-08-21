# GitHub

Autonova supports public repository inspection and confirmation-gated hosted GitHub operations for issues, branches, pull requests, and a selected scoped-workspace text-file write. Fine-grained GitHub token material is encrypted server-side and never returned to Android.

For the workspace-file operation, Android lists only supported text files inside the owner’s previously selected scoped document tree. The owner explicitly selects one file, supplies the repository-relative path and commit message, and prepares a pending operation. The selected text is not sent to GitHub until the owner presses **Confirm and run** on the resulting operation record. GitHub errors such as a changed file, stale SHA, protected branch, or incompatible write are recorded as failed outcomes with conflict guidance; Autonova does not overwrite local Android files in response.

Local cloning, native Git history, full three-way conflict resolution, releases, Actions control, and direct phone-side Git execution are not currently implemented. The workspace write uses GitHub’s confirmed contents API rather than unrestricted Android shell access or an embedded Git daemon.
