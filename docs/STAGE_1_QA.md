# Stage 1 — QA acceptance checklist

## CI gates
- [ ] `:app:testDebugUnitTest` passes, including `OllieWindowClassTest`.
- [ ] `:app:assembleDebug` passes and publishes the debug APK.
- [ ] `:app:connectedDebugAndroidTest` passes on API 35.

## Phone
- [ ] Launch in portrait; bottom navigation is visible and all seven destinations can be reached by horizontal scrolling.
- [ ] Rotate portrait ↔ landscape; selected tab, search text, category, selected series, and open dialog state remain coherent.
- [ ] Import M3U by URL and file; import Xtream; import EPG.
- [ ] Start playback, rotate while playing, and verify the same stream continues without restarting from zero.
- [ ] Verify Back returns to the previously selected tab and the player releases when playback is exited.
- [ ] Verify bottom navigation does not cover the last list item or progress/message feedback.

## Tablet
- [ ] At widths from 600 dp to 839 dp, icon rail is shown and content uses the remaining width.
- [ ] At widths of 840 dp and above, full labelled navigation is shown.
- [ ] Resize split-screen/window width across 600 dp and 840 dp boundaries; navigation changes without crash.
- [ ] Test portrait and landscape; check dialogs, guide, recordings, and playlist manager for clipping.
- [ ] Rotate while playing; verify playback position and stream continue.

## Android TV
- [ ] Full labelled navigation rail is visible.
- [ ] D-pad moves focus through navigation and selects every destination.
- [ ] Focus feedback is clearly visible on selected navigation item.
- [ ] Back exits player to the prior screen; channel up/down and media keys still work.
- [ ] Verify focus remains usable after rotating/resizing is not applicable to the TV form factor.

## Regression
- [ ] M3U and Xtream import/refresh.
- [ ] XMLTV/EPG day navigation and programme selection.
- [ ] Favourites, series/episodes, catch-up.
- [ ] Immediate and scheduled recordings, stop/cancel, playback of completed recording.
- [ ] Restart app and verify Room data is unchanged.
- [ ] Confirm no database migration was introduced by Stage 1.
