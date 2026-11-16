# Ollie TV 0.4.1 — Stabilization

## Fixed
- Resolved Kotlin compile errors in Compose remote-key handling, Media3 audio-language selection, player screen model typing, and recording status updates.
- Tightened XMLTV timestamp parsing to reject malformed dates and invalid calendar values rather than accepting lenient rollover dates.

## Reliability and security
- Wrapped playlist import, refresh replacement, and deletion operations in Room transactions.
- Encrypted immediate-recording source URLs before persisting them in the recordings database.

## Quality assurance
- Added unit tests for M3U parsing, XMLTV parsing/time-zone conversion, Xtream response parsing and HTTP failures, HLS segment recording and unsupported encrypted/fMP4 streams, Room persistence, and database migration 1 → 2.
- Added Android instrumented tests for Android Keystore credential round-trips, legacy plaintext compatibility, and ciphertext tamper detection.
- Updated GitHub Actions to run unit tests, build the debug APK, and run Android Keystore tests on an API 35 emulator.

## Scope note
This is a stabilization and test-foundation update. It does not claim physical-device acceptance or production readiness.
