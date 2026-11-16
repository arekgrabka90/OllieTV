# Stage 2 — QA acceptance checklist

## CI
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` passes and uploads the debug APK.
- [ ] `:app:connectedDebugAndroidTest` passes on API 35.
- [ ] Room migration test upgrades version 1 through version 2 to version 3 without losing existing rows.
- [ ] PIN hashing tests pass.
- [ ] Live-channel filter tests pass, including the 10,000-item fixture.

## Smart Channel List
- [ ] Search matches channel name and category, ignoring case.
- [ ] Category filter can be changed and reset to All channels.
- [ ] Default, A–Z, Z–A, and category sorting produce the expected order.
- [ ] Search/filter/sort remain responsive with 10,000 live channels.
- [ ] Scrolling remains lazy and stable; no eager construction of 10,000 row composables.

## Quick Zapping
- [ ] DPAD up/down switches to previous/next channel.
- [ ] Dedicated channel-up/channel-down keys work where the remote provides them.
- [ ] The zap overlay shows the destination channel.
- [ ] The same ExoPlayer instance is reused when switching channels.
- [ ] A locked destination prompts for the PIN and does not start before successful verification.
- [ ] Repeated zapping does not leak players or listeners.

## Mini EPG
- [ ] Player HUD shows the current programme and progress when EPG data exists.
- [ ] Player HUD shows the next programme.
- [ ] Missing or stale EPG data does not crash playback or display invented metadata.
- [ ] Tablet preview pane shows the selected channel's now/next data and Watch action.

## Recent Channels
- [ ] Playing a live channel adds it to Recent Channels.
- [ ] Replaying a channel moves it to the top rather than creating duplicates.
- [ ] History is capped at 20 stored entries and the UI shows up to 12.
- [ ] Recent list survives app restart.
- [ ] Removing a playlist removes its channels from visible history.

## Favorite Groups
- [ ] Create a group with a unique name.
- [ ] Add a channel to a group from the Live TV list.
- [ ] Group membership survives app restart and playlist refresh.
- [ ] Adding to a group also marks the channel as favourite.
- [ ] Removing a channel from a group leaves its favourite status unchanged.
- [ ] Removing a channel from favourites removes it from all groups.
- [ ] Deleting a group leaves channel favourites unchanged.
- [ ] Deleting a playlist removes stale group memberships.

## Channel Lock
- [ ] First lock action requires creating and confirming a 4–8 digit PIN.
- [ ] PIN is stored as a salted hash, never plaintext.
- [ ] Incorrect PIN is rejected.
- [ ] Correct PIN locks/unlocks the selected channel.
- [ ] Locked channel cannot start playback without PIN verification.
- [ ] Quick zap, Recent Channels, Favorites, and EPG playback all respect the lock.
- [ ] Lock state survives app restart and playlist refresh.
- [ ] Deleting the source playlist removes stale lock entries.

## Stage 1 corrections
- [ ] Back closes Catch-up/Schedule/security/group dialogs before playback.
- [ ] Back closes playback before returning from an episode list to the series list.
- [ ] Tablet at 720 dp and above shows channel list + now/next preview side by side.
- [ ] Resize across 600, 720, and 840 dp without losing the selected destination or crashing.
- [ ] Playback continues through Activity recreation.

## Device acceptance
- [ ] Phone: portrait/landscape, bottom navigation, touch targets, Live TV list and player.
- [ ] Tablet: portrait/landscape, two-pane Live TV at supported widths, rail navigation.
- [ ] Android TV: labelled rail, DPAD focus, channel zapping, mini EPG, Back order.
- [ ] Existing M3U, Xtream, EPG, catch-up, favourites, recordings, and playlist management regressions pass.
