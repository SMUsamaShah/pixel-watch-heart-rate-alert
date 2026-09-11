# Pixel Watch Heart Rate Alert

A small Wear OS app that lets you choose an upper heart-rate limit and alerts you when a reading reaches it. It uses Wear OS Health Services passive monitoring, so the watch’s health system supplies the readings instead of this app keeping its own sensor running continuously.

## Use it

1. Build `app-debug.apk` and install it on the watch.
2. Open **Heart Threshold**, enter a limit such as `120`, and tap **Start monitoring**.
3. Grant the heart-rate, background-health, and notification permissions.

The app alerts once per episode and re-arms after the reading drops below the limit. Passive data is batched by Wear OS, so a brief spike can be delayed or missed. This is a wellness tool, not a medical alarm.

## Build

Install Android Studio (or JDK 17 plus Android SDK 36), then run from the project root:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [BUILD.md](BUILD.md) for the exact tool versions, CI build, and clean-machine notes.
