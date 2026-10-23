# MusicApp (offline player + Kwala-style sampler + editor)

Stability-first Android music app built from the features chosen out of `inspo/`:
- **Effin/Retro Music core**: library, queue playback, shuffle/repeat, mini player
- **Effin sample-play section** → full **Kwala-style 8-pad sampler** (polyphonic, file→pad import)
- **Audio editor**: waveform view, trim, fade in/out, gain, WAV export, one-tap "send to pad"
- LatentJam's map is intentionally **not** ported (too expensive); kept on the backlog.

## Module dependency graph (what depends on what)

```
MainActivity ──► AppState (single state holder)
                  ├──► MusicRepository ──► MediaStore            [Library scan]
                  ├──► MusicPlayerServiceBinder ──► MediaPlayer, MediaSessionCompat
                  │        └── uses data.Song / RepeatMode / ShuffleMode
                  ├──► SamplerEngine ──► MediaCodec+MediaExtractor (decode), AudioTrack MODE_STATIC (play)
                  │        └── uses editor.AudioEditor.normalize (optional)
                  └──► AudioEditor (pure PCM functions: applyPcm/normalize/waveform/writeWav)
                           └── decode path reuses SamplerEngine.decodeToSample

UI screens depend only on AppState:
  LibraryScreen / SamplerScreen / EditorScreen ──► AppState ──► (modules above)
```

Key rules that keep it stable:
1. No module reaches into another's internals — everything flows through `AppState`.
2. Player owns exactly one `MediaPlayer`; corrupt files are skipped, never crash.
3. Sampler pads are preloaded with synthesized tones so the grid always works even before imports.
4. Editor math is pure functions over `ShortArray` → unit-testable without a device.
5. **No INTERNET permission** — offline by construction, not just by convention.

## Feature → dependency table

| Feature | Depends on | Notes |
|---|---|---|
| Offline library | MediaStore, READ_MEDIA_AUDIO | rescan button; albums/artists derived |
| Queue playback / gap-ish next-track | MediaPlayer + file paths | error listener auto-skips bad files |
| Shuffle / Repeat (off/all/one) | player rebuildOrder | current song pinned when toggling shuffle |
| Lockscreen/notification controls | MediaSessionCompat | session active; wire a MediaBrowserService later for foreground service |
| Mini player UI | player position polling | 500 ms LaunchedEffect poll |
| 8-pad sampler | SamplerEngine, AudioTrack STATIC | tap = fire, long-press = assign file |
| Sample import (file→pad) | MediaCodec/MediaExtractor decode | stereo downmixed to mono |
| Waveform view | AudioEditor.waveform | min/max envelope, 240 buckets |
| Trim/fade/gain/export | AudioEditor.applyPcm + writeWav | exports 16-bit mono WAV to cache |
| Edited sample → pad | AppState.applyEdit(assignToPad) | closes loop between editor and sampler |
| EQ / ReplayGain | *(backlog)* | needs android.media.audio.EqualizerReleaser or DSP chain |
| Lyrics, playlists CRUD | *(backlog)* | Retro Music parity items |

## Build

```bash
./gradlew :app:assembleDebug   # requires Android SDK; no gradle wrapper jar committed here
```
minSdk 24 · target/compile 34 · Kotlin 1.9.22 · Compose BOM 2024.02 · Material3

## Roadmap (next, in stability order)
1. Wrap playback in a `MediaSessionService` (foreground) so audio survives screen off.
2. Playlists persisted in Room.
3. EQ via `android.media.audiofx.Equalizer` attached to `player.audioSessionId`.
4. Pad slicing/choke/reverse (Kwala pro features) — all pure PCM ops, easy to add to AudioEditor.

## Poster Wall (Iris Music feature — integrated)

The only Iris Music feature kept: the infinite **Poster Wall** album-art browser.

| File | Role | Depends on |
|---|---|---|
| `ui/wall/WallSolver.kt` | Pure layout math ported from Iris's PosterWall: `packWall` (gapless 1x1/2x1/1x2/2x2 mosaic, deterministic seed, one tile per song) + `solveWall` (make-way solver: active card grows to 2x2, neighbors shrink/relocate/recursive-push/hole-fill; rolls back on failure) | nothing (zero Android deps → runs on Dispatchers.Default, unit-testable) |
| `ui/ArtworkLoader.kt` | 3-layer artwork cache ported from Iris: memory LRU → disk thumbnails in cacheDir (keyed by path\|size\|mtime) → downsampled MediaMetadataRetriever decode; in-flight dedup, failed-path memo | MediaMetadataRetriever, cacheDir |
| `ui/wall/PosterWall.kt` | Compose wall: pan/pinch camera on one graphicsLayer, 4-direction infinite tiling with cancellable vsync fling, row-bucketed visibility culling, tile move animations, haptic quarter-cell ticks, nearest-first artwork warm-up, tap-to-play + drag-seek progress on the active card | WallSolver, ArtworkLoader, player (position/seek/queue), Material3 |

Wiring: bottom-nav tab **Wall** (`Tab.WALL`) in `MainActivity`; `AppState.init` calls
`ArtworkLoader.init()` and bumps `playerVersion` on every player state change so the
wall recomposes when play/pause/skip happens. Tap a poster → plays that song; tap the
active poster → toggles pause/resume.
