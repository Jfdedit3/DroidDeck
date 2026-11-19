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
        // Ubuntu running under PRoot does not have systemd as PID 1.
        // Prevent package post-install scripts from trying to start daemons.
        // Also repair any packages left unpacked by an earlier interrupted install.
        String command =
            "set -e; " +
            "export DEBIAN_FRONTEND=noninteractive; " +
            "mkdir -p /tmp /var/tmp /root /usr/sbin; chmod 1777 /tmp /var/tmp; " +
            "if [ ! -e /usr/sbin/policy-rc.d ]; then " +
            "printf '#!/bin/sh\\nexit 101\\n' > /usr/sbin/policy-rc.d; chmod 755 /usr/sbin/policy-rc.d; fi; " +
            "LOG=/root/droiddeck-plasma-install.log; " +
            "echo 'Reparation des paquets deja extraits...'; " +
            "dpkg --configure -a > \"$LOG\" 2>&1 || echo 'Certains paquets necessitent une reparation supplementaire'; " +
            "echo 'Correction des dependances APT...'; " +
            "if ! apt-get -f install -y --no-install-recommends >> \"$LOG\" 2>&1; then " +
            "echo 'Erreur de reparation APT (extrait du journal) :'; " +
            "tail -n 100 \"$LOG\"; dpkg --audit || true; exit 31; fi; " +
            "echo 'Finalisation de dpkg...'; " +
            "if ! dpkg --configure -a >> \"$LOG\" 2>&1; then " +
            "echo 'Erreur de configuration dpkg (extrait du journal) :'; " +
            "tail -n 100 \"$LOG\"; dpkg --audit || true; exit 32; fi; " +
            "echo 'Mise a jour des index APT...'; " +
            "if ! apt-get update -o APT::Update::Error-Mode=any >> \"$LOG\" 2>&1; then " +
            "tail -n 65 \"$LOG\"; exit 33; fi; " +
            "echo 'Verification et installation des composants Plasma X11 / VNC...'; " +
            "if ! apt-get install -y --no-install-recommends " +
            "kde-plasma-desktop plasma-session-x11 tigervnc-standalone-server tigervnc-tools " +
            "dbus-x11 xauth xfonts-base >> \"$LOG\" 2>&1; then " +
            "echo 'Erreur installation Plasma (extrait du journal) :'; " +
            "echo '--- PREMIERES ERREURS DPKG / APT (avec contexte) ---'; " +
            "grep -n -i -E -B 4 -A 3 'error|failed|cannot|permission denied|no such file|bad file descriptor|not configured|dependency problems|traceback' \"$LOG\" | tail -n 140 || true; " +
            "echo '--- DIAGNOSTIC CIBLE : ca-certificates ---'; " +
            "dpkg --configure ca-certificates 2>&1 || true; " +
            "echo '--- DIAGNOSTIC CIBLE : plasma-session-x11 ---'; " +
            "dpkg --configure plasma-session-x11 2>&1 || true; " +
            "echo '--- DIAGNOSTIC CIBLE : xserver-xorg ---'; " +
            "dpkg --configure xserver-xorg 2>&1 || true; " +
            "echo '--- FIN DES ERREURS APT ---'; " +
            "tail -n 35 \"$LOG\"; dpkg --audit || true; exit 34; fi; " +
            "command -v startplasma-x11; command -v Xtigervnc; command -v tigervncpasswd; " +
            "test -z \"$(dpkg --audit)\" || { echo 'Paquets incomplets :'; dpkg --audit; exit 35; }; " +
            "touch /" + MARKER + "; echo 'Plasma installe et configure.'";
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
            "mkdir -p /root/.config/tigervnc /tmp/.X11-unix /tmp/runtime-root; " +
            "chmod 700 /root/.config/tigervnc /tmp/runtime-root; " +
            "if [ -d /root/.vnc ]; then " +
            "cp -an /root/.vnc/. /root/.config/tigervnc/ 2>/dev/null || true; " +
            "mv /root/.vnc /root/.vnc.backup.$(date +%s); fi; " +
            "printf '%s\\n' '" + pass + "' | tigervncpasswd -f > /root/.config/tigervnc/passwd; " +
            "chmod 600 /root/.config/tigervnc/passwd; " +
            "printf '%s\\n' '#!/bin/sh' 'unset SESSION_MANAGER' " +
            "'unset DBUS_SESSION_BUS_ADDRESS' 'export TMPDIR=/tmp' " +
            "'export XDG_SESSION_TYPE=x11' 'export XDG_CURRENT_DESKTOP=KDE' " +
            "'export XDG_RUNTIME_DIR=/tmp/runtime-root' 'export QT_X11_NO_MITSHM=1' " +
            "'export LIBGL_ALWAYS_SOFTWARE=1' " +
            "'exec dbus-run-session -- startplasma-x11' > /root/.config/tigervnc/xstartup; " +
            "chmod 700 /root/.config/tigervnc/xstartup; " +
            "rm -f /tmp/.X11-unix/X1; " +
            "Xtigervnc :1 -localhost -SecurityTypes VncAuth -PasswordFile /root/.config/tigervnc/passwd " +
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
