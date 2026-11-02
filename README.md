# Remote Work Tracker — Android

Android app (Jetpack Compose, Material 3) for tracking remote work hours and quota compliance, built for German companies with ArbZG (Working Hours Act) rules, the Bavarian holiday calendar and SAP time statement ("Zeitnachweis") PDF import.

The business logic lives in the Kotlin Multiplatform module `shared/`, the user interface in `androidApp/`. This repository contains the **Android part only**; the iOS app (SwiftUI) lives in the main project `remote-quota-guard` and uses the same `shared/` module.

## Build the app

| Needs | Version |
|---|---|
| JDK | 17 (Temurin or similar) |
| Android SDK | platform 34, build-tools 34 |
| Gradle | wrapper included (`gradlew`, Gradle 8.5) — do not install Gradle separately |

Tell Gradle where the Android SDK is: set `ANDROID_HOME`, or create `local.properties` in the project root (git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
# debug APK (debug-signed, for testing)
./gradlew :androidApp:assembleDebug
#   -> androidApp/build/outputs/apk/debug/androidApp-debug.apk

# install on a connected device or running emulator
./gradlew :androidApp:installDebug

# release build (unsigned until you add a signing config to androidApp/build.gradle.kts)
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:bundleRelease        # Android App Bundle for Google Play
```

On Windows use `gradlew.bat` instead of `./gradlew`. Linux, macOS and Windows all work.

**Cloud build without any local setup:** the GitHub Actions workflow `Android Build` runs the privacy check, the unit tests and `assembleDebug` on every push and offers the APK as artifact `android-debug-apk` (*Actions → Android Build → latest run*).

## Tests

```bash
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest      # JVM, no emulator needed
./gradlew :androidApp:connectedDebugAndroidTest                        # needs an emulator or device
```

The instrumented test `RealStatementsInstrumentedTest` needs real statements that are *not* part of the repository and skips itself when they are missing.

## Features

- Daily work hours (date, start/end, break, remote hours), edit/delete, overlap detection
- Dashboard: month and quarter quota, color-coded progress bar, multi-month selector
- History of closed months, sorted, **red background** when the remote limit is exceeded, **final report** grouped by quarter with a rating
- ArbZG break rules for manual entries, weekend blocking, Bavarian holidays 2025–2030
- SAP time statement PDF import (imported entries carry no break warning)
- CSV export, PDF monthly report, JSON backup and restore; dark mode; German/English

## Project structure

```
shared/      Kotlin Multiplatform module: models, calculations, repository, final report (+ tests)
androidApp/  Android app: MainActivity, PDF parser, report generator, export (+ unit and instrumented tests)
docs/        user manual and technical documentation (DE/EN), test protocol, reviewed test screenshots
tools/       check_privacy.py (GDPR guard)
```

## Privacy (GDPR)

The app handles working times of a real person. This repository must never contain time statements (PDF), screenshots with real data, backups or personal addresses. `python tools/check_privacy.py` checks every file Git would publish; run it before every push. Images are only allowed in `docs/test-screenshots/` (reviewed, fictional values only) and in the app icon folders.

## License

MIT, see [LICENSE](LICENSE).
