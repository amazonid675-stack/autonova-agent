# Android First-Launch Validation Record

## Automated validation completed

The fresh-launch command-center regression is implemented in `android/Autonova/app/src/androidTest/java/im/autonova/mobile/NavigationTest.kt`. It verifies that a launch without saved credentials visibly presents **Connect your agent**, **Connect Autonova**, and the ready command input; no manual endpoint or session-cookie field is present.

`MobileAuthDeepLinkTest.kt` verifies that only `autonova://auth?code=…` is accepted for the browser return path. `MobileAgentApiTest.kt` verifies the client formats an `Authorization: Bearer` value rather than relying on a browser cookie. The secure storage instrumentation suite verifies encrypted bearer-token and one-time-verifier storage semantics.

The GitHub workflow run [`32368134475`](https://github.com/zerd0098-debug/autonova-agent/actions/runs/32368134475) completed successfully. It compiled unit and instrumentation tests, executed the Android emulator instrumentation suite, and uploaded a debug APK artifact.

## Server handoff contract validation

Against the running workspace, a browser-style request to `GET /api/mobile/auth/start` with the public Autonova origin and a 64-character verifier hash returned `302`, set a short-lived HTTP-only OAuth state cookie, and targeted the Manus browser sign-in portal. The same request with an unrelated `serverOrigin` was rejected with HTTP `400`. This validates origin binding before an identity-provider session is involved.

## Account-specific sign-in step

The final authorization screen requires the user’s actual Manus account approval in a browser, which cannot be automated by this project without a real account session. After approval, the tested client receives the `autonova://auth` link, exchanges the one-time verifier-bound grant, encrypts the bearer token, and sends subsequent protected requests using the bearer header. The app shows an explicit success or error banner at each stage.
