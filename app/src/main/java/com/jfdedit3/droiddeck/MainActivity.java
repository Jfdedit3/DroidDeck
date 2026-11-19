package com.jfdedit3.droiddeck;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(10, 14, 23);
    private final int panel = Color.rgb(25, 33, 46);
    private final int accent = Color.rgb(39, 157, 240);
    private TextView state, detail;
    private Button install;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        scroll.addView(root);
        addText(root, "◉  DROIDDECK LITE", 16, accent, true);
        addText(root, "Votre espace Linux", 30, Color.WHITE, true);
        addText(root, "Version indépendante • Android ARM64", 14, 0xffaab8ca, false);
        LinearLayout box = panel(root);
        addText(box, "Environnement Linux", 22, Color.WHITE, true);
        state = addText(box, "", 15, 0xffaab8ca, false);
        detail = addText(box, "Ubuntu Base ARM64 • image officielle", 13, 0xffaab8ca, false);
        install = button(box, "Télécharger le runtime Ubuntu", true, v -> startDownload());
        button(box, "Supprimer l'archive locale", false, v -> {
            File f = RuntimeArchive.archive(this);
            if (f.exists() && !f.delete()) detail.setText("Suppression impossible.");
            else detail.setText("Archive supprimée.");
            refresh();
        });
        LinearLayout modes = panel(root);
        addText(modes, "Modes", 22, Color.WHITE, true);
        button(modes, "Bureau Plasma — bientôt", false, v ->
            detail.setText("Le lancement Linux nécessite un moteur PRoot natif, non encore intégré."));
        button(modes, "Jeux PC / Proton — bientôt", false, v ->
            detail.setText("Wine, FEX et Proton ne font pas partie de cette première version."));
        LinearLayout about = panel(root);
        addText(about, "Projet", 22, Color.WHITE, true);
        addText(about, "Base Android reconstruite indépendamment. Téléchargement vérifié par SHA-256, sans root. Le lancement Linux et le bureau ne sont pas encore opérationnels.", 14, 0xffc0cad8, false);
        button(about, "Ouvrir GitHub", false, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jfdedit3/DroidDeck"))));
        setContentView(scroll);
        refresh();
    }

    private void startDownload() {
        install.setEnabled(false);
        state.setText("Téléchargement en cours...");
        detail.setText("Connexion au serveur Ubuntu...");
        RuntimeArchive.download(this, (message, progress, done) -> runOnUiThread(() -> {
            state.setText(message);
            detail.setText(progress >= 0 ? String.format(Locale.FRANCE, "%d %%", progress) : "Vérification / connexion");
            if (done) { install.setEnabled(true); refresh(); if (!RuntimeArchive.archive(this).isFile()) detail.setText(message); }
        }));
    }
    private void refresh() {
        File f = RuntimeArchive.archive(this);
        boolean ready = f.isFile() && RuntimeArchive.verifiedFlag(this).isFile();
        state.setText(ready ? "Image Ubuntu vérifiée et téléchargée" : "Image Ubuntu absente");
        install.setText(ready ? "Télécharger à nouveau" : "Télécharger le runtime Ubuntu");
    }
    private LinearLayout panel(LinearLayout root) {
        LinearLayout v = new LinearLayout(this);
        v.setPadding(dp(18), dp(16), dp(18), dp(18));
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(panel); drawable.setCornerRadius(dp(18));
        v.setBackground(drawable);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(22); root.addView(v, lp);
        return v;
    }
    private TextView addText(LinearLayout parent, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(5), 0, dp(9));
        parent.addView(t); return t;
    }
    private Button button(LinearLayout parent, String value, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? accent : 0xff39465b));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(10); parent.addView(b, lp);
        b.setOnClickListener(listener); return b;
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
