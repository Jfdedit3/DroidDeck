# DroidDeck Lite 0.2 — Ubuntu ARM64 and PRoot terminal

Android application rebuilt for Jfdedit3. Not affiliated with the original DroidDeck developers.

## Features
- Download official Ubuntu Base 26.04.1 ARM64 with SHA-256 verification
- Safely unpack Ubuntu tar.gz into app-private storage, preserving existing installation during updates
- Bundle pinned DroidDeck PRoot ARM64 and loader at APK build time (GPL license applies)
- Launch non-interactive shell commands inside Ubuntu through PRoot, stream stdout/stderr to the on-screen console
- Stop running commands and display exit codes
- No root access necessary

**Limitations:** no PTY interactive terminal, no KDE Plasma display, Wine, FEX or Proton at this stage. Commands run in a new /bin/sh process; state like `cd` does not persist between executions. Installing packages with apt may require additional PRoot system configuration and network DNS setup. The Android app process must remain alive while running a command.

## Build
GitHub Actions > Build Android APK. Download the `DroidDeck-Lite-debug-apk` artifact.

The workflow fetches a pinned PRoot package from the upstream open-source project and verifies its SHA-256 before packaging. Attribution and source: https://github.com/Droid-Deck/DroidDeck (GPL-3.0). Ubuntu Base is provided by Canonical.

## Android permissions
Only INTERNET permission. Target SDK 28 preserves compatibility with executable-in-app-data restrictions on some Android versions; future upgrades require compatibility testing.
