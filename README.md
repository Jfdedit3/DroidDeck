# DroidDeck Lite — rebuilt from scratch

Independent Android application starter for Jfdedit3, replacing the previous DroidDeck fork working tree.

## Available now
- Android dashboard and runtime status
- Official Ubuntu Base ARM64 image download, verified against pinned SHA-256
- Buildable debug APK through GitHub Actions, no private CI secrets

## Not implemented yet
- Extracting and starting the Ubuntu runtime with native PRoot
- KDE Plasma / Wayland desktop
- FEX, Wine, Proton, and PC gaming

**Important:** Downloading the Ubuntu archive does not install or run a Linux environment. The interface labels it as a downloaded image, not a working Linux system.

## CI
Actions > Build Android APK > Run workflow. Download the `DroidDeck-Lite-debug-apk` artifact from the successful run.

## Local build
Install JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.10.2 and run `gradle :app:assembleDebug`.
