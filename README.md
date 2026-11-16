# Ollie TV — Android IPTV client (0.4.0)

Independent Android IPTV client. It is not based on VIREO and contains no TiviMate source code, branding, or proprietary assets. Its own dark, remote-first interface follows familiar IPTV navigation patterns.

## Added in 0.4.0

### Full-day EPG timeline
- 24-hour time ruler with proportional programme widths based on actual start/end timestamps.
- Horizontal timeline scrolling, shared across channel rows and the time ruler.
- Previous day / Today / Next day navigation.
- Programme detail dialog with description, date/time and Watch channel action.
- XMLTV feed refresh can be configured per playlist.

### Recording
- Immediate recording for direct HTTP streams and clear MPEG-TS HLS playlists.
- HLS master-playlist first-variant selection, relative segment URL resolution, segment de-duplication and live-playlist polling.
- HLS encrypted media and fMP4/CMAF `EXT-X-MAP` playlists are rejected with an explicit error; DRM is not bypassed.
- Scheduled recordings use persistent WorkManager one-time jobs with network constraints, start time and duration.
- Recording library supports local playback and deletion; deleting a scheduled item cancels its WorkManager job.
- Recordings are stored under the app-private Movies/OllieTV directory.

### Playlist manager and refresh
- Enable/disable each source.
- Reorder sources.
- Manual refresh and delete.
- Per-source automatic refresh interval (1, 6, 12 or 24 hours).
- Optional per-source XMLTV URL, refreshed by the periodic worker.
- WorkManager runs an hourly check and refreshes only sources whose configured interval has elapsed.
- Favourite flags are preserved across playlist refreshes.

### Player HUD and remote
- Custom overlay HUD with programme information, progress/time display and auto-hide.
- DPAD Up/Down channel zapping; DPAD Left/Right 10-second seek when HUD is hidden.
- Media Play/Pause keys and center/enter to reveal controls.
- Previous/next channel buttons, play/pause, ±10-second seek, aspect-ratio modes, record, schedule, stop and catch-up actions.
- Focusable remote-first controls.

### Existing functionality retained
- M3U file and URL import.
- Xtream Codes login and Live/VOD/Series catalogues.
- Xtream season/episode URL resolution.
- Room persistence and Android Keystore encryption for credentials.
- Favorites, categories, search, XMLTV parser and Media3 playback.

## Known limitations / important behavior

- This is a larger feature-complete foundation, but **not yet a verified production release or a one-to-one TiviMate replacement**.
- The EPG timeline uses a 24-hour proportional scale, but channel/programme mapping still depends on exact `tvg-id` or Xtream EPG IDs. Some providers need manual mapping. The timeline is a Compose implementation and needs performance/focus QA on large playlists and TV remotes.
- XMLTV import currently reads XMLTV XML. Gzip feeds, provider-specific XMLTV quirks, daylight-saving edge cases and very large feeds need more compatibility testing.
- HLS recording supports clear MPEG-TS media playlists. Encrypted AES-128/SAMPLE-AES/DRM and fMP4/CMAF segments are not recorded. Direct-stream recording depends on the server exposing a continuous HTTP response.
- Scheduled recording is managed by WorkManager. Android may defer jobs due to Doze, battery policy, network availability or manufacturer background restrictions; it is not a hard real-time broadcast recorder. Keep the device powered and network-connected.
- Automatic playlist refresh is best-effort and runs via WorkManager. M3U playlists imported from a local file cannot be refreshed automatically; re-import the file. Remote M3U and Xtream sources can refresh.
- Xtream catch-up uses the common `timeshift.php` URL pattern; providers differ.
- Immediate recording uses a foreground service; scheduled recording uses a foreground WorkManager worker. Android 13+ notification permission may be needed for visible notifications.
- No Gradle build or physical Android TV/device test could be run in this environment because Gradle/Android SDK are not installed here. This archive is source code, **not a verified APK**.

## Build

Open the project folder in Android Studio with JDK 17 and Android SDK 35 installed. Sync Gradle and run the `app` configuration. Dependencies include AndroidX WorkManager 2.10.0. The Gradle wrapper properties are included; if wrapper launcher scripts are absent, generate them in Android Studio or with an installed Gradle distribution.

## Legal / compatibility

Use only IPTV subscriptions and streams you are authorized to access. Ollie TV does not provide channels or bypass provider access controls.
