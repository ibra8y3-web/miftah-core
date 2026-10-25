# Architecture
Modules (dependency direction: app -> core:* -> core:model):
- core:model (pure JVM): capability/operation/audit/diagnostic types, app query, export formats.
- core:capability (Android): CapabilityEngine, PackageInventory, PermissionReader, DiagnosticsReader.
- core:execution (pure JVM): typed ShellCommand allow-list, ExecutionEngine (timeout/confirmation/verification), probes, Rollback, PlanRunner, TerminalRunner, parsers, redaction.
- core:adb (Android): LibAdbTransport (only class touching libadb-android), KeyVault (Keystore-wrapped key), AdbDiscovery (mDNS via NsdManager).
- core:audit (Android): SQLite operation log.
- app: Compose UI (theme, components, screens), MainViewModel, PairingCoordinator + PairingReceiver (notification reply), MiftahApplication (shared ADB session).
UI reads only ViewModel state; the ADB session is shared process-wide so the notification reply and the UI see the same connection.
