# Build and run
1. Install JDK 17, Android SDK (platform 35), and Gradle 8.9 (the wrapper jar is not included: run `gradle wrapper --gradle-version 8.9` once).
2. `./gradlew testDebugUnitTest :core:model:test assembleDebug lint`
3. APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed; release signing keys are never stored in the repo).
CI: `.github/workflows/build.yml` runs the same commands with Gradle 8.9.
