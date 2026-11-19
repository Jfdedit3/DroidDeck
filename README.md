# DroidDeck Lite — official Ubuntu + Kubuntu Plasma download

This independent Android launcher now installs **the pinned official DroidDeck Ubuntu runtime and Kubuntu Plasma desktop pack**, instead of relying on `apt-get install kde-plasma-desktop` under PRoot.

The matching official archives are provided by [Droid-Deck/DroidDeck-Components](https://github.com/Droid-Deck/DroidDeck-Components/releases/tag/ubuntu-r1-rc5):
- `ubuntu-r1-rc5.tar.zst` — 291,553,163 bytes, SHA-256 `434d3d91f9c93d94deebbbdc57a1b3763443f785925393f15db19feed477e030`
- `ubuntu-r1-rc5-desktop.tar.zst` — 898,306,226 bytes, SHA-256 `e676968a8dc5f3817061731008f877be860e29cc6d017b3170d192513d584de2`

**Installation:** Tap *Installer Ubuntu + Plasma de DroidDeck*. The app downloads both matching archives (supports HTTP Range resume when available), verifies their SHA-256 and exact size, unpacks zstd/tar to an isolated staging directory, verifies desktop manifest and KWin, and activates the new rootfs only on success. Existing Ubuntu is preserved as `ubuntu.before-droiddeck`. You need enough free space for downloads, both extracted images, and the backup.

**Important limitation:** DroidDeck's official desktop pack is configured for its own Wayland compositor and Android display infrastructure. DroidDeck Lite **does not yet include that compositor/view**, so downloading the real desktop image does **not** make the display button open Plasma. The previous VNC launch path requires separately installed TigerVNC/X11; the official pack is not assumed to provide it. Full upstream desktop support requires porting the native session/display components.

Sources: upstream [runtime installer](https://github.com/Droid-Deck/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/runtime/LinuxRuntimeInstaller.java), [desktop pack](https://github.com/Droid-Deck/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/runtime/DesktopPack.kt), and [build script](https://github.com/Droid-Deck/DroidDeck/blob/main/tools/ubuntu/build.sh). Licensing: consult upstream repo and component licenses.
