# Stage 3 — EPG, guide navigation and programme reminders

## Scope

- Room database v3 → v4 migration adds XMLTV source, channel metadata, manual mapping and reminder tables. Existing playlist, stream, programme, recording, favourite, history and lock rows are retained.
- XMLTV parser reads plain XML and GZIP streams, channel display names/icons, programme metadata and offset-aware timestamps. Offset-less XMLTV timestamps retain the existing UTC interpretation.
- EPG imports parse and validate the complete feed before replacing source-scoped rows. HTTP, malformed and empty-feed failures update source status but do not replace the last successful programme data.
- Multiple EPG sources can be added, enabled, refreshed and removed from Playlist management. The periodic refresh worker processes enabled feeds.
- Exact `tvg-id` matching is automatic. Name-based matches are suggestions only. Manual mappings are persistent and take precedence over automatic matches.
- The main guide supports a seven-day range, local calendar day boundaries, channel/programme search, current-programme progress and manual EPG mapping.
- The in-player guide browses channels without changing playback; the user must explicitly choose WATCH. It reuses the existing ExoPlayer instance and the selected day from the guide.
- Programme reminders are persisted and scheduled with WorkManager for 5, 10 or 15 minutes before start. They can be cancelled from the guide. Android 13+ notification permission is requested at the point of scheduling.

## Time and background behavior

Guide day ranges use local calendar arithmetic. Europe/Warsaw transition days therefore span 23 or 25 hours as appropriate. Reminder work is scheduled from the programme's UTC instant. WorkManager remains subject to Android battery optimizations and manufacturer background limits; delivery can be delayed.

## CI

GitHub Actions is the verification gate. It runs `:app:testDebugUnitTest` and `:app:assembleDebug`, then uploads the unit-test report and debug APK. Termux installation does not run Gradle or local compilation.

## Acceptance still required

- CI green on the user's branch.
- Manual QA on Android TV remote, tablet and phone, including EPG browsing/playback continuity, large XMLTV feed, feed failure recovery, manual mappings, reminder permission and cancellation, and Europe/Warsaw DST boundaries.
