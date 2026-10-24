# Decisions and assumptions
- Versions (AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk/targetSdk 35) were chosen from memory with no network access. They were NOT checked against current official docs; verify and bump before release. Google Play may require a newer target API.
- minSdk 26 as the initial value requested.
- QUERY_ALL_PACKAGES is used because a full inventory is the core feature; Play Store needs a declared-use justification (see docs/SECURITY.md).
- Module split: core:model (pure JVM), core:capability (Android library), app. Other modules (adb, execution, audit, ...) are added in later phases.
- No ADB library chosen yet (licence/maintenance review pending, Phase C).

## ADB library decision (Phase C)
- Chosen: libadb-android (MuntashirAkon), dependency `com.github.MuntashirAkon:libadb-android:3.1.1` via JitPack. The version number was read from a fork's README that mirrors upstream; confirm it is the latest published tag on JitPack.
- Licence: dual GPL-3.0-or-later OR Apache-2.0 (per its README). We elect Apache-2.0; the README notes an LGPL transitive dependency. Re-check before distribution.
- Caveat from the authors: the library has never had a security audit. It is isolated behind `AdbTransport`.
- Alternatives seen: Kadb (com.flyfishxu:kadb:2.1.1), and a BouncyCastle-based libadb fork. Not evaluated in depth.
- Companion deps: conscrypt-android 2.5.2 (TLS 1.3 for pairing), BouncyCastle 1.78.1 (certificate generation only). Versions come from memory/READMEs; unverified against Maven Central.
- Shell v1 service merges stderr into stdout and has no exit code, so an exit-code marker with a random nonce is appended (see ExitMarker).
- Import paths (`io.github.muntashirakon.adb.*`) and method names (`pair`, `connect`, `openStream`, `isConnected`, `disconnect`, `setApi`) were taken from the README and memory; the first CI build is the real check.
