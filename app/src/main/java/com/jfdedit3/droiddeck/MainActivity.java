package com.jfdedit3.droiddeck;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int bg = 0xff0b101b, panel = 0xff192333, accent = 0xff239af0;
    private TextView state, detail, output;
    private EditText command;
    private Button install, run, stop, plasmaInstall, plasmaLaunch;
    private PlasmaManager plasma;
    private ScrollView outputScroll;
    private volatile boolean busy;
    private LinuxEngine engine;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new LinuxEngine(this);
        plasma = new PlasmaManager(this, engine);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(25));
        scroll.addView(root);
        text(root, "◉ DROIDDECK LITE", 15, accent, true);
        text(root, "Ubuntu sur Android", 27, Color.WHITE, true);
        text(root, "Linux ARM64 via PRoot • sans root", 13, 0xffacbacb, false);

        LinearLayout installPanel = panel(root);
        text(installPanel, "Environnement Linux", 21, Color.WHITE, true);
        state = text(installPanel, "", 15, 0xffd9e4f1, true);
        detail = text(installPanel, "", 13, 0xffb6c3d0, false);
        install = button(installPanel, "Installer Ubuntu", true, v -> installUbuntu());
        button(installPanel, "Vérifier l'installation", false, v -> refresh());

        LinearLayout desktopPanel = panel(root);
        text(desktopPanel, "Bureau KDE Plasma 6", 21, Color.WHITE, true);
        text(desktopPanel, "Bureau X11 sur Ubuntu via TigerVNC. Un visualiseur VNC Android est nécessaire pour voir le bureau.", 13, 0xffb6c3d0, false);
        plasmaInstall = button(desktopPanel, "Installer KDE Plasma (gros téléchargement)", true, v -> installPlasma());
        plasmaLaunch = button(desktopPanel, "Lancer le bureau Plasma", true, v -> launchPlasma());
        text(desktopPanel, "Adresse VNC : 127.0.0.1:5901 (connexion locale uniquement)", 13, 0xffb6c3d0, false);
        TextView passwordView = text(desktopPanel, "Mot de passe VNC : " + plasma.password(), 14, 0xffbde4ff, true);
        button(desktopPanel, "Copier l'adresse et le mot de passe", false, v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DroidDeck VNC", "Adresse : 127.0.0.1:5901\\nMot de passe : " + plasma.password()));
            Toast.makeText(this, "Coordonnées VNC copiées", Toast.LENGTH_SHORT).show();
        });
        button(desktopPanel, "Ouvrir une application VNC", false, v -> openViewer());
        button(desktopPanel, "Arrêter la session Plasma", false, v -> {
            engine.stop();
            append("Arrêt du processus PRoot demandé.\\n");
        });

        LinearLayout terminalPanel = panel(root);
        text(terminalPanel, "Terminal Ubuntu", 21, Color.WHITE, true);
        text(terminalPanel, "Exécute une commande dans Ubuntu. Les commandes peuvent accéder aux fichiers Linux ; utilise-les avec précaution.", 12, 0xffaebed0, false);
        command = new EditText(this);
        command.setSingleLine(true);
        command.setTextColor(Color.WHITE);
        command.setHintTextColor(0xff8192a6);
        command.setHint("Exemple : uname -a");
        command.setText("cat /etc/os-release");
        command.setTextSize(15);
        command.setTypeface(Typeface.MONOSPACE);
        terminalPanel.addView(command, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        terminalPanel.addView(actions);
        run = button(actions, "Exécuter", true, v -> execute());
        stop = button(actions, "Arrêter", false, v -> engine.stop());
        LinearLayout.LayoutParams runParams = (LinearLayout.LayoutParams)run.getLayoutParams();
        runParams.width = 0; runParams.weight = 1; run.setLayoutParams(runParams);
        LinearLayout.LayoutParams stopParams = (LinearLayout.LayoutParams)stop.getLayoutParams();
        stopParams.width = 0; stopParams.weight = 1; stop.setLayoutParams(stopParams);
        outputScroll = new ScrollView(this);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(270));
        op.topMargin = dp(12);
        terminalPanel.addView(outputScroll, op);
        output = new TextView(this);
        output.setTextSize(12);
        output.setTextColor(0xffd0e2f4);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        output.setText("Console prête. Installe Ubuntu puis exécute une commande.\n");
        output.setTextIsSelectable(true);
        output.setBackgroundColor(0xff080d15);
        outputScroll.addView(output);

        LinearLayout infoPanel = panel(root);
        text(infoPanel, "État de la version 0.3", 19, Color.WHITE, true);
        text(infoPanel, "✅ Installation Ubuntu Base ARM64\n✅ Téléchargement vérifié SHA-256\n✅ Lancement de commandes via PRoot\n✅ Journal et arrêt des commandes\n⏳ Terminal interactif complet (PTY)\n✅ Installation KDE Plasma X11 et serveur VNC\\n⏳ Affichage intégré dans l'APK (visualiseur VNC externe requis)\n⏳ FEX / Wine / Proton", 13, 0xffbecdde, false);
        setContentView(scroll);
        refresh();
    }

    private void refresh() {
        boolean installed = engine.isInstalled();
        state.setText(installed ? "Ubuntu installé" : "Ubuntu non installé");
        String binary = engine.prootReady() ? "PRoot ARM64 : présent" : "PRoot ARM64 : absent dans l'APK";
        detail.setText(binary + "\nArchive : " + (RuntimeArchive.archive(this).isFile() ? "disponible" : "absente") + "\nStockage : " + getFilesDir().getAbsolutePath());
        install.setText(installed ? "Réinstaller Ubuntu" : "Installer Ubuntu");
        run.setEnabled(installed && engine.prootReady() && !busy);
        plasmaInstall.setEnabled(installed && !busy);
        plasmaLaunch.setEnabled(installed && plasma.installed() && !busy);
    }

    private void installUbuntu() {
        if (busy) return;
        busy = true;
        install.setEnabled(false); run.setEnabled(false);
        output.setText("");
        append("Installation de Linux...\n");
        engine.install((message, progress, finished) -> runOnUiThread(() -> {
            state.setText(message);
            if (progress >= 0) detail.setText(String.format(Locale.FRANCE, "Progression : %d %%", progress));
            if (finished) {
                busy = false;
                append(message + "\n");
                refresh();
                if (!engine.isInstalled()) detail.setText(message + "\nVérifie la connexion et l'espace disponible.");
            }
        }));
    }
    private void execute() {
        if (busy || !engine.isInstalled()) return;
        String cmd = command.getText().toString().trim();
        if (cmd.isEmpty()) return;
        busy = true; run.setEnabled(false); install.setEnabled(false);
        append("\n$ " + cmd + "\n");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(command.getWindowToken(), 0);
        engine.execute(cmd, new LinuxEngine.ConsoleListener() {
            @Override public void line(String text) { runOnUiThread(() -> append(text + "\n")); }
            @Override public void done(int code) { runOnUiThread(() -> { append("[Terminé : code " + code + "]\n"); busy = false; install.setEnabled(true); refresh(); }); }
        });
    }
    private LinuxEngine.ConsoleListener listenerFor(String operation) {
        return new LinuxEngine.ConsoleListener() {
            @Override public void line(String line) { runOnUiThread(() -> append(line + "\\n")); }
            @Override public void done(int code) {
                runOnUiThread(() -> {
                    append(operation + " termine (code " + code + ").\\n");
                    busy = false;
                    install.setEnabled(true);
                    refresh();
                });
            }
        };
    }

    private void installPlasma() {
        if (busy || !engine.isInstalled()) return;
        busy = true;
        install.setEnabled(false);
        refresh();
        append("\\nInstallation de KDE Plasma depuis les dépôts Ubuntu...\\n");
        plasma.install(listenerFor("Installation Plasma"));
    }

    private void launchPlasma() {
        if (busy || !plasma.installed()) return;
        busy = true;
        install.setEnabled(false);
        refresh();
        append("\\nDémarrage de KDE Plasma via TigerVNC...\\n");
        append("Connecte un visualiseur VNC à 127.0.0.1:5901 avec le mot de passe indiqué.\\n");
        plasma.start(listenerFor("Session Plasma"));
    }

    private void openViewer() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("vnc://127.0.0.1:5901"));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Installe d'abord un visualiseur VNC puis connecte-toi à 127.0.0.1:5901", Toast.LENGTH_LONG).show();
        }
    }

    private void append(String line) {
        if (output.length() > 100000) output.setText("[Sortie précédente tronquée]\n");
        output.append(line);
        outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
    }
    private LinearLayout panel(LinearLayout root) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable draw = new GradientDrawable();
        draw.setColor(panel); draw.setCornerRadius(dp(15)); box.setBackground(draw);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(18); root.addView(box, lp);
        return box;
    }
    private TextView text(LinearLayout root, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(9)); root.addView(t); return t;
    }
    private Button button(LinearLayout root, String value, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(value); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? accent : 0xff39475d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(7); root.addView(b, lp); b.setOnClickListener(listener); return b;
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
