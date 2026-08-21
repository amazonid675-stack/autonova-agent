# Security and Consent

Tokens and sensitive Android settings use encrypted storage. Hosted provider and GitHub credentials are encrypted at rest and omitted from activity payloads. The app uses public HTTPS endpoint validation, browser sign-in, scoped Android storage, system-mediated screenshot capture, runtime permissions, and explicit confirmation for sensitive device actions.

Autonova never bypasses Android permissions, account authorization, website controls, or repository access. Local Only mode is the default. Users can revoke scoped storage, capability grants, sessions, and GitHub connections.
