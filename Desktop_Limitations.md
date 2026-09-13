# Desktop Limitations

While Nexo Audio is built primarily for Android, it is designed with cross-platform considerations using Jetpack Compose and ExoPlayer. However, certain features behave differently on desktop environments (Windows, Linux, macOS).

## Hardware Feature Parity

- **MemoryWatchdog**: Bypassed on Desktop. Desktop JVMs manage memory differently, and the strict 120MB heap limit is not enforced, allowing up to 250MB for better caching and performance.
- **Mobile CPU Profiling**: Android-specific CPU scaling (VIVID/BALANCED/ECO presets) relies on mobile architecture patterns. On Desktop, these presets are replaced by a dedicated high-performance desktop audio quality setting.
- **Audio Stacks**: Desktop platforms utilize their native audio stacks (ASIO/WASAPI on Windows, PulseAudio/PipeWire on Linux). Nexo optimizes ExoPlayer's buffer sizes to accommodate these high-latency stacks, bypassing the standard mobile performance modes.
- **Storage Access**: Scoped Storage permissions (READ_MEDIA_AUDIO) are Android-specific. On Desktop, standard file system access is used.
