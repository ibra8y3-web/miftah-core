# Decisions and assumptions
- Versions (AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk/targetSdk 35) were chosen from memory with no network access. They were NOT checked against current official docs; verify and bump before release. Google Play may require a newer target API.
- minSdk 26 as the initial value requested.
- QUERY_ALL_PACKAGES is used because a full inventory is the core feature; Play Store needs a declared-use justification (see docs/SECURITY.md).
- Module split: core:model (pure JVM), core:capability (Android library), app. Other modules (adb, execution, audit, ...) are added in later phases.
- No ADB library chosen yet (licence/maintenance review pending, Phase C).
