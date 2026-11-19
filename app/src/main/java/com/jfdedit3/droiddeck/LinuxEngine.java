package com.jfdedit3.droiddeck;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class LinuxEngine {
    public interface Progress { void update(String stage, int percent, boolean done); }
    public interface ConsoleListener { void line(String text); void done(int code); }
    private final Context context;
    private volatile Process process;
    private final AtomicBoolean installing = new AtomicBoolean(false);
    private static final String MARKER = ".droiddeck-installed-v2";
    public LinuxEngine(Context context) { this.context = context.getApplicationContext(); }
    public File root() { return new File(context.getFilesDir(), "ubuntu"); }
    public boolean isInstalled() { return new File(root(), MARKER).isFile() && new File(root(), "bin/sh").exists(); }
    public boolean prootReady() {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libproot.so").isFile()
            && new File(context.getApplicationInfo().nativeLibraryDir, "libproot-loader.so").isFile();
    }
    public void install(Progress callback) {
        if (!installing.compareAndSet(false, true)) { callback.update("Installation déjà en cours", -1, true); return; }
        new Thread(() -> {
            try {
                if (!prootReady()) throw new IOException("PRoot ARM64 manquant dans l'APK");
                File archive = RuntimeArchive.archive(context);
                if (!RuntimeArchive.isVerified(context)) {
                    final String[] error = { null };
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    RuntimeArchive.download(context, (message, pct, done) -> {
                        callback.update(message, pct, false);
                        if (done) { if (!RuntimeArchive.isVerified(context)) error[0] = message; latch.countDown(); }
                    });
                    latch.await();
                    if (error[0] != null) throw new IOException(error[0]);
                }
                if (!archive.isFile()) throw new IOException("Archive Ubuntu absente");
                File target = root();
                File staging = new File(context.getFilesDir(), "ubuntu.new");
                if (staging.exists()) deleteTree(staging);
                if (!staging.mkdirs()) throw new IOException("Impossible de préparer l'installation");
                try {
                    extract(archive, staging, callback);
                    if (!new File(staging, "bin/sh").exists() && !new File(staging, "usr/bin/sh").exists())
                        throw new IOException("L'archive Ubuntu ne contient pas de shell");
                    new File(staging, "root").mkdirs();
                    Files.write(new File(staging, MARKER).toPath(), "ubuntu-base-26.04.1\n".getBytes(StandardCharsets.UTF_8));
                    // Preserve the existing installation if staging or extraction fails.
                    File previous = new File(context.getFilesDir(), "ubuntu.old");
                    if (previous.exists()) deleteTree(previous);
                    if (target.exists() && !target.renameTo(previous)) throw new IOException("Impossible de préparer la mise à jour");
                    if (!staging.renameTo(target)) {
                        if (previous.exists()) previous.renameTo(target);
                        throw new IOException("Impossible d'activer Ubuntu");
                    }
                    if (previous.exists()) deleteTree(previous);
                } finally {
                    if (staging.exists()) deleteTree(staging);
                }
                callback.update("Ubuntu installé. Terminal disponible.", 100, true);
            } catch (Exception e) {
                callback.update("Échec installation : " + e.getMessage(), -1, true);
            } finally { installing.set(false); }
        }, "linux-install").start();
    }
    private void extract(File archive, File root, Progress callback) throws IOException {
        Path base = root.getCanonicalFile().toPath();
        ArrayList<String[]> hardlinks = new ArrayList<>();
        long total = Math.max(1, archive.length()), progress = 0;
        try (InputStream in = new FileInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(in));
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            int count = 0;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/")) throw new IOException("Chemin absolu dans l'archive");
                Path destination = base.resolve(name).normalize();
                if (!destination.startsWith(base) || destination.equals(base)) continue;
                Path parent = destination.getParent();
                // Never extract through a symlink from a prior archive entry.
                Path walk = base;
                for (Path segment : base.relativize(parent)) {
                    walk = walk.resolve(segment);
                    if (Files.isSymbolicLink(walk)) throw new IOException("Extraction par lien symbolique rejetée : " + name);
                }
                Files.createDirectories(parent);
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else if (entry.isSymbolicLink()) {
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) Files.delete(destination);
                    Files.createSymbolicLink(destination, Paths.get(entry.getLinkName()));
                } else if (entry.isLink()) {
                    hardlinks.add(new String[]{name, entry.getLinkName()});
                } else if (entry.isFile()) {
                    Files.deleteIfExists(destination);
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = tar.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    destination.toFile().setExecutable((entry.getMode() & 0111) != 0, false);
                    destination.toFile().setReadable(true, false);
                }
                if (++count % 400 == 0) callback.update("Extraction Ubuntu (" + count + " fichiers)", -1, false);
            }
        }
        for (String[] pair : hardlinks) {
            Path dest = base.resolve(pair[0]).normalize(), source = base.resolve(pair[1]).normalize();
            if (!dest.startsWith(base) || !source.startsWith(base)) throw new IOException("Lien dur hors du rootfs");
            Files.createDirectories(dest.getParent());
            // Link to regular source only; never follow archive-generated symlinks.
            if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(dest);
                try { Files.createLink(dest, source); }
                catch (UnsupportedOperationException | IOException e) { Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }
    private void deleteTree(File folder) throws IOException {
        if (!folder.exists()) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(folder.toPath())) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }

    /**
     * Ubuntu 26.04 ships Rust/uutils multi-call coreutils. Older PRoot loaders
     * expose the loader name in AT_EXECFN, breaking ls, mkdir, env, etc.
     * Ubuntu keeps the GNU equivalents prefixed by "gnu" (gnuls, gnumkdir).
     * Prepare PATH shims on the host filesystem; do not overwrite dpkg-owned
     * /usr/bin links. This also repairs installations already extracted.
     */
    private void prepareGnuCoreutils() throws IOException {
        Path usrBin = new File(root(), "usr/bin").toPath();
        Path shims = new File(root(), "usr/local/bin").toPath();
        Files.createDirectories(shims);
        int linked = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(usrBin, "gnu*")) {
            for (Path source : files) {
                String name = source.getFileName().toString();
                if (!name.startsWith("gnu") || name.length() <= 3 || !Files.isRegularFile(source)) continue;
                String original = name.substring(3);
                Path existing = usrBin.resolve(original);
                if (!Files.isSymbolicLink(existing)) continue;
                String link = Files.readSymbolicLink(existing).toString();
                if (!link.contains("coreutils")) continue;
                Path shim = shims.resolve(original);
                if (Files.exists(shim, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createSymbolicLink(shim, Paths.get("/usr/bin/" + name));
                linked++;
            }
        }
        android.util.Log.i("LinuxEngine", "GNU coreutils compatibility shims: " + linked);
    }


    /** Sync Android DNS into the app-private Ubuntu rootfs before running PRoot. */
    private void prepareDns() throws IOException {
        java.util.LinkedHashSet<String> servers = new java.util.LinkedHashSet<>();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.Network active = cm.getActiveNetwork();
                android.net.LinkProperties props = active == null ? null : cm.getLinkProperties(active);
                if (props != null) {
                    for (java.net.InetAddress address : props.getDnsServers()) {
                        String host = address.getHostAddress();
                        if (host != null && !host.contains("%") && !address.isAnyLocalAddress()
                                && !address.isLoopbackAddress()) servers.add(host);
                    }
                }
            }
        } catch (Exception error) {
            android.util.Log.w("LinuxEngine", "Cannot read Android DNS", error);
        }
        if (servers.isEmpty()) {
            servers.add("1.1.1.1");
            servers.add("8.8.8.8");
        }
        Path etc = new File(root(), "etc").toPath();
        Files.createDirectories(etc);
        Path resolver = etc.resolve("resolv.conf");
        if (Files.isSymbolicLink(resolver)) Files.delete(resolver);
        StringBuilder config = new StringBuilder("# DNS from Android").append((char) 10);
        for (String server : servers) config.append("nameserver ").append(server).append((char) 10);
        config.append("options timeout:3 attempts:2").append((char) 10);
        Files.write(resolver, config.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void execute(String command, ConsoleListener callback) {
        if (process != null && process.isAlive()) { callback.line("Une autre commande est en cours"); callback.done(-1); return; }
        new Thread(() -> {
            int exitCode = -1;
            try {
                if (!isInstalled() || !prootReady()) throw new IOException("Ubuntu / PRoot non installé");
                prepareGnuCoreutils();
                prepareDns();
                String nativeDir = context.getApplicationInfo().nativeLibraryDir;
                String proot = nativeDir + "/libproot.so";
                String loader = nativeDir + "/libproot-loader.so";
                File temp = new File(context.getCacheDir(), "proot-tmp"); temp.mkdirs();
                ArrayList<String> args = new ArrayList<>();
                // Android's system linker can execute native .so files from the APK.
                if (Build.VERSION.SDK_INT >= 29) args.add("/system/bin/linker64");
                args.add(proot);
                Collections.addAll(args, "-0", "-r", root().getAbsolutePath(), "-w", "/root",
                    "-b", "/dev", "-b", "/proc", "-b", "/sys");
                File external = Environment.getExternalStorageDirectory();
                if (external.isDirectory()) Collections.addAll(args, "-b", external.getAbsolutePath() + ":/sdcard");
                Collections.addAll(args, "/bin/sh", "-c", command);
                ProcessBuilder pb = new ProcessBuilder(args);
                pb.directory(context.getFilesDir());
                pb.redirectErrorStream(true);
                pb.environment().put("PROOT_LOADER", loader);
                pb.environment().put("HOME", "/root");
                pb.environment().put("USER", "root");
                pb.environment().put("LANG", "C.UTF-8");
                pb.environment().put("TERM", "xterm-256color");
                pb.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
                pb.environment().put("PROOT_TMP_DIR", temp.getAbsolutePath());
                pb.environment().put("TMPDIR", temp.getAbsolutePath());
                process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) callback.line(line);
                }
                exitCode = process.waitFor();
            } catch (Exception e) { callback.line("Erreur PRoot : " + e.getMessage()); }
            finally { process = null; callback.done(exitCode); }
        }, "linux-command").start();
    }
    public void stop() {
        Process p = process;
        if (p != null) p.destroy();
    }
}
