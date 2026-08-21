# Architecture

Autonova is an Android-first personal agent with three explicit operating modes: **Local Only**, **Local Plus Internet**, and **Optional Remote Agent**. The Android app owns private model files, encrypted settings, Room runtime state, local document chunks, scoped device files, and WorkManager profiles. Remote agent access is opt-in and uses a user-provided public HTTPS endpoint plus browser sign-in.

The local core is `LocalModelEngine`, `LocalKnowledgeEngine`, Room, and scoped Android storage. The optional server provides authenticated multi-step task planning, hosted research, encrypted GitHub operations, provider integration, storage, and activity records. No capability should claim offline support if it calls the optional server.
