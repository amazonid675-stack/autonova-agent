# GitHub Workflow

Autonova is maintained as a private GitHub repository. Use `main` as the protected release branch and `develop` as the integration branch. Make scoped feature branches such as `feature/android-mobile-sync`, `feature/document-memory`, `fix/task-retry`, and `security/provider-boundary`.

Every change should be reviewed through a pull request. The Android validation workflow installs API 35, generates a temporary Gradle wrapper when one is not checked in, and runs the Android unit-test target. It uses read-only repository permissions and no secrets.

Before requesting review, run the web validation commands locally:

```bash
pnpm test
pnpm check
```

For Android changes, open `android/Autonova` in Android Studio, use JDK 17 and Android SDK 35, then run the app and its unit tests. Never commit generated keystores, `local.properties`, provider credentials, OAuth tokens, or build artifacts.
