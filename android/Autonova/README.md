# Autonova Android Companion

This native Android module is the mobile control center for Autonova. It uses Kotlin, Jetpack Compose, Room, WorkManager, encrypted configuration storage, modern Android permissions, and an explicit repository boundary for the protected server.

The initial release is local-first: it renders chat and task creation locally, provides Room entities for durable cache state, stores endpoint configuration with Android Keystore-backed encryption, and reserves WorkManager for safe resumable synchronization. OAuth-backed mobile API calls and SSE consumption belong in the repository layer after the production mobile redirect/session configuration is enabled.

## Build

Open `android/Autonova` in Android Studio Hedgehog or newer. Install Android SDK 35 and use JDK 17. Android Studio will create the Gradle wrapper if the repository is checked out without one. Run the `app` configuration on an Android 8.0+ emulator or device.

> Do not paste API keys into this project. The mobile application should obtain short-lived credentials through a future OAuth/session exchange and retain only encrypted local configuration.
