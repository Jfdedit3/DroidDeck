# DroidDeck Lite 0.3 — Ubuntu ARM64 + KDE Plasma X11 (VNC)

Android app rebuilt for Jfdedit3, not affiliated with upstream DroidDeck.

## Current capabilities

- Pinned Ubuntu Base 26.04.1 ARM64 download and extraction, SHA-256 verification
- Native ARM64 PRoot bundled at APK build time
- Shell commands through PRoot with on-screen output
- **Install KDE Plasma 6** using Ubuntu's APT repositories, including `plasma-session-x11`, `tigervnc-standalone-server`, `tigervnc-tools` and `dbus-x11`
- **Start KDE Plasma X11** through TigerVNC, listening on **127.0.0.1:5901** only
- Generates a per-installation VNC password; copy the credentials from the Android app
- Launch a third-party Android VNC viewer using a `vnc://` intent

## How to use

1. Install the APK from GitHub Actions artifacts.
2. Tap **Installer Ubuntu** and wait for the runtime to extract.
3. Tap **Installer KDE Plasma**, which downloads many ARM64 packages; sufficient free disk space and Internet are necessary.
4. Tap **Lancer le bureau Plasma**.
5. In an Android VNC viewer, connect to **127.0.0.1:5901**, password shown in-app.

### Limitations and testing

Plasma runs via **software-rendered X11 and VNC**, not GPU-accelerated native Android Vulkan. You must use a separate VNC viewer, not a built-in display surface. Actual operation on Android 16 / Xclipse 920 has not yet been device-tested. PRoot, Linux package installation, system services and dbus may require compatibility fixes. Commands are executed one at a time; this is not a PTY terminal. Wine/Proton/FEX not included.

Ubuntu 26.04 requires the separate `plasma-session-x11` package for X11 sessions. Package listings: https://packages.ubuntu.com/resolute/arm64/plasma-session-x11 and https://packages.ubuntu.com/en/resolute/tigervnc-standalone-server.

GPL-3.0 upstream PRoot package sourced and verified from https://github.com/Droid-Deck/DroidDeck-Components.
