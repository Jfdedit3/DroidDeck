package com.jfdedit3.droiddeck;

import android.content.Context;
import java.io.File;
import java.security.SecureRandom;

/**
 * KDE Plasma X11 inside Ubuntu via TigerVNC, bound to device loopback only.
 * VNC requires a viewer application; this class does not pretend to display it in-app.
 */
public final class PlasmaManager {
    private final Context context;
    private final LinuxEngine linux;
    private static final String MARKER = ".droiddeck-plasma-x11";
    public static final String ADDRESS = "127.0.0.1:5901";

    public PlasmaManager(Context context, LinuxEngine linux) {
        this.context = context.getApplicationContext();
        this.linux = linux;
    }
    public boolean installed() { return new File(linux.root(), MARKER).isFile(); }
    public String password() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("plasma-vnc", Context.MODE_PRIVATE);
        String value = prefs.getString("password", null);
        if (value != null && value.matches("[a-f0-9]{8}")) return value;
        byte[] bytes = new byte[4];
        new SecureRandom().nextBytes(bytes);
        StringBuilder b = new StringBuilder();
        for (byte v : bytes) b.append(String.format(java.util.Locale.ROOT, "%02x", v & 0xff));
        value = b.toString();
        prefs.edit().putString("password", value).apply();
        return value;
    }

    public void install(LinuxEngine.ConsoleListener callback) {
        if (!linux.isInstalled()) {
            callback.line("Installe Ubuntu avant KDE Plasma.");
            callback.done(-1);
            return;
        }
        // The Ubuntu 26.04 X11 session is a separate package.
        String command = "set -e; " +
            "export DEBIAN_FRONTEND=noninteractive; " +
            "mkdir -p /tmp /var/tmp /root; chmod 1777 /tmp /var/tmp; " +
            "apt-get update; " +
            "apt-get install -y --no-install-recommends " +
            "kde-plasma-desktop plasma-session-x11 tigervnc-standalone-server tigervnc-tools " +
            "dbus-x11 xauth xfonts-base; " +
            "command -v startplasma-x11; command -v Xtigervnc; command -v tigervncpasswd; " +
            "touch /" + MARKER + "; echo 'Plasma installe et pret.'";
        // Correct absolute marker location is rootfs/.droiddeck-plasma-x11
        linux.execute(command, callback);
    }

    public void start(LinuxEngine.ConsoleListener callback) {
        if (!installed()) {
            callback.line("Plasma n'est pas installé. Utilise Installer Plasma.");
            callback.done(-1);
            return;
        }
        String pass = password(); // strict 8-char lowercase hex, never accepts shell metacharacters
        String command =
            "set -e; " +
            "mkdir -p /root/.vnc /tmp/.X11-unix /tmp/runtime-root; " +
            "chmod 700 /tmp/runtime-root; " +
            "printf '%s\\n' '" + pass + "' | tigervncpasswd -f > /root/.vnc/passwd; " +
            "chmod 600 /root/.vnc/passwd; " +
            "rm -f /tmp/.X11-unix/X1; " +
            "Xtigervnc :1 -localhost -SecurityTypes VncAuth -PasswordFile /root/.vnc/passwd " +
            "-rfbport 5901 -geometry 1280x720 -depth 24 & " +
            "server=$!; sleep 3; " +
            "if ! kill -0 $server 2>/dev/null; then echo 'TigerVNC n a pas demarre'; exit 1; fi; " +
            "echo 'VNC PRET : 127.0.0.1:5901'; " +
            "export DISPLAY=:1 XDG_SESSION_TYPE=x11 XDG_CURRENT_DESKTOP=KDE " +
            "XDG_RUNTIME_DIR=/tmp/runtime-root QT_X11_NO_MITSHM=1 LIBGL_ALWAYS_SOFTWARE=1; " +
            "dbus-run-session -- startplasma-x11; result=$?; kill $server 2>/dev/null || true; exit $result";
        linux.execute(command, callback);
    }
}
