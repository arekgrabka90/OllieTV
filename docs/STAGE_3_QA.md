# Stage 3 QA checklist

## CI
- [ ] GitHub Actions unit tests pass.
- [ ] GitHub Actions debug APK build succeeds.
- [ ] APK artifact is available.

## Database and import
- [ ] v1 → v4 migration retains existing data.
- [ ] v2 → v4 and v3 → v4 migration paths pass.
- [ ] Multiple XMLTV sources can be added, enabled, refreshed and removed.
- [ ] GZIP and plain XMLTV feeds parse channel metadata and programmes.
- [ ] Empty, malformed and failed feeds do not erase previously imported valid data.
- [ ] Source status and last error/update information are accurate.

## Matching and guide
- [ ] Exact tvg-id mapping is automatic.
- [ ] Name matches are suggestions, not silently accepted.
- [ ] Manual mapping persists and overrides automatic matching.
- [ ] Search covers channel names, programme titles and descriptions.
- [ ] Seven-day navigation is bounded to today through day six.
- [ ] Europe/Warsaw spring and autumn transition days have correct 23/25-hour ranges.
- [ ] Current programme highlight/progress is correct.

## Player and reminders
- [ ] EPG browsing does not zap until WATCH is confirmed.
- [ ] EPG/HUD overlays do not recreate or release ExoPlayer.
- [ ] D-pad focus, Enter, Back, channel keys and media play/pause work on Android TV.
- [ ] Reminder choices are 5, 10 and 15 minutes.
- [ ] Notification permission is requested on Android 13+.
- [ ] Reminder persists across process restart and can be cancelled.
- [ ] Battery optimization caveat is shown to the user.

## Form factors
- [ ] Android TV / remote.
- [ ] Tablet portrait and landscape.
- [ ] Phone portrait and landscape.
- [ ] Large playlist and large XMLTV feed performance.
