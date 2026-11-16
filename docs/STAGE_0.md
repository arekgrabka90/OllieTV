# Stage 0 — Stabilization and quality foundation

## Baseline
- Repository: `arekgrabka90/OllieTV`
- Source branch: `main`
- Source commit: `192e1181912511f34253825b20a2840ddde2b00c`
- App baseline: `0.4.0` / versionCode `4`
- Target stabilization version: `0.4.1` / versionCode `5`

## Findings from the first GitHub build
The first SDK setup failure was fixed in commit `192e118`. The next build passed Android SDK setup and dependency resolution but exposed Kotlin compilation errors in:
- Compose native key-code access;
- Media3 preferred audio-language selection;
- the unused player screen's stale `Channel` model reference;
- the recording status DAO call missing its `startedAt` argument.

These source issues are corrected in this update.

## Included in this update
1. Fixed the Kotlin compilation errors listed above.
2. Added JVM test infrastructure (JUnit, Robolectric, MockWebServer).
3. Added tests for M3U parsing (including 10,000 entries), XMLTV parsing/time-zone conversion, Xtream response parsing and HTTP errors, HLS segment recording/master-playlist selection/unsupported formats, Room persistence, and database migration 1 → 2.
4. Added Android instrumented tests for Android Keystore credential round-trips, legacy plaintext compatibility, and ciphertext tamper detection.
5. Made playlist import, refresh replacement, and deletion atomic Room operations.
6. Encrypted immediate-recording source URLs before database persistence.
7. Fixed HLS master-playlist parsing so variant playlist URLs are not mistakenly written as media segments.
8. Tightened XMLTV timestamp parsing to reject malformed and impossible dates rather than accepting lenient rollover dates.
9. Updated CI to run unit tests, build the debug APK, and run Android Keystore tests on an API 35 emulator.
10. Added a manual QA checklist for Android TV, tablets, and phones.

## Local validation performed while preparing this archive
- Kotlin compilation of the standalone M3U parser: passed.
- M3U parser smoke checks, including 10,000 entries: passed.
- Kotlin compilation of the standalone HLS recorder: passed (JDK deprecation warnings only).
- HLS master → variant → MPEG-TS segment smoke test against a local HTTP server: passed.
- GitHub Actions workflow YAML syntax parse: passed.

The complete Android Gradle build and Android/Robolectric test suites have **not yet been run against this updated archive**. This environment has no Android SDK or Gradle installation and cannot access the Gradle distribution host. The updated GitHub Actions workflow is intended to run those checks after this archive is applied and pushed.

## Acceptance status
Stage 0 implementation is packaged, but formal acceptance remains **pending** until:
- GitHub Actions unit tests and debug APK build pass;
- API 35 Android Keystore instrumented tests pass;
- the manual device checklist in `docs/QA_CHECKLIST.md` is completed on representative hardware.

A successful emulator build does not prove Android TV, tablet, or phone production readiness.
