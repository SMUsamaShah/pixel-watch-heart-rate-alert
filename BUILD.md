# Build and reproduce

This is a single-module Wear OS Android application. The source is Kotlin and the app reads `DataType.HEART_RATE_BPM` through `PassiveListenerService` in the AndroidX Health Services client.

## Known-good toolchain

- JDK 17
- Gradle 8.10.2 (pinned by the checked-in launcher)
- Android Gradle Plugin 8.8.2
- Kotlin Gradle plugin 2.1.20
- Android SDK platform 36
- Android build-tools 35.0.0
- `androidx.health:health-services-client:1.1.0-rc02`

Android Studio can install the SDK components and run the project directly. The checked-in `gradlew` launcher uses an installed Gradle command only when it is exactly 8.10.2; otherwise it downloads that pinned version into a user cache. A command-line machine also needs `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) set to its SDK directory.

## Build a debug APK

From the repository root:

```bash
chmod +x gradlew
./gradlew --no-daemon assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The APK has package name `com.usamashah.heartthreshold`, version `1.1`, and is signed with the normal debug key. It is suitable for installing on a development watch, not for Play Store release.

## Android Studio

Open the repository as a project, allow Gradle sync, install SDK 36 if prompted, and run the `app` configuration on a paired Wear OS watch or emulator. No `local.properties`, keystore, or credentials belong in the repository; Android Studio creates machine-specific SDK settings locally.

## GitHub Actions

`.github/workflows/build-apk.yml` runs the same debug build on pushes and pull requests. The generated APK is uploaded as the `heart-threshold-debug-apk` workflow artifact.

## Important implementation details

- Registration is passive and long-running; it does not use a persistent foreground service or a direct high-frequency `SensorManager` listener.
- Passive registrations do not survive a watch reboot. `StartupReceiver` schedules `RestorePassiveMonitoringWorker` when monitoring was enabled before reboot.
- On Wear OS 4 and earlier, the app requests legacy `BODY_SENSORS` permissions. On newer Wear OS versions it requests Health Services permissions.
- The notification permission is requested before monitoring is enabled on Android 13 and later.
- Each `SampleDataPoint` carries a boot-relative measurement duration. The service converts it with Health Services’ `getTimeInstant()` API and displays the resulting measurement time in the watch’s local timezone; it does not use the callback/delivery time.
- `PREF_WAS_ABOVE` prevents repeated alerts for the same episode. It resets after monitoring is stopped or when a later reading is at least 5 BPM below the configured limit.

The official background-monitoring guidance is available at <https://developer.android.com/health-and-fitness/health-services/monitor-background>.
