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

## Added in the UI/pairing update (all UNTESTED, compiled only by CI)
mDNS discovery (AdbDiscovery), PairingCoordinator + notification reply (PairingReceiver), auto-connect, adaptive launcher icon, real app icons, bottom-navigation redesign, light/dark brand theme. Real behaviour depends on the device (Wi-Fi, Wireless debugging on, notification permission on Android 13+). Android never exposes the pairing code to apps, so the user always types the 6 digits.

## Added in the "tools" update (all UNTESTED on hardware; compiled and unit-tested only by CI)
- JVM unit tests written: core:net (IPv4/UDP codec incl. a known checksum vector, DNS query parsing, NXDOMAIN reply, blocklist matching, list parser), core:execution (component/density/animation commands, rollback rules, density/appops parsers, probe verification), core:model (privacy score, startup grouping).
- No tests for: MiftahVpnService (real tunnel), PrivacyScanner, StartupScanner, display revert countdown, all new Compose screens. Real behaviour depends on the device and ROM.
- Honest unknowns: some OEM ROMs restrict AppOps/accessibility reads; `cmd appops` output format can differ by Android version (reads that do not parse show as unreadable/unverified, never as success).
