# Changelog
## 0.1.0 (unbuilt)
- Project skeleton, capability model/engine, real PackageManager inventory, device info, 3 screens (en/ar).
- core:execution: AdbTransport boundary, typed ShellCommand allow-list, ExecutionEngine (timeout, confirmation, verification), parsers, redactor, tests (unexecuted). Added missing coroutines dependency to core:capability.
- core:adb: libadb-android transport (pair/connect/disconnect/clear), Keystore-backed key vault, connected only after a verified `id` query.
- App: ADB tab; app detail dialog; disable/enable with confirmation, post-state verification and rollback info.
- Force stop, uninstall for user (typed confirmation), restore, grant/revoke runtime permissions, AppOps read/change, all verified by independent before/after probes with rollback info.
- Bulk plans with exclusion list, preview, per-step approval, stop-on-failure and undo.
- Terminal with risk prompt, Audit log (SQLite) with JSON/CSV preview+share, Diagnostics (device/storage/battery/network/logcat), Settings (theme, log detail, session saving, language entry, exclusions), Extensions screen explaining why it is unavailable.
- Fix: duplicate BouncyCastle classes (use jdk15to18 1.81 like libadb).
- Adaptive app icon (with themed monochrome layer), real launcher icons in the app list, bottom-navigation redesign (cards, status pills, animated transitions, brand light/dark theme, Arabic-safe typography).
- Automatic pairing: mDNS finds the ports, code typed in-app or via notification reply, automatic connect; advanced manual ports kept as fallback.
