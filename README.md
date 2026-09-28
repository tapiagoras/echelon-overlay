# Echelon Overlay

Kotlin Android prototype for an Echelon EX-5s with a 22-inch console. The intended
app reads cadence, power, speed, and resistance over BLE, runs structured workouts,
and safely controls resistance while a minimal overlay remains visible over
Netflix, Prime Video, or YouTube.

## Current scope

This repository contains a Gradle module scaffold, a static Android launch screen,
and architecture/development documentation. **BLE, resistance control, workout
execution, foreground services, and the overlay are not implemented.** No bike
commands are sent and no Bluetooth or overlay permissions are requested.
No code has been copied from qDomyos-Zwift.

The bike console's Android version, installation permissions, BLE availability,
protocol, and behavior alongside streaming apps still require physical-device
validation. API 26 is a provisional minimum, not a claim of console compatibility.

## Layout

```text
app/                 Android entry point; future session/service composition
core/bike/           Pure Kotlin bike contracts and telemetry boundary
core/workout/        Pure Kotlin structured-workout engine boundary
core/safety/         Pure Kotlin automatic-control policy boundary
feature/overlay/     Android overlay presentation boundary
docs/architecture.md Responsibilities, lifecycle, safety, and test plan
AGENTS.md            Development rules
```

See [architecture](docs/architecture.md) and [development rules](AGENTS.md).
Module READMEs describe future responsibilities, not working features.

## Development

Use JDK 17, Android SDK platform 35 and Build Tools 35.0.0, and Gradle 8.13.
Plugins are pinned to Android Gradle Plugin 8.13.2 and Kotlin 2.2.21. The
[AGP compatibility documentation](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
describes its Gradle/JDK requirements.

Open this root directory in Android Studio. Set `sdk.dir` in an untracked
`local.properties` or configure `ANDROID_HOME` for your local SDK installation.
Use the checked-in Gradle wrapper (PowerShell commands below; use `./gradlew` on
macOS/Linux):

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :core:bike:test :core:workout:test :core:safety:test
```

The core test tasks initially have no test sources; a successful empty task is
not evidence of safety. Add JVM tests with the first domain behavior. Kotlin/JUnit
test dependencies are preconfigured. The initial dependency resolution requires
network access. Build output is under `app/build/outputs/apk/debug/`.

## Next tasks

1. Record the real console's Android/API level, sideloading and permission support,
   BLE access, competing bike connections, and foreground-service/overlay behavior
   alongside the three streaming apps. Capture protocol evidence without writes.
2. Define pure Kotlin telemetry, workout, and safety contracts; implement a fake
   bike and fake clock, then test workout transitions and default-deny safety
   behavior before adding any physical resistance control.
3. Build a simulated session through a foreground service and minimal overlay,
   including notification stop, permission denial, backgrounding, and process-loss
   tests. Add real BLE in a separately reviewed phase after these foundations.
