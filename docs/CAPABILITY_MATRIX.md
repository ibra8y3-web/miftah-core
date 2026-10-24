# Capability matrix
| Capability | Level needed | Implemented | Verified on a device |
|---|---|---|---|
| List packages | Standard (QUERY_ALL_PACKAGES) | yes | no |
| Open app info | Standard | yes | no |
| ADB wireless pairing + connect (API 30+) | Standard | yes (libadb-android) | no |
| Disable / enable package for current user (verified via `pm list packages -d`) | ADB | yes | no |
| Force stop, uninstall for user, grant/revoke, AppOps, terminal, logcat | ADB | no | no |
