# Avenor Audio 🎵

**Premium, Hardware-Accelerated Local Music Player**

Avenor is an advanced, offline-first local music player meticulously designed with Material 3 principles and Jetpack Compose. It aims to bridge the gap between audiophile-grade media engines and striking, highly responsive UI aesthetics.

## 💻 Requirements

### Android

| | Minimum | Recommended |
|---|---|---|
| OS | Android 8.0 (API 26) | Android 13+ (API 33) |
| CPU | Dual-core 1.2 GHz | Quad-core 1.8 GHz or better |
| RAM | 2 GB | 4 GB |
| Storage | Varies with library size | — |
| Performance Mode | ECO | VIVID |

Avenor runs on Android 8.0 and above. On devices below the recommended specs, the app automatically selects the **ECO** performance profile, which disables background shaders, procedural gradients, and heavy animations to preserve smooth playback.

### Desktop (Windows & Linux)

| | Minimum | Recommended |
|---|---|---|
| OS | Windows 10 (64-bit) / Modern Linux (X11) | Windows 11 / Modern Linux (Wayland) |
| CPU | Dual-core (e.g. Intel Pentium) | Intel Core i3 / AMD Ryzen 3 or better |
| RAM | 4 GB (Windows) / 2 GB (Linux) | 8 GB (Windows) / 4 GB (Linux) |
| JRE | Bundled with the installer — no separate Java install required | — |
| Additional | **VLC Media Player must be installed** (see Desktop section) | — |

The Windows `.msi` and Linux `.deb` installers bundle their own JRE. VLC Media Player is **not bundled** and must be installed separately.

## ✨ Key Features

- **Multi-Queue Architecture**: Avenor features a unique queue management system, allowing you to maintain multiple independent queues, effortlessly transition between different listening sessions, and preserve the state of playlists without losing your place.
- **Cross-Platform Architecture (Compose)**: Built with Kotlin and Jetpack Compose for **Native Android** and **Desktop** (Compose Multiplatform on JVM for Windows and Linux). See the **Desktop** section below for system requirements.
- **Dynamic Crossfade Engine**: Manual crossfade (smooth volume transition on manual track skips) is active. Automatic crossfade (auto-transition between consecutive tracks in the queue) is paused until Phase 3 (task 3.17) due to a known re-entrancy bug in `DualPlayerCrossfadeManager`.
- **SmartTrash Recovery System**: Never lose a track by accident. Deleted songs are placed in a 30-day grace period queue with an active background WorkManager, allowing for secure recovery or immediate deep-purging to free up space.
- **Embedded & Local Lyrics**: Reads embedded `USLT`/`LYRICS` tags from ID3v2, `LYRICS`/`UNSYNCEDLYRICS` from Vorbis Comments, and sibling `.lrc` files in the filesystem. When synchronized timestamps are not present, plain-text lyrics are displayed in a scrollable view.
- **Android Permissions**: Uses `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12 and below) for local audio indexing, plus optional `POST_NOTIFICATIONS` (Android 13+) solely to display the media playback notification.

## 🖥 Desktop (Windows & Linux)

- **Requirements**: Avenor Desktop on Windows and Linux uses VLCJ (Java bindings), which does not bundle `libVLC`. It requires **VLC Media Player** to be installed on the system.
- **Installation**: Download and install VLC from https://www.videolan.org/vlc/.
- **Missing VLC Detection**: If VLC is not found at startup, the app detects it and displays a dedicated screen with the download link instead of failing silently.
- **Builds**: `.deb` (Linux) and `.msi` (Windows) installers are automatically generated via GitHub Actions (`build-desktop.yml`).

## 🚀 Managing Build Workloads (No Powerful PC Required)

You do **not** need Android Studio or a high-end computer to build Avenor. We leverage **GitHub Actions** to offload the heavy lifting (compilation, testing, and linting) to the cloud.

The repository includes a pre-configured `.github/workflows/android-ci.yml` file. Whenever you push changes to the `main` branch, GitHub Actions will automatically:
1. Compile the Android APK natively using Ubuntu cloud runners.
2. Limit Gradle's memory footprint and parallel execution (`--max-workers=2`) to ensure stable CI builds.
3. Attach the built `app-debug.apk` and `app-release.apk` as downloadable artifacts in the "Actions" tab of your GitHub repository.

You can download the APK directly from GitHub to your phone without ever opening Android Studio!

## 🔐 Generating a Production Keystore (For automated Release Signing)

To enable GitHub Actions to automatically build signed production releases of your app that can be published or updated securely on Android, you need a Keystore.

**Step 1: Generate the Keystore via Command Line**
Open your terminal (or Command Prompt) and run the following command (you must have Java installed):
```bash
keytool -genkey -v -keystore avenor-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias avenor
```
- It will prompt you for a password (e.g., `avenor123`). **Remember this!**
- It will ask for your name, organization, etc. (you can press Enter to skip or fill them in).
- Finally, type `yes` to confirm.

**Step 2: Convert the Keystore to Base64**
GitHub Secrets cannot store binary `.jks` files directly, so you must encode it to a base64 string:
- **Linux/Mac**: `base64 avenor-release-key.jks > keystore_base64.txt`
- **Windows (PowerShell)**: `[convert]::ToBase64String((Get-Content -path "avenor-release-key.jks" -Encoding byte)) | Out-File keystore_base64.txt`

**Step 3: Securely Add to GitHub Secrets**
1. Go to your GitHub Repository -> **Settings** -> **Secrets and variables** -> **Actions**.
2. Click **New repository secret**. Add the following four secrets:
   - `SIGNING_KEY`: Paste the entire contents of `keystore_base64.txt` here.
   - `KEY_ALIAS`: Enter `avenor` (or the alias you chose).
   - `KEY_STORE_PASSWORD`: Enter the password you created.
   - `KEY_PASSWORD`: Enter the password you created.
3. In the `.github/workflows/android-ci.yml` file, uncomment the `Sign APK` step. Future commits will automatically output a fully signed production APK!

## 🛠 Building from Source (Local CLI)

If you have a low-resource machine but want to build locally, use the command line (avoiding Android Studio entirely):

```bash
# Clone the repository
git clone https://github.com/YourUsername/avenor.git
cd avenor

# The provided gradle.properties ensures memory is capped.
# Build the Debug APK locally without the Gradle Daemon hoarding RAM:
./gradlew assembleDebug --no-daemon

# Build the Release APK (requires keystore configuration)
./gradlew assembleRelease --no-daemon
```

## 📦 Database & Legacy Compatibility Policy

### Room Schema Versioning & Export
- **Current Version**: 21
- **Schema Export**: Enabled (`exportSchema = true`) via KSP under `app/schemas/` to ensure reproducible, version-controlled schema definitions for every database evolution.
- **Destructive Migrations**: Prohibited. Avenor strictly forbids `fallbackToDestructiveMigration()` to protect local music metadata, playlists, and settings from silent data loss.

### Supported Migrations (Automatic & Non-Destructive)
Any database on version 11 or higher can safely and automatically migrate to version 21:
- **v11 → v12 → v13 → v14 → v15 → v16 → v17 → v18 → v19 → v20 → v21** (Full contiguous chain: `MIGRATION_11_12`, `MIGRATION_12_13`, `MIGRATION_13_14`, `MIGRATION_14_15`, `MIGRATION_15_16`, `MIGRATION_16_17`, `MIGRATION_17_18`, `MIGRATION_18_19`, `MIGRATION_19_20`, `MIGRATION_20_21`)
- **v12 → v13 → v14 → v15 → v16 → v17 → v18 → v19 → v20 → v21**
- **v13 → v14 → v15 → v16 → v17 → v18 → v19 → v20 → v21**
- **v14 → v15 → v16 → v17 → v18 → v19 → v20 → v21**
- **v15 → v16 → v17 → v18 → v19 → v20 → v21**
- **v16 → v17 → v18 → v19 → v20 → v21**
- **v17 → v18 → v19 → v20 → v21**
- **v18 → v19 → v20 → v21**
- **v19 → v20 → v21**
- **v20 → v21**
- **New Installations**: Directly provisioned in **v21** through standard Room creation and initial preset population callbacks.

### Unsupported Versions (Legacy Gap)
- **v1, v2, v3, v4, v5, v6, v7, v8, v9, v10 → v21**
- **Technical Reason**: Absence of verifiable historical schema evidence or DDL logs for intermediate transitions 5→6, 6→7, 7→8, 8→9, 9→10, and 10→11.
- **Data Safety**: These legacy databases are not invalid or corrupted. However, Avenor cannot guarantee an automatic migration without risk of schema mismatch or data loss, and deliberately refuses to introduce speculative or destructive fallback mechanisms.

