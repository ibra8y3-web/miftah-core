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

## Later decisions
- BouncyCastle: libadb-android pulls `bcprov-jdk15to18:1.81` transitively, so core:adb uses the same artifact family/version (`bcprov/bcpkix-jdk15to18:1.81`) to avoid duplicate classes (found by the first CI run).
- Audit log uses plain SQLite (`SQLiteOpenHelper`) instead of Room: the brief allows "Room or suitable storage", and it avoids KSP version risk while nothing can be compiled locally. Migrate to Room later if wanted.
- Settings use DataStore Preferences. Language follows the system; per-app language is exposed through `localeConfig` (Android 13+).
- Extensions / IPC service for other apps is intentionally NOT built: it needs an SDK, per-client authorization and an independent client test, and the brief forbids fake controls. The tab explains this.
- Rollback is offered only when the state read BEFORE the operation proves it was a real change (see `Rollback`), and only right after the operation or inside a plan run, not from old audit rows.
- Bulk plans exclude uninstall on purpose. Uninstall for user requires typing the package name.
- Probes treat any unparseable output as "unknown", which shows the result as NOT verified rather than success.
