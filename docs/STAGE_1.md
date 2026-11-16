# Stage 1 — Adaptive UI and playback lifecycle foundation

## Baseline
- Repository: `arekgrabka90/OllieTV`
- Branch: `main`
- Base commit: `1a404f6705057ef88af9be799fc239319c217dd8`
- Application baseline: `0.4.1` / versionCode `5`
- Gradle module: one Android application module, `:app`

## Goals
Stage 1 establishes a responsive UI and a configuration-safe playback lifecycle without changing the app's data model or importing new runtime libraries.

## Included
1. Removed the manifest's forced landscape orientation. Android can now recreate the activity in portrait, landscape, and resized multi-window configurations.
2. Added width-based window classes: compact below 600 dp, medium from 600 dp to below 840 dp, and expanded from 840 dp.
3. Added compact bottom navigation for phone-sized windows, an icon navigation rail for medium windows, and the full labelled rail for expanded windows/TV.
4. Added visible focus feedback to navigation items, D-pad/click navigation, and system Back handling for player, dialogs, and the series detail screen.
5. Saved the selected tab, search, category, dialog, selected-series identifier, catch-up target identifier, and schedule-dialog state with `rememberSaveable`.
6. Moved ExoPlayer ownership into a ViewModel-owned `PlaybackEngine`. The Compose player view attaches/detaches the player but does not release it on Activity recreation.
7. Preserved player HUD preferences across configuration changes with saveable Compose state.
8. Centralized the app's graphite palette in `ui/theme` and extracted navigation/window-size concerns into dedicated packages.
9. Removed the unused legacy `PlayerScreen.kt` implementation after confirming there were no call sites. The active `OlliePlayer` composable is extracted into `player/ui` and is backed by the ViewModel engine.
10. Added unit tests for all adaptive window-width boundaries.

## Explicitly unchanged
- Room entities, database version, migrations, and persisted user data.
- M3U/Xtream import, XMLTV/EPG parsing, playlist refresh, favourites, catch-up, recording workers, and recording service.
- Existing dependency versions and the single `:app` Gradle module.
- Existing dark graphite visual direction.

## Validation
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:connectedDebugAndroidTest` on API 35
- Manual acceptance on phone, tablet, and Android TV is still required.

The package installer does not build, commit, or push. It verifies the exact base commit, creates a backup, applies only the listed payload files, runs `git diff --check`, and prints the diff for review.


## Follow-up corrections bundled with Stage 2
- Back priority was corrected so dialogs are dismissed before player exit, and player exit occurs before leaving the selected-series screen.
- The Stage 2 Live TV screen provides a tablet two-pane layout at expanded widths and medium windows of at least 720 dp.
