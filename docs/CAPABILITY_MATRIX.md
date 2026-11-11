# Capability matrix
"Implemented" = a real code path exists. "Verified on a device" = nobody has run it on hardware yet unless noted in docs/TESTING.md.

| Capability | Level | Implemented | How the result is verified |
|---|---|---|---|
| List packages (incl. removed for user) | Standard (QUERY_ALL_PACKAGES) | yes | n/a (read) |
| Open app info / system uninstall dialog (user apps) | Standard | yes | system UI |
| Read requested permissions + protection class | Standard | yes | n/a (read) |
| Privacy doctor: accessibility, notification listeners, device admins, overlay, usage access, unknown-app install, sensitive grants | Standard | yes | n/a (read); refused checks are counted |
| Startup manager: list boot receivers | Standard | yes | PackageManager |
| Disable / enable one boot receiver | ADB | yes | `getComponentEnabledSetting` before/after |
| ADB wireless pairing + connect (API 30+), mDNS port discovery, notification reply | Standard | yes | real `id` query must parse |
| Disable / enable package for current user | ADB | yes | `pm list packages -d` before/after |
| Force stop | ADB | yes | `pidof` before/after (apps may restart: then NOT verified) |
| Uninstall for user / restore | ADB | yes | `pm list packages --user N` before/after |
| Grant / revoke runtime permission | ADB | yes | PackageManager.checkPermission before/after |
| Read / change AppOps (5 known ops) | ADB | yes | `cmd appops get` before/after |
| Last sensor use (camera, mic, location) | ADB | yes | raw text from `cmd appops get` |
| Bulk plans, per-step approval, stop on failure, undo | ADB | yes | each step verified |
| Display density (with 15 s auto-revert) and animation scales | ADB | yes | `wm density` / `settings get` before/after |
| Ad blocker (local VPN, DNS filter, NXDOMAIN) | none (VPN consent) | yes | system VPN transport check; blocked counter |
| Per-app firewall (local VPN, drop) | none (VPN consent) | yes | system VPN transport check |
| Terminal, Logcat, Diagnostics, Audit (SQLite, JSON/CSV) | ADB / Standard | yes | see earlier notes |
| Extensions / IPC for other apps | n/a | NO | needs SDK + external client tests |

Limits worth knowing: only one VPN can run at a time (blocker OR firewall). DNS filtering cannot identify the asking app and cannot remove ads served from the content's own domain. Fixed Private DNS bypasses the blocker (the app warns). Not offered by design: signature permissions, deleting system APKs, bulk uninstall.
