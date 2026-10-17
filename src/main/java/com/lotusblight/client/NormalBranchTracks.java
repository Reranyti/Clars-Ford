package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the exit process needs from the jar, put where it can use it. The fight's two tracks, and Honcho's
 * track for the lesson, ship as Ogg Vorbis, which plain Java can't play, so they are decoded to WAV with the stb_vorbis the game already
 * carries; the video decoder's libraries are copied out as they are (the video itself is read straight
 * from the jar by the exit process). All of it goes to our own folder under the system temp directory -
 * nothing else is written anywhere - and each file appears only once complete (written under a temporary
 * name, then moved into place).
 */
final class NormalBranchTracks {
    private static final Logger LOG = LogUtils.getLogger();
    /**
     * The third song and the cutscene before it are being made again from the start: until the new ones exist they are neither read from the jar
     * nor decoded (that was ~65 MB of WAV written to the temp folder at every start), and the fight ends after the second song.
     */
    private static final boolean SONG3_AND_INTERLUDE = false;
    private static final String[] SOURCES = {"overlay/map_1.ogg", "overlay/map_2.ogg", "overlay/honcho_briefing.ogg", "overlay/song3.ogg", "overlay/interlude.ogg"};
    private static final String[] OUTPUTS = {"song1.wav", "song2.wav", "contact.wav", "song3.wav", "interlude.wav"};
    private static volatile boolean hasContact, hasSong3, hasInterlude;
    /** The exit process's libraries, shipped as plain files in the jar - keep in step with build.gradle. */
    private static final String[] LIBS = {"jcodec-0.2.5.jar", "jcodec-javase-0.2.5.jar"};

    private NormalBranchTracks() {}

    /** Where the tracks go: song1.wav and song2.wav in this folder. */
    static Path folder() {
        return Path.of(System.getProperty("java.io.tmpdir"), LotusBlight.MODID);
    }

    static Path song(int n) {
        return folder().resolve("song" + n + ".wav");
    }

    /** Honcho's track for the lesson (the third one); only there if the jar was built with it. */
    static Path contact() {
        return folder().resolve("contact.wav");
    }

    /** The third song (only there if the jar was built with it). */
    static Path song3() {
        return folder().resolve("song3.wav");
    }

    /** The music of the cutscene between the second and third songs (only there if the jar carries it). */
    static Path interlude() {
        return folder().resolve("interlude.wav");
    }

    static boolean hasInterlude() {
        return hasInterlude;
    }

    static boolean hasSong3() {
        return hasSong3;
    }

    static boolean hasContact() {
        return hasContact;
    }

    /**
     * Copies the exit process's libraries out of the jar (small, done right here - they must be in
     * place before the process starts) and returns them as classpath entries. Missing ones are skipped.
     */
    static List<String> libraries(Minecraft mc) {
        List<String> paths = new ArrayList<>();
        for (String lib : LIBS) {
            Path out = folder().resolve("lib").resolve(lib);
            try {
                Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(LotusBlight.MODID, "overlay/lib/" + lib));
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    copy(in, out);
                }
                paths.add(out.toString());
            } catch (IOException e) {
                LOG.warn("Нормальная_ветка: can't unpack {}: {}", lib, e.toString());
            }
        }
        return paths;
    }

    /**
     * Reads both tracks out of the mod's resources (on the calling thread - the game thread) and decodes
     * them on a background thread. Returns right away.
     */
    static void prepare(Minecraft mc) {
        byte[][] ogg = new byte[SOURCES.length][];
        for (int i = 0; i < SOURCES.length; i++) {
            if (!SONG3_AND_INTERLUDE && i >= 3) continue;
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(LotusBlight.MODID, SOURCES[i]));
            if (res.isEmpty()) {
                if (i < 2) LOG.warn("Нормальная_ветка: {} is missing", SOURCES[i]);
                continue;
            }
            try (InputStream in = res.get().open()) {
                ogg[i] = in.readAllBytes();
                if (i == 2) hasContact = true;
                if (i == 3) hasSong3 = true;
                if (i == 4) hasInterlude = true;
            } catch (IOException e) {
                LOG.warn("Нормальная_ветка: can't read {}: {}", SOURCES[i], e.toString());
            }
        }
        cancelled = false;
        Thread worker = new Thread(() -> {
            for (int i = 0; i < ogg.length; i++) {
                if (ogg[i] == null || cancelled) continue;
                try {
                    writeWav(ogg[i], folder().resolve(OUTPUTS[i]));
                } catch (Exception e) {
                    LOG.warn("Нормальная_ветка: can't decode {}: {}", SOURCES[i], e.toString());
                }
            }
        }, "LotusBlight tracks");
        worker.setDaemon(true);
        worker.start();
    }

    private static volatile boolean cancelled;

    /**
     * Called when the exit process is over: stops the decoding (if it is still going) and removes our folder in the temp directory
     * - the tracks, the unpacked libraries and any half-written file - so nothing of ours is left behind however the fight ended.
     */
    static void cleanup() {
        cancelled = true;
        try {
            Path dir = folder();
            if (!Files.isDirectory(dir)) return;
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // a file still in use is left; the next run overwrites it
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Нормальная_ветка: can't clean up {}: {}", folder(), e.toString());
        }
    }

    /** Writes a stream to a file, under a temporary name until it's complete. */
    private static void copy(InputStream in, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Path part = out.resolveSibling(out.getFileName() + ".part");
        Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        Files.move(part, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeWav(byte[] ogg, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        Path part = out.resolveSibling(out.getFileName() + ".part");
        ByteBuffer data = MemoryUtil.memAlloc(ogg.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(ogg).flip();
            IntBuffer channels = stack.mallocInt(1), rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
            if (pcm == null) throw new IOException("not a readable Ogg Vorbis stream");
            try {
                ByteBuffer samples = MemoryUtil.memByteBuffer(pcm).order(ByteOrder.LITTLE_ENDIAN);
                try (FileChannel file = FileChannel.open(part, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    file.write(header(channels.get(0), rate.get(0), samples.remaining()));
                    while (samples.hasRemaining()) file.write(samples);
                }
            } finally {
                LibCStdlib.free(pcm);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        Files.move(part, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A 44-byte header for 16-bit PCM. */
    private static ByteBuffer header(int channels, int rate, int dataBytes) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataBytes).put(new byte[]{'W', 'A', 'V', 'E'});
        h.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        h.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataBytes);
        return h.flip();
    }
}
