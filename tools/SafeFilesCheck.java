import com.lotusblight.safety.SafeFiles;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-check for SafeFiles. Run with -Djava.io.tmpdir=<an empty scratch folder>; it only ever touches files it creates there.
 *   javac -d out src/main/java/com/lotusblight/safety/SafeFiles.java tools/SafeFilesCheck.java
 *   java -Djava.io.tmpdir=D:\tmp\sf-test -cp out SafeFilesCheck
 */
public class SafeFilesCheck {
    static int fails = 0;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) fails++;
    }

    static Path make(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
        return p;
    }

    public static void main(String[] a) throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        Path own = tmp.resolve("lotusblight");

        Path s1 = make(own.resolve("song1.wav"));
        check("own track in tmp/lotusblight is deleted", SafeFiles.deleteOwn(s1) && !Files.exists(s1));
        Path part = make(own.resolve("song2.wav.part"));
        check("half-written .part of a track is deleted", SafeFiles.deleteOwn(part) && !Files.exists(part));

        Path other = make(own.resolve("notes.txt"));
        check("unknown name inside our folder is refused", !SafeFiles.deleteOwn(other) && Files.exists(other));
        Path wrongDir = make(tmp.resolve("elsewhere").resolve("song1.wav"));
        check("track name in another folder is refused", !SafeFiles.deleteOwn(wrongDir) && Files.exists(wrongDir));
        Path tricky = make(tmp.resolve("song3.wav"));
        check("track name in tmp itself is refused", !SafeFiles.deleteOwn(tricky) && Files.exists(tricky));
        check("'..' escape is refused", !SafeFiles.deleteOwn(own.resolve("..").resolve("song3.wav")) && Files.exists(tricky));
        check("a folder is refused", !SafeFiles.deleteOwn(own) && Files.isDirectory(own));
        check("missing file is no error", !SafeFiles.deleteOwn(own.resolve("song9.wav")));
        check("null is no error", !SafeFiles.deleteOwn(null));

        Path video = make(tmp.resolve("drive").resolve(".lotusblight-cache").resolve("finale.mp4"));
        Path doc = make(tmp.resolve("drive").resolve(".lotusblight-cache").resolve("report.docx"));
        check("finale video in .lotusblight-cache is deleted", SafeFiles.deleteOwn(video) && !Files.exists(video));
        check("another file in .lotusblight-cache is refused", !SafeFiles.deleteOwn(doc) && Files.exists(doc));
        check("non-empty cache folder is kept", !SafeFiles.deleteEmptyCacheDir(doc.getParent()) && Files.isDirectory(doc.getParent()));
        Files.delete(doc);
        check("empty cache folder is removed", SafeFiles.deleteEmptyCacheDir(doc.getParent()) && !Files.exists(doc.getParent()));
        Path notCache = tmp.resolve("drive").resolve("Documents");
        Files.createDirectories(notCache);
        check("an empty folder with another name is refused", !SafeFiles.deleteEmptyCacheDir(notCache) && Files.isDirectory(notCache));

        check("record: taps file in song-test is allowed", SafeFiles.isTestRecordFile(tmp.resolve("song-test").resolve("taps2.txt")));
        check("record: any other path is refused", !SafeFiles.isTestRecordFile(tmp.resolve("Documents").resolve("taps2.txt")));
        check("record: non-.txt in song-test is refused", !SafeFiles.isTestRecordFile(tmp.resolve("song-test").resolve("a.docx")));

        try {
            Path target = make(tmp.resolve("victim").resolve("song1.wav"));
            Path link = own.resolve("song1.wav");
            Files.createDirectories(own);
            Files.createSymbolicLink(link, target);
            check("a link named like a track is refused and its target survives", !SafeFiles.deleteOwn(link) && Files.exists(target));
        } catch (Exception e) {
            System.out.println("SKIP  link test (no permission to create links here)");
        }
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
