# OllieTV — Stage 0 QA checklist

## Automated CI gates
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` passes and publishes `OllieTV-debug-apk`.
- [ ] `:app:connectedDebugAndroidTest` passes on API 35 emulator.
- [ ] Test report artifact is retained for failed and successful unit-test runs.

## Data and persistence
- [ ] Import a valid M3U URL and local M3U file.
- [ ] Import an Xtream account with live, movie, and series catalogues.
- [ ] Refresh a remote playlist; verify favourites remain attached to matching streams.
- [ ] Force a failed/empty refresh; verify the previous stream list remains intact.
- [ ] Restart the app; verify playlists, favourites, recordings, and programme data remain available.
- [ ] Upgrade a database created by the previous app version; verify migration preserves existing rows.

## EPG
- [ ] Import XMLTV with explicit UTC offsets and offset-less UTC timestamps.
- [ ] Check invalid dates, overnight programmes, overlapping entries, and daylight-saving transitions.
- [ ] Verify channel mapping and the 24-hour timeline against provider data.

## Playback and network
- [ ] Test valid HLS, direct HTTP streams, unavailable URLs, HTTP 401/403/404, and connection loss.
- [ ] Verify player errors are visible and do not crash the application.
- [ ] Verify recovery behavior after Wi-Fi disconnect/reconnect.
- [ ] Verify audio/subtitle track switching and aspect-ratio controls on supported media.

## Recording
- [ ] Test immediate direct HTTP recording and clear MPEG-TS HLS recording.
- [ ] Verify encrypted HLS and fMP4/CMAF are rejected with a clear explanation.
- [ ] Test stop, cancellation, scheduled start, network loss, and storage exhaustion.
- [ ] Verify recording URLs are encrypted at rest and recordings are playable after completion.

## Device acceptance (manual)
- [ ] Android TV: DPAD focus, back navigation, channel zapping, EPG scrolling, and long playlist performance.
- [ ] Tablet: current landscape layout, split-screen/resizing where supported, touch targets, and playback lifecycle. Portrait adaptation is a Stage 1 deliverable because the current manifest still requests landscape.
- [ ] Phone: current landscape playback, touch navigation, background/foreground transitions, and playback lifecycle. Portrait adaptation is a Stage 1 deliverable.
- [ ] Verify notification permission and foreground recording behavior on Android 13+.

A Stage 0 release is not considered fully accepted until automated gates pass and the manual device checklist has been completed on representative devices.
