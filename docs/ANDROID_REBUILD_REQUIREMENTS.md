# Android Rebuild Requirements Notes

## Diagnosed failure in delivered APK

The delivered Android build opens into a setup-gated shell that tells the user to manually enter an HTTPS endpoint and a session cookie before the agent can do anything. This does not satisfy the requested product experience because the PDF specifies a **personal AI employee** controlled primarily from Android, not an empty shell that depends on hidden manual setup.

## Key PDF requirements relevant to the rebuild

The PDF requires the Android app to be the **primary client** and to provide a usable, modern interface with the following primary screens: **Home, Chat, Tasks, Projects, Files, Memory, Tools, GitHub, Settings, Activity/Logs**.

The main chat surface must support **text, voice, image, file, camera, screenshots, and URLs**.

The agent must behave like an **agent platform**, not a simple chatbot. It must follow the task lifecycle **RECEIVE → UNDERSTAND → CLASSIFY → PLAN → EXECUTE → OBSERVE → VERIFY → REPAIR → COMPLETE**.

The Android application should provide **chat, voice input, voice output, camera input, screenshot analysis, file access, document access, local memory, notifications, background tasks, local AI, cloud AI, web access, share-sheet integration, deep links, Android intents, clipboard integration, local database, and secure credential storage**.

The user must be able to issue a task such as **"Build me a website"** and have the app create an **agent task**, not merely treat it as a plain chat reply.

The system must use a **hybrid local-first architecture** where the phone handles practical local work and the server handles expensive work.

The app must implement a **tool system**, **memory layers**, **permissions**, **human confirmation for dangerous actions**, **GitHub as a first-class tool**, **image generation**, **document workflows**, **personal file-system access by explicit user choice**, and **secure secret handling**.

## Rebuild priorities implied by the failure

1. Replace manual session-cookie setup with a genuine first-run onboarding and authentication flow.
2. Make the command surface usable on first launch, with visible capabilities and actionable prompts.
3. Ensure task-style prompts create visible task records and execution states.
4. Surface actual mobile capabilities and their permission states, instead of hiding them behind inert screens.
5. Preserve security requirements: scoped file access, explicit confirmation for dangerous actions, and no secret leakage.
