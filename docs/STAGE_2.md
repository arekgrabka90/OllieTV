# Stage 2 — Live TV Premium

## Baseline
- Repository: `arekgrabka90/OllieTV`
- Branch: `main`
- Base commit: `1a404f6705057ef88af9be799fc239319c217dd8`
- Stage 1 is already applied locally and remains uncommitted.
- Gradle module: one Android application module, `:app`

## Included scope

### 1. Smart Channel List
- Search across channel name and category with a short debounce.
- Category filters and selectable sort order (default, name ascending/descending, category).
- Filtering/sorting runs on `Dispatchers.Default`, not on the Compose main thread.
- Lazy list uses stable stream IDs and item content types.
- The implementation is designed for large playlists; 10,000-channel acceptance remains a required CI/manual performance check.

### 2. Quick Zapping
- DPAD up/down and dedicated channel-up/channel-down keys switch live channels.
- The playback engine reuses the existing ExoPlayer instance when changing streams instead of releasing and recreating it for every zap.
- A short on-screen channel confirmation identifies the selected destination.
- Locked channels request the parental PIN before playback.

### 3. Mini EPG
- Player HUD shows the current programme, its progress, and the next programme when XMLTV data exists.
- The expanded tablet live-TV pane shows the selected channel's now/next information before playback.
- Missing EPG data is shown as unavailable; programme data is never fabricated.

### 4. Recent Channels
- Last 20 live-channel plays are stored by stable stream ID; the UI shows the latest 12.
- History updates asynchronously and does not store stream URLs or provider credentials.
- Removed/disabled playlist channels are excluded from the visible recent list.

### 5. Favorite Groups
- Users can create/delete named groups, add live channels from the channel list, and remove channels from a selected group.
- A channel added to a group is also marked as a favourite.
- Removing a channel from favourites removes its group memberships.
- Group membership is stored separately from playlist data.

### 6. Channel Lock
- Users can lock/unlock individual live channels.
- First use creates a 4–8 digit parental PIN; later changes and playback access require that PIN.
- Only a salted PBKDF2-HMAC-SHA256 hash is stored in private app preferences; the PIN is never stored as plaintext.
- Channel lock state is stored separately in Room.
- This is a local parental-control barrier, not DRM or a guarantee against a technically advanced device owner.

### 7. Stage 1 corrections included
- Back handling now dismisses security/group/catch-up/schedule dialogs before closing playback, and closes playback before leaving the selected-series screen.
- Expanded tablet windows, plus medium windows at or above 720 dp, use a two-pane Live TV layout with channel list and now/next preview.

## Persistence and migration
- Room database version advances from 2 to 3.
- Migration `2 → 3` creates `favorite_groups`, `favorite_group_channels`, `channel_history`, and `channel_locks`.
- Existing playlists, streams, EPG programmes, recordings, and favourites are retained.
- No existing table is dropped or rebuilt.
- No runtime dependency versions are changed.

## Validation gates
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:connectedDebugAndroidTest` on API 35
- Manual phone, tablet, and Android TV acceptance.
- Performance check with a representative 10,000-channel playlist.

The installer applies Stage 2 on top of the locally installed Stage 1 working tree. It does not commit, push, or run a local Android build.
