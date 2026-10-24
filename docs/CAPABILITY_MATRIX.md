# Capability matrix
"Implemented" = a real code path exists. "Verified on a device" = nobody has run it on hardware yet (all rows: NO).

| Capability | Level | Implemented | How the result is verified |
|---|---|---|---|
| List packages (incl. removed for user) | Standard (QUERY_ALL_PACKAGES) | yes | n/a (read) |
| Open app info / system uninstall dialog (user apps) | Standard | yes | system UI |
| Read requested permissions + protection class | Standard | yes | n/a (read) |
| ADB wireless pairing + connect (API 30+) | Standard | yes | real `id` query must parse |
| Disable / enable for current user | ADB | yes | `pm list packages -d` before/after |
| Force stop | ADB | yes | `pidof` before/after (apps may restart: then NOT verified) |
| Uninstall for current user / restore (`install-existing`) | ADB | yes | `pm list packages --user N` before/after |
| Grant / revoke runtime permission | ADB | yes | PackageManager.checkPermission before/after |
| Read / change AppOps (5 known ops) | ADB | yes | `cmd appops get` before/after |
| Bulk plans (disable / enable / force stop), per-step approval, stop on failure, undo | ADB | yes | each step verified as above |
| Terminal (interactive, risk prompt) | ADB | yes | exit-code marker only |
| Logcat (last 200 lines) | ADB | yes | n/a (read) |
| Diagnostics (device, storage, battery, network) + export | Standard | yes | platform APIs |
| Audit log (SQLite), JSON/CSV export | n/a | yes | n/a |
| Extensions / IPC for other apps | n/a | NO | needs SDK + external client tests |

Not offered by design: granting signature/privileged permissions, deleting APKs from the system partition, bulk uninstall.
