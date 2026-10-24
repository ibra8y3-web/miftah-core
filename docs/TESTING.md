# Testing status (honest report)
The authoring environment had JDK 21 only: no Gradle, Kotlin compiler, Android SDK, device or emulator.

| Item | Status |
|---|---|
| assemble / unit tests / lint | run only by GitHub Actions; check the latest run |
| JVM unit tests written: core:model (query, protection, audit export JSON/CSV, diagnostics report), core:capability (engine), core:execution (commands, rollback rules, parsers, risk heuristic, exit marker, probes, engine, plan runner, terminal runner) | written; results come from CI |
| core:adb (libadb transport, KeyVault), core:audit (SQLite), PermissionReader, DiagnosticsReader, all Compose screens | NO tests; compiled only by CI |
| Real pairing/connect, disable/enable, force-stop, uninstall/restore, grant/revoke, AppOps, plans, terminal on a real device | NOT TESTED |
| Instrumented tests, RTL screenshots | not written |

Before trusting any destructive action, test on a spare device with a throw-away app. Update this file with real output after each build.
