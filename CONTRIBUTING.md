# Contributing to Ruddarr for Android

Thanks for helping improve the Android and Wear OS clients. Keep each change focused on a user-visible bug or feature and preserve the local-first design: no account, cloud backend, Firebase, or remote credential storage.

## Before opening a pull request

1. Create a focused branch and keep unrelated formatting or refactors out of the change.
2. Build the affected app modules:

   ```bash
   ./gradlew :app:assembleDebug :wearApp:assembleDebug
   ```

3. Run the relevant checks:

   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ./gradlew :wearApp:testDebugUnitTest :wearApp:lintDebug
   ```

   `connectedDebugAndroidTest` requires a running phone emulator or device. Check Wear OS changes on both a small round and a larger round emulator when they affect layout.

4. Update `CHANGELOG.md` under **Unreleased** for user-visible changes. Keep `TestFlight/WhatToTest.en-US.txt` in sync with that entry.
5. Describe the behavior, verification, and any UI impact in the pull request. Include screenshots only when they do not reveal server URLs, API keys, or private media metadata.

## Code and UI expectations

- Write idiomatic Kotlin and Jetpack Compose, matching the surrounding module rather than introducing a new architectural layer.
- Keep phone UI in `app` and watch UI in `wearApp`; the watch must remain independently configurable and make its own local requests.
- Use Material 3 Expressive components and motion already provided by the project. Preserve clear contrast, readable text, touch targets, and round-screen safe padding.
- Add or update a focused test whenever parsing, mapping, or user-visible state changes.
- Use concise imperative commit subjects, such as `Fix Sonarr episode counts`.

## Credentials and local data

Never commit server addresses, API keys, emulator data, release keystores, or screenshots of Settings. For a private Android debug setup, copy `app/src/debug/assets/seed-instances.example.json` to `app/src/debug/assets/seed-instances.json`; Git ignores the populated file. Configure Wear OS instances directly on the test watch.
