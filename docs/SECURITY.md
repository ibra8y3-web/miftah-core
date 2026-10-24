# Security and privacy
- Offline: no INTERNET permission, no analytics, no uploads.
- Only permission: QUERY_ALL_PACKAGES (read-only inventory). Declare its use if publishing on Google Play.
- No secrets, keys or pairing codes exist in this build. allowBackup=false.
- Report vulnerabilities by private message to the project owner.

## Update (ADB)
- INTERNET permission is declared only for the loopback connection to this device's adbd; the app rejects any host other than 127.0.0.1/localhost.
- ADB identity: RSA-2048 key generated on device; stored encrypted with an AES-GCM key in Android Keystore. "Clear pairing data" deletes it. Pairing codes are never stored or logged.
- libadb-android is unaudited (its authors say so). Keep Wireless debugging off when not in use.
