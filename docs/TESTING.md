# Testing

The project uses Vitest for backend contracts and Android unit/instrumentation tests for encrypted configuration, consent synchronization, navigation, scoped storage confirmation, background constraints, local knowledge behavior, and Room restart recovery. GitHub Actions compiles Android tests, runs them on an API 34 emulator, and uploads a debug APK.

The current failure matrix covers an unavailable local model with local-only recovery guidance, strict remote endpoint policy, provider and GitHub error-message preservation, rejected invalid task and improvement inputs, rejected unsafe workspace Git paths, microphone-permission denial, local task persistence across Room restart, local index clearing, and GitHub operation conflict guidance. Hosted provider quality, third-party GitHub token acceptance, model incompatibility, low storage, and live network loss remain environment-dependent; failures are surfaced as visible, retryable errors rather than simulated success.
