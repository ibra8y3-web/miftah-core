# Testing status (honest report)
Authoring environment had JDK 21 only: no Gradle, Kotlin compiler, Android SDK, device or emulator.
| Item | Status |
|---|---|
| assemble / unit tests / lint | NOT RUN locally; run by GitHub Actions (check the latest run) |
| Unit tests written: core:model, core:capability, core:execution (commands, engine, parsers, redactor, exit marker, state probe) | written; results come from CI |
| core:adb (libadb-android transport, KeyVault) | implemented, compiled only by CI, NO unit tests, NEVER run on a device |
| Real pairing/connect on a device, real disable/enable | NOT TESTED |
| Device/emulator instrumented tests, RTL screenshots | not written |
Update this file with real command output after each build.
