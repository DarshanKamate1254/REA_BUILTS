# DarshanCarrom

An original, local pass-and-play carrom game for Android, built with Kotlin and Jetpack Compose.

## Play

- Drag the striker backward and release to shoot.
- Pocketed coins score 1 point; the red queen scores 3.
- A player keeps the turn after pocketing a coin. Otherwise, the turn passes.
- The first player to reach 10 points wins.

## Build

Open this directory in Android Studio, or build the debug APK with Gradle 8.9, JDK 17, and Android SDK platform 35:

```shell
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow builds this APK on pushes, pull requests, and manual runs, then uploads it as the `DarshanCarrom-debug-apk` artifact.
