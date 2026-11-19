package com.jfdedit3.droiddeck;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class RuntimeArchive {
    // Pinned official Ubuntu Base 26.04.1 ARM64 image.
    private static final String URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz";
    private static final String SHA256 = "5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd";
    public interface Listener { void update(String message, int percent, boolean done); }
    public static File archive(Context c) { return new File(c.getFilesDir(), "ubuntu-base-arm64.tar.gz"); }
    public static File verifiedFlag(Context c) { return new File(c.getFilesDir(), "ubuntu-base-arm64.verified"); }

    public static boolean isVerified(Context c) {
        File f = archive(c), marker = verifiedFlag(c);
        if (!f.isFile() || f.length() == 0 || !marker.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(marker))) {
            return SHA256.equalsIgnoreCase(reader.readLine());
        } catch (IOException e) { return false; }
    }
    public static void download(Context context, Listener listener) {
        new Thread(() -> {
            File file = archive(context), temp = new File(file.getPath() + ".part");
            HttpURLConnection con = null;
            try {
                if (isVerified(context)) {
                    listener.update("Image déjà téléchargée", 100, true);
                    return;
                }
                con = (HttpURLConnection) new URL(URL).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                con.setInstanceFollowRedirects(true);
                if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
                long length = con.getContentLengthLong(), read = 0;
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new BufferedInputStream(con.getInputStream()); OutputStream out = new FileOutputStream(temp)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n); md.update(buf, 0, n); read += n;
                        int pct = length > 0 ? (int) Math.min(99, (read * 100) / length) : -1;
                        listener.update("Téléchargement Ubuntu", pct, false);
                    }
                }
                listener.update("Vérification SHA-256", -1, false);
                StringBuilder sha = new StringBuilder();
                for (byte b : md.digest()) sha.append(String.format(Locale.ROOT, "%02x", b & 0xff));
                if (!SHA256.equals(sha.toString())) throw new IOException("SHA-256 incorrect : téléchargement rejeté");
                if (file.exists() && !file.delete()) throw new IOException("Ancienne archive non supprimable");
                if (!temp.renameTo(file)) throw new IOException("Impossible de finaliser l'archive");
                try (FileWriter writer = new FileWriter(verifiedFlag(context))) {
                    writer.write(SHA256 + "\n");
                }
                listener.update("Image Ubuntu téléchargée et vérifiée", 100, true);
            } catch (Exception e) {
                temp.delete();
                listener.update("Erreur : " + e.getMessage(), -1, true);
            } finally { if (con != null) con.disconnect(); }
        }, "ubuntu-download").start();
    }
}
