# Ruddarr for Android developer guide

## Toolchain

Use JDK 17 and an Android SDK containing API 37. The phone app has a minimum API level of 26; the standalone Wear OS app has a minimum API level of 30. Android Studio can import the repository directly, or use the Gradle wrapper from a shell.

## Build and run

```bash
./gradlew :app:assembleDebug
./gradlew :wearApp:assembleDebug
```

Install the generated debug APK for the module you want to run. The phone and watch use separate encrypted instance stores, so configuring one does not configure the other.

### Local Android configuration

Open **Settings** and add local Radarr or Sonarr instances. For repeatable private debug setup, copy the ignored seed template:

```bash
cp app/src/debug/assets/seed-instances.example.json app/src/debug/assets/seed-instances.json
```

Fill the copy with local values, run the debug app, and select **Load debug instance seeds** in Settings. Never commit the populated seed file.

### Local Wear OS configuration

Launch the Wear OS app on a round emulator or physical watch, open **Connections**, and configure Radarr and Sonarr directly. The watch connects to the configured local endpoints itself; it does not receive credentials from the phone.

## Verification

Run the checks appropriate to the module you changed:

```bash
./gradlew :app:connectedDebugAndroidTest
./gradlew :wearApp:testDebugUnitTest :wearApp:lintDebug
```

For visual changes, verify the phone on a compact device and Wear OS on both 384 px and 454 px round displays. Exercise the library, loading, empty, and error states; confirm long titles and metric labels stay visible inside the round-screen safe area.

## Project map

- `app/src/main/java/uk/bleier/ruddarr/MainActivity.kt` contains the Android Compose navigation and screens.
- `app/src/main/java/uk/bleier/ruddarr/RuddarrViewModel.kt` coordinates screen state and Arr actions.
- `app/src/main/java/uk/bleier/ruddarr/data` contains direct Arr requests and encrypted local instance storage.
- `app/src/main/java/uk/bleier/ruddarr/domain` maps Arr responses to displayable models.
- `wearApp/src/main/java/uk/bleier/ruddarr/wear` contains the standalone Wear OS UI, storage, and direct Arr status client.

## Release flow

The Android-only GitHub Actions workflow builds the phone and Wear OS APKs. It requires the Android signing secrets documented in the README. Pushing an `android-v*` tag publishes the signed artifacts; do not change signing configuration or versioning as part of ordinary feature work.
