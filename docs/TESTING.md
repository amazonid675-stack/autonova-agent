# Testing

The project uses Vitest for backend contracts and Android unit/instrumentation tests for encrypted configuration, consent synchronization, navigation, scoped storage confirmation, background constraints, and local knowledge behavior. GitHub Actions compiles Android tests, runs them on an API 34 emulator, and uploads a debug APK.

The next required matrix expands live and simulated failures: no local model, model incompatibility, low storage, provider failure, network loss, task recovery, GitHub conflict, permission denial, and background retry. A feature is not complete until its success and relevant failure behavior are tested.
