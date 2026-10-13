package com.lotusblight.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * The reward of the long fight, for a player who agreed to it in the warning before the minigame: the special files, the "ending passed"
 * state and a restart of the computer, once. A player who refused gets none of it (and this class does nothing for them).
 *
 * Everything lives in {@code <user home>/NotYourScreen}: {@code ending.state} (the choice and the flags) and {@code SpecialFiles/}.
 * Nothing outside that folder is written, nothing is deleted, an existing file is never overwritten. The restart is the ordinary
 * {@code shutdown /r /t 0} WITHOUT /f, so a program with unsaved work gets to ask the player (a forced restart would lose it).
 */
final class EndingRewards {
    /** The files to hand out sit in the jar under this folder. */
    static final String RESOURCE_DIR = "/assets/lotusblight/ending_files/";

    private static final Path ROOT = Path.of(System.getProperty("user.home"), "NotYourScreen");
    private static final Path FILES_DIR = ROOT.resolve("SpecialFiles");
    private static final Path STATE = ROOT.resolve("ending.state");

    private EndingRewards() {}

    // ------------------------------------------------------------------ the state

    private static synchronized Properties load() {
        Properties p = new Properties();
        if (Files.isRegularFile(STATE)) {
            try (Reader in = Files.newBufferedReader(STATE, StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException ignored) {
                // an unreadable state counts as empty: nothing is given out
            }
        }
        return p;
    }

    private static synchronized void store(Properties p) throws IOException {
        Files.createDirectories(ROOT);
        Path temp = Files.createTempFile(ROOT, "ending-", ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                p.store(out, "Lotus Blight: the long fight. Safe to delete (then nothing is given out).");
            }
            try {
                Files.move(temp, STATE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, STATE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean flag(Properties p, String key) {
        return "true".equals(p.getProperty(key));
    }

    /** The warning before the minigame: remembers whether the player agreed. */
    static void recordChoice(boolean accepted) throws IOException {
        Properties p = load();
        p.setProperty("accepted", Boolean.toString(accepted));
        store(p);
    }

    static boolean accepted() {
        return flag(load(), "accepted");
    }

    /** True once the files are handed out (the ending is passed and kept). */
    static boolean prepared() {
        return flag(load(), "prepared");
    }

    // ------------------------------------------------------------------ the files

    /**
     * Copies the named jar resources into {@code SpecialFiles/} and checks each copy (SHA-256). Only for a player who agreed. A file that is
     * already there is left alone; if a different one has the same name, the new one gets a numbered name. Returns false when the player
     * did not agree (nothing is written).
     */
    static boolean handOutFiles(List<String> names) throws IOException {
        Properties p = load();
        if (!flag(p, "accepted")) return false;
        Files.createDirectories(FILES_DIR);
        for (String name : names) {
            String clean = Path.of(name).getFileName() == null ? "" : Path.of(name).getFileName().toString();
            if (clean.isBlank() || !clean.equals(name)) throw new IOException("Bad file name: " + name);
            byte[] data;
            try (InputStream in = EndingRewards.class.getResourceAsStream(RESOURCE_DIR + name)) {
                if (in == null) throw new IOException("Not in the jar: " + name);
                data = in.readAllBytes();
            }
            String hash = sha256(data);
            Path target = FILES_DIR.resolve(name);
            for (int i = 1; Files.exists(target); i++) {
                if (sha256(Files.readAllBytes(target)).equals(hash)) {
                    target = null;                       // the same file is already there
                    break;
                }
                int dot = name.lastIndexOf('.');
                target = FILES_DIR.resolve(dot > 0 ? name.substring(0, dot) + " (" + i + ")" + name.substring(dot) : name + " (" + i + ")");
            }
            if (target == null) continue;
            Files.write(target, data, java.nio.file.StandardOpenOption.CREATE_NEW);
            if (!sha256(Files.readAllBytes(target)).equals(hash)) throw new IOException("The copy does not match: " + name);
        }
        p.setProperty("prepared", "true");
        store(p);
        return true;
    }

    // ------------------------------------------------------------------ the restart, once

    /**
     * Asks Windows for the ordinary restart, once. The "asked" mark is written BEFORE the command, so a crash cannot make it a second time.
     * Not for a player who refused, not before the files are handed out, not off Windows. Returns whether the command was issued.
     */
    static boolean restartOnce() throws IOException {
        Properties p = load();
        if (!flag(p, "accepted") || !flag(p, "prepared") || flag(p, "restartAsked")) return false;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        p.setProperty("restartAsked", "true");
        store(p);
        Process process = new ProcessBuilder("shutdown.exe", "/r", "/t", "0").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    /**
     * After the restart, when the game starts again: true exactly once for a player who agreed and got the files (the mark is written first),
     * so the message is shown one time and never again.
     */
    static boolean showMessageOnce() throws IOException {
        Properties p = load();
        if (!flag(p, "accepted") || !flag(p, "prepared") || flag(p, "messageShown")) return false;
        p.setProperty("messageShown", "true");
        store(p);
        return true;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
