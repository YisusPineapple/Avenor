# Nexo Audio Technical Guide

## Custom AudioProcessor Architecture
Nexo relies on ExoPlayer's extensible architecture for audio processing. We implement custom AudioProcessors for gapless playback, crossfading, and dynamic EQ. These processors intercept the raw PCM audio buffers before they reach the AudioTrack, applying transformations efficiently.

## Lyric Offset Synchronization Logic
Lyrics are synchronized using a `.lrc` parsing engine. To handle improperly timed lyrics:
1. **LyricSyncState**: A Room database entity maps a `songId` (URI or unique ID) to a `offsetMs` (Long).
2. **Real-time Adjustment**: The `LyricSyncOverlay` allows users to nudge the offset by +/- 2000ms.
3. **Application**: The active `offsetMs` is subtracted from the current playback position when finding the active lyric line, shifting the UI focus in real-time.
4. **Persistence**: Changes are immediately saved to the DAO, preserving per-track sync indefinitely.

## Memory-Efficient Resource Management
To maintain the <120MB RAM target:
- **MemoryWatchdog**: A background coroutine polls `Runtime.getRuntime().totalMemory()`. If usage exceeds 120MB, it clears Coil's memory cache and requests garbage collection (`System.gc()`).
- **Dynamic Color Extraction**: Palette generation runs on Dispatchers.IO and only keeps the resulting `ColorScheme` in memory, discarding the source Bitmap.
- **DeviceProfileManager**: Dynamically sizes ExoPlayer's forward and backward buffers based on the device's CPU core count and performance preset (VIVID/ECO).
