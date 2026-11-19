package com.jfdedit3.droiddeck;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

/**
 * Official DroidDeck Ubuntu 26.04 ARM64 + Kubuntu Plasma desktop pack.
 *
 * Both archives belong to the SAME pinned upstream release. They cannot be
 * applied to the previous plain Ubuntu Base rootfs; create a new staging
 * filesystem and atomically swap it once both archives are verified/extracted.
 */
public final class DroidDeckRuntimeInstaller {
    public interface Listener { void update(String message, int percent, boolean finished); }

    private static final String TAG = "DroidDeckInstaller";
    private static final String VERSION = "ubuntu-r1-rc5";
    private static final String RELEASE = "https://github.com/Droid-Deck/DroidDeck-Components/releases/download/" + VERSION + "/";
    private static final String[] NAMES = {
        "ubuntu-r1-rc5.tar.zst",
        "ubuntu-r1-rc5-desktop.tar.zst"
    };
    private static final String[] SHAS = {
        "434d3d91f9c93d94deebbbdc57a1b3763443f785925393f15db19feed477e030",
        "e676968a8dc5f3817061731008f877be860e29cc6d017b3170d192513d584de2"
    };
    private static final long[] SIZES = { 291553163L, 898306226L };
    private static final String MARK = ".droiddeck-official-plasma";
    private final Context context;
    private volatile boolean running;
    public DroidDeckRuntimeInstaller(Context c) { context = c.getApplicationContext(); }
    public File installedRoot() { return new File(context.getFilesDir(), "ubuntu"); }
    public boolean installed() {
        File f = installedRoot();
        return new File(f, MARK).isFile() && new File(f, "usr/bin/kwin_wayland").exists()
            && new File(f, "usr/share/droiddeck/desktop").exists();
    }
    public synchronized void install(Listener l) {
        if (running) { l.update("Installation déjà en cours", -1, true); return; }
        running = true;
        new Thread(() -> {
            try {
                if (!new File(context.getApplicationInfo().nativeLibraryDir, "libproot.so").isFile())
                    throw new IOException("PRoot ARM64 absent de l'APK");
                File archivesFolder = new File(context.getFilesDir(), "droiddeck-archives");
                if (!archivesFolder.isDirectory() && !archivesFolder.mkdirs())
                    throw new IOException("Impossible de créer le stockage des archives");
                long required = SIZES[0] + SIZES[1] + 2L * 1024L * 1024L * 1024L;
                long available = android.os.StatFs.class.cast(new android.os.StatFs(context.getFilesDir().getPath())).getAvailableBytes();
                if (available < required)
                    throw new IOException("Espace insuffisant : " + (required / (1024 * 1024)) +
                        " Mo recommandés libres, disponibles " + (available / (1024 * 1024)) + " Mo");
                File[] archives = new File[2];
                for (int i = 0; i < 2; i++) {
                    final int part = i;
                    File destination = new File(archivesFolder, NAMES[i]);
                    archives[i] = destination;
                    if (!checkSha(destination, SHAS[i], SIZES[i])) {
                        l.update(i == 0 ? "Téléchargement Ubuntu DroidDeck" : "Téléchargement Plasma DroidDeck", 0, false);
                        fetch(RELEASE + NAMES[i], destination, SHAS[i], SIZES[i],
                            pct -> l.update(part == 0 ? "Téléchargement Ubuntu DroidDeck" : "Téléchargement Plasma DroidDeck", pct, false));
                    } else {
                        l.update("Archive " + NAMES[i] + " vérifiée", 100, false);
                    }
                }
                File root = installedRoot();
                File stage = new File(context.getFilesDir(), "ubuntu.droiddeck.new");
                File old = new File(context.getFilesDir(), "ubuntu.before-droiddeck");
                if (stage.exists()) remove(stage);
                if (!stage.mkdirs()) throw new IOException("Impossible de créer le dossier de préparation");
                try {
                    for (int i = 0; i < archives.length; i++) {
                        final int idx = i;
                        l.update(i == 0 ? "Extraction Ubuntu DroidDeck..." : "Installation du pack Plasma...", -1, false);
                        extract(archives[i], stage, count -> {
                            if (count % 1500 == 0)
                                l.update((idx == 0 ? "Ubuntu : " : "Plasma : ") + count + " fichiers", -1, false);
                        });
                    }
                    // The desktop pack ships a dpkg database and manifest. Import
                    // exactly onto the paired runtime; do not run APT here.
                    if (!new File(stage, "usr/share/droiddeck/desktop/files.tsv.gz").isFile())
                        throw new IOException("Le pack Plasma officiel est incomplet (manifest absent)");
                    if (!new File(stage, "usr/bin/kwin_wayland").exists())
                        throw new IOException("KWin absent du bureau Plasma");
                    if (!new File(stage, "usr/bin/sh").exists() && !new File(stage, "bin/sh").exists())
                        throw new IOException("Shell Ubuntu absent");
                    new File(stage, "root").mkdirs();
                    try (FileWriter writer = new FileWriter(new File(stage, MARK))) {
                        writer.write(VERSION + "\n");
                    }
                    // The old rootfs may be broken; keep it until the verified
                    // install is active. Never overwrite a previous backup.
                    if (old.exists()) {
                        // A backup from an earlier interrupted swap is retained.
                        throw new IOException("Sauvegarde Ubuntu existante : " + old.getName() +
                            ". Ne la supprime pas sans vérifier les données.");
                    }
                    if (root.exists() && !root.renameTo(old))
                        throw new IOException("Impossible de sauvegarder l'ancien Ubuntu");
                    if (!stage.renameTo(root)) {
                        if (old.exists()) old.renameTo(root);
                        throw new IOException("Impossible d'activer le runtime DroidDeck");
                    }
                } finally {
                    if (stage.exists()) remove(stage);
                }
                l.update("Ubuntu + Plasma officiels de DroidDeck installés (" + VERSION + ")", 100, true);
            } catch (Throwable e) {
                Log.e(TAG, "Official runtime installation", e);
                l.update("Échec installation DroidDeck : " + e.getMessage(), -1, true);
            } finally {
                running = false;
            }
        }, "droiddeck-install").start();
    }
    private interface Percent { void update(int progress); }
    private interface Count { void update(int count); }

    private static void fetch(String url, File target, String sha, long size, Percent listener) throws Exception {
        File temp = new File(target.getAbsolutePath() + ".part");
        // Resume downloads using HTTP range if the upstream server supports it.
        long resume = temp.isFile() ? temp.length() : 0;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(25000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "DroidDeck-Lite/0.4");
        if (resume > 0 && resume < size) connection.setRequestProperty("Range", "bytes=" + resume + "-");
        int response = connection.getResponseCode();
        boolean append = response == 206 && resume > 0;
        if (response != 200 && response != 206) throw new IOException("HTTP " + response + " (" + url + ")");
        if (!append) resume = 0;
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(temp, append))) {
            byte[] buffer = new byte[128 * 1024];
            int n; long count = resume; long last = -1;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                count += n;
                int pct = (int) Math.min(99, count * 100L / size);
                if (pct != last) { listener.update(pct); last = pct; }
            }
        } finally { connection.disconnect(); }
        if (!checkSha(temp, sha, size)) throw new IOException("SHA-256 incorrect : " + target.getName());
        if (target.exists() && !target.delete()) throw new IOException("Ancienne archive non supprimable");
        if (!temp.renameTo(target)) throw new IOException("Échec de finalisation du téléchargement");
        listener.update(100);
    }

    private static boolean checkSha(File file, String expected, long size) throws Exception {
        if (!file.isFile() || file.length() != size) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] bytes = new byte[128 * 1024]; int read;
            while ((read = in.read(bytes)) != -1) digest.update(bytes, 0, read);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : digest.digest()) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString().equalsIgnoreCase(expected);
    }

    private static void extract(File archive, File target, Count progress) throws Exception {
        Path root = target.getCanonicalFile().toPath();
        ArrayList<String[]> hardLinks = new ArrayList<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new ZstdCompressorInputStream(new BufferedInputStream(new FileInputStream(archive))))) {
            TarArchiveEntry entry; int count = 0;
            while ((entry = tar.getNextTarEntry()) != null) {
                String rel = entry.getName();
                if (rel == null || rel.equals(".") || rel.equals("./") || rel.isEmpty()) continue;
                if (rel.startsWith("/")) throw new IOException("Entrée absolue dans archive : " + rel);
                Path destination = root.resolve(rel).normalize();
                if (!destination.startsWith(root)) throw new IOException("Sortie du rootfs : " + rel);
                // Refuse to traverse previously extracted symbolic links.
                Path walk = root;
                for (Path p : root.relativize(destination.getParent())) {
                    walk = walk.resolve(p);
                    if (Files.isSymbolicLink(walk))
                        throw new IOException("Chemin symbolique dangereux dans l'archive : " + rel);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else if (entry.isSymbolicLink()) {
                    Files.createDirectories(destination.getParent());
                    Files.deleteIfExists(destination);
                    Files.createSymbolicLink(destination, Paths.get(entry.getLinkName()));
                } else if (entry.isLink()) {
                    hardLinks.add(new String[] {rel, entry.getLinkName()});
                } else if (entry.isFile()) {
                    Files.createDirectories(destination.getParent());
                    Files.deleteIfExists(destination);
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = tar.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    File f = destination.toFile();
                    f.setReadable(true, false);
                    f.setWritable(true, true);
                    f.setExecutable((entry.getMode() & 0111) != 0, false);
                }
                progress.update(++count);
            }
        }
        for (String[] item : hardLinks) {
            Path destination = root.resolve(item[0]).normalize();
            Path source = root.resolve(item[1]).normalize();
            if (!destination.startsWith(root) || !source.startsWith(root))
                throw new IOException("Lien dur hors du runtime");
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Cible du lien dur introuvable : " + item[1]);
            Files.createDirectories(destination.getParent());
            Files.deleteIfExists(destination);
            try { Files.createLink(destination, source); }
            catch (IOException | UnsupportedOperationException e) { Files.copy(source, destination); }
        }
    }
    private static void remove(File dir) throws IOException {
        if (!dir.exists()) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(dir.toPath())) {
            for (Path p : (Iterable<Path>)stream.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(p);
        }
    }
}
