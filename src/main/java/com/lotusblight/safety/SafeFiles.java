package com.lotusblight.safety;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The only door the mod's separate processes use to delete a file. It deletes the mod's own temporary files and nothing else.
 *
 * <p>Why a door at all: a path that arrives as a command-line argument is not proof that the file is ours. Everything here is
 * decided by the file's own name and the folder it sits in, never by "the caller said so". Links are never followed, a name
 * with a different folder is refused, and a refusal is logged and returns false (it never throws).
 *
 * <p>Pure Java on purpose: the overlay processes run without Minecraft.
 *
 * <p>Not covered here (by the project's rules): the player's own files and other mods' files are never deleted or moved by
 * the mod; a world is only ever removed by the ending's own, separate path (see the world rules), not through this class.
 */
public final class SafeFiles {
    private static final Logger LOG = Logger.getLogger("lotusblight.safety");

    public static final String MODID = "lotusblight";
    /** The folder the finale video lives in, on a drive with room (named exactly like this, anywhere). */
    public static final String CACHE_DIR = ".lotusblight-cache";
    /** The tracks the exit process plays, in the temp directory's own "lotusblight" folder. */
    private static final Set<String> TEMP_FILES = Set.of("song1.wav", "song2.wav", "song3.wav", "contact.wav", "interlude.wav");
    private static final Set<String> CACHE_FILES = Set.of("finale.mp4");
    private static final String PART = ".part";

    private SafeFiles() {}

    /**
     * Deletes one of our own temporary files: a known track in {tmp}/lotusblight, or the finale video in a .lotusblight-cache folder
     * (a half-written ".part" of either too). Anything else is refused.
     *
     * @return true only if a file was deleted
     */
    public static boolean deleteOwn(Path file) {
        try {
            if (file == null) return false;
            Path abs = file.toAbsolutePath().normalize();
            Path name = abs.getFileName(), parent = abs.getParent();
            if (name == null || parent == null) return refuse(file, "no folder");
            if (!Files.exists(abs, LinkOption.NOFOLLOW_LINKS)) return false;           // nothing there: nothing to do
            if (Files.isSymbolicLink(abs) || Files.isSymbolicLink(parent)) return refuse(file, "a link");
            if (!Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS)) return refuse(file, "not a plain file");
            String base = name.toString();
            if (base.endsWith(PART)) base = base.substring(0, base.length() - PART.length());
            boolean ours = (TEMP_FILES.contains(base) && isOwnTempDir(parent))
                    || (CACHE_FILES.contains(base) && parent.getFileName() != null && CACHE_DIR.equals(parent.getFileName().toString()));
            if (!ours) return refuse(file, "not one of our own files");
            Files.delete(abs);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warning("SafeFiles: could not delete " + file + ": " + e);
            return false;
        }
    }

    /** Removes a .lotusblight-cache folder, but only if it is empty (so nothing else that happens to be in it is ever lost). */
    public static boolean deleteEmptyCacheDir(Path dir) {
        try {
            if (dir == null) return false;
            Path abs = dir.toAbsolutePath().normalize();
            Path name = abs.getFileName();
            if (name == null || !CACHE_DIR.equals(name.toString())) return refuse(dir, "not our cache folder");
            if (Files.isSymbolicLink(abs) || !Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS)) return false;
            try (DirectoryStream<Path> in = Files.newDirectoryStream(abs)) {
                if (in.iterator().hasNext()) return false;                           // something is in it: leave it
            }
            Files.delete(abs);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warning("SafeFiles: could not remove " + dir + ": " + e);
            return false;
        }
    }

    /** The developer's --record option may only touch a .txt file inside a folder named "song-test" (the test scripts' folder). */
    public static boolean isTestRecordFile(Path file) {
        if (file == null) return false;
        Path abs = file.toAbsolutePath().normalize();
        Path name = abs.getFileName(), parent = abs.getParent();
        if (name == null || parent == null || parent.getFileName() == null) return false;
        if (Files.isSymbolicLink(abs) || Files.isSymbolicLink(parent)) return false;
        return name.toString().endsWith(".txt") && parent.getFileName().toString().equals("song-test");
    }

    private static boolean isOwnTempDir(Path dir) throws IOException {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.isEmpty()) return false;
        return dir.toRealPath().equals(Path.of(tmp).toRealPath().resolve(MODID));
    }

    private static boolean refuse(Path p, String why) {
        LOG.warning("SafeFiles: refused to delete " + p + " (" + why + ")");
        return false;
    }
}
