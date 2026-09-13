# Nexo: Hardware Requirements & Technical Specifications

Nexo is engineered with a strict performance-first architecture. By utilizing dynamic caching, asynchronous audio processing (CrossfadeManager), and an optimized embedded database (Room with automatic VACUUM), Nexo achieves extreme efficiency across multiple platforms.

The following estimates detail the hardware requirements for Nexo across Linux, Windows, and Android, based on Jetpack Compose (and Compose Multiplatform) targets.

---

## 1. Android (Native Architecture)
Nexo leverages native Media3 components and Jetpack Compose without bridging to WebViews, keeping the footprint exceptionally small.

### Minimum Specifications (ECO Mode)
Designed for legacy and Android Go Edition devices.
* **CPU:** Quad-Core 1.2 GHz (e.g., Snapdragon 400 series or MediaTek MT6739)
* **RAM:** 1 GB
* **Storage:** 15 MB available space
* **OS:** Android 8.0 Oreo (API Level 26)
* **Notes:** `ECO` preset automatically disables heavy graphics shaders, procedural animations, and limits background polling to prevent CPU stalls during audio crossfade.

### Recommended Specifications (VIVID Mode)
* **CPU:** Octa-Core 1.8 GHz or superior
* **RAM:** 3 GB or higher
* **Storage:** 50 MB available space
* **OS:** Android 11+ (API Level 30+)
* **Notes:** Supports fluid 60/120fps edge-to-edge UI transitions, real-time dynamic blur, and instantaneous Room database queries.

---

## 2. Windows (Compose Multiplatform)
If ported to Windows using Compose Multiplatform / JVM, Nexo bypasses the heavy RAM penalties of Electron-based music players.

### Minimum Specifications (ECO Mode)
* **CPU:** Intel Pentium E5800 / Celeron N3060 (Dual-Core)
* **RAM:** 4 GB System RAM (Application consumes ~200MB - 250MB)
* **Graphics:** Intel HD Graphics
* **OS:** Windows 10 (64-bit)
* **Notes:** The offline HTML Help guide and static UI layouts prevent UI thread starvation on dual-core processors.

### Recommended Specifications (VIVID Mode)
* **CPU:** Intel Core i3 (4th Gen) or AMD Ryzen equivalent
* **RAM:** 8 GB System RAM (Application footprint remains under 300MB)
* **Graphics:** DirectX 12 compatible GPU for hardware-accelerated Compose rendering
* **OS:** Windows 11

---

## 3. Linux (Compose Multiplatform)
For Linux distributions, Nexo operates efficiently under lightweight desktop environments (XFCE, LXQt) due to JVM optimizations.

### Minimum Specifications (ECO Mode)
* **CPU:** Intel Core 2 Duo / AMD Athlon 64 X2 (Dual-Core)
* **RAM:** 2 GB System RAM
* **Display Server:** X11
* **OS:** Ubuntu 20.04 LTS (or equivalent Debian-based distributions)
* **Notes:** Ideal for legacy hardware running lightweight distributions (e.g., Lubuntu, Xubuntu).

### Recommended Specifications (VIVID Mode)
* **CPU:** Intel Core i3 / AMD Ryzen 3
* **RAM:** 4 GB System RAM
* **Display Server:** Wayland (with XWayland fallback) or X11
* **OS:** Ubuntu 22.04 LTS+ or Arch Linux
* **Notes:** Enables full hardware acceleration via Skia for smooth visual equalizers and list scrolling.

---

## System Optimization Guide
Nexo bundles an offline `faq.html` containing optimization tips for users on constrained hardware. Key recommendations include:
1. **Background Processes:** Clear unnecessary apps to ensure the Media3 Audio Service receives uninterrupted CPU cycles, particularly during EQ initialization.
2. **Database Health:** Nexo's SQLite database runs an automated `VACUUM` threshold sweep on files > 5MB to mitigate fragmentation on cheap eMMC storage.
3. **Crossfade Latency:** The `CrossfadeManager` actively monitors CPU starvation. If thermal throttling causes execution delays > 200ms, crossfading snaps immediately to the target volume, ensuring the main audio thread never tears or stutters.
