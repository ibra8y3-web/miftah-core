# Building Miftah Core

The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, JDK 17, and Android SDK Platform 35.

## Local build

Install JDK 17 and Android SDK Platform 35, then run:

```sh
./gradlew :app:assembleDebug
```

Run JVM unit tests with:

```sh
./gradlew :core:model:test :core:execution:test :core:net:test
```

The `Android build` GitHub Actions workflow runs on pushes to `main`, pull requests, or manual dispatch, and uploads the debug APK when the build succeeds.

## Known source gaps

The initial repository snapshot omits implementation sources for `core:adb` and `core:audit`; it also lacks execution/model types referenced from application and execution code. Gradle setup alone cannot make the APK compile until those source files are restored. CI intentionally attempts the full build so these compile errors remain visible rather than producing a misleading artifact.
