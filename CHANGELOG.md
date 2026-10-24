# Changelog
## 0.1.0 (unbuilt)
- Project skeleton, capability model/engine, real PackageManager inventory, device info, 3 screens (en/ar).
- core:execution: AdbTransport boundary, typed ShellCommand allow-list, ExecutionEngine (timeout, confirmation, verification), parsers, redactor, tests (unexecuted). Added missing coroutines dependency to core:capability.
- core:adb: libadb-android transport (pair/connect/disconnect/clear), Keystore-backed key vault, connected only after a verified `id` query.
- App: ADB tab; app detail dialog; disable/enable with confirmation, post-state verification and rollback info.
