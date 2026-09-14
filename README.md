# Avenor Audio 🎵

**Premium, Hardware-Accelerated Local Music Player**

Avenor is an advanced, offline-first local music player meticulously designed with Material 3 principles and Jetpack Compose. It aims to bridge the gap between audiophile-grade media engines and striking, highly responsive UI aesthetics.

## ✨ Key Features

- **Multi-Queue Architecture**: Avenor features a unique queue management system, allowing you to maintain multiple independent queues, effortlessly transition between different listening sessions, and preserve the state of playlists without losing your place.
- **Cross-Platform Architecture (Compose)**: Built with Kotlin and Jetpack Compose. While currently optimized for **Native Android**, the core UI and logic are decoupled from the OS, paving the way for seamless Compose Multiplatform scaling to Desktop (Windows/Linux) in future releases.
- **Dynamic Crossfade Engine**: A custom-built, hardware-accelerated `ValueAnimator` engine that guarantees smooth volume interpolation and seamless transitions between tracks. It scales dynamically based on your device's active profile (Vivid, Balanced, or Eco).
- **SmartTrash Recovery System**: Never lose a track by accident. Deleted songs are placed in a 30-day grace period queue with an active background WorkManager, allowing for secure recovery or immediate deep-purging to free up space.

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
