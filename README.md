# Vidcraft Pro

A Kotlin/Android Gradle project for Vidcraft Pro — a video editing/processing Android application.

## Project Overview

- Multimodule Android app using Gradle Kotlin DSL.
- App module located at `app/`.

## Requirements

- JDK 11 or newer
- Android SDK (recommended via Android Studio)
- Gradle wrapper (included)

## Quick Setup

1. Install Android Studio and the Android SDK.
2. Ensure `local.properties` contains `sdk.dir` pointing to your Android SDK, or configure via Android Studio.
3. Use the included Gradle wrapper to build and run.

## Build & Run

- Build debug APK:

```
./gradlew :app:assembleDebug
```

- Install on a connected device or emulator:

```
./gradlew :app:installDebug
```

- Build release APK (signing config required):

```
./gradlew :app:assembleRelease
```

Open the project in Android Studio to run on an emulator, debug, and use the IDE tooling.

## Tests & Checks

- Run unit tests:

```
./gradlew test
```

- Run instrumentation tests on a connected device/emulator:

```
./gradlew connectedAndroidTest
```

- Run lint checks:

```
./gradlew lint
```

## Project Structure

- `app/` — main Android application module.
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts` — build configuration and Gradle Kotlin DSL files.

## Signing

Release builds require signing configuration. Add signing configs to `app/build.gradle.kts` or configure via environment variables and `local.properties`. Keep keystore passwords out of version control.

## Troubleshooting

- If Gradle fails to find the SDK: verify `sdk.dir` in `local.properties`.
- For dependency or Kotlin issues: sync and invalidate caches in Android Studio.

## Contributing

- Fork the repo, create a feature branch, and open a pull request with a clear description.

## License

Specify your license here (e.g., MIT). If you need, I can add a `LICENSE` file.

---

If you want, I can also:

- add a `CONTRIBUTING.md` and `LICENSE` file
- add CI build steps (GitHub Actions)
- customize README with screenshots and architecture diagrams
