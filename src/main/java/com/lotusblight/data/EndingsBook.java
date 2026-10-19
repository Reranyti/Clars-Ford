package com.lotusblight.data;

import com.lotusblight.LotusBlight;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Which endings the player has seen, across every world. It lives in a file in the config folder, not in a world, because
 * a world is deleted when it ends (and the menu that lists the endings opens before any world exists).
 */
public final class EndingsBook {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = "endings.properties";

    /** An ending the menu knows about. The name stays hidden ("???") until it has been seen. */
    public record Ending(String id, String name, String icon) {}

    /** Every ending, in the order the menu lists them. Names that are not decided yet are left out of the lang files on purpose. */
    public static final List<Ending> ALL = List.of(
            new Ending("traitor", "Предатель", null),                      // to be rewritten as an alternative branch with the new characters and events
            new Ending("cursed", "Проклятая", "glitch"),                 // the cursed branch: the glitch light
            new Ending("true", "Истинная", "four"),                     // the true line: break the seal, gather all the Architects (the Lights)
            new Ending("chromo", "Хромо", "chromo"));

    /** For now every ending and every step is open, so the whole story can be looked through; switch off when the menu goes live. */
    public static final boolean UNLOCK_ALL = true;

    private static Properties cache;

    private EndingsBook() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(LotusBlight.MODID).resolve(FILE);
    }

    private static synchronized Properties load() {
        if (cache != null) return cache;
        Properties props = new Properties();
        Path path = file();
        if (Files.isRegularFile(path)) {
            try (Reader in = Files.newBufferedReader(path)) {
                props.load(in);
            } catch (IOException e) {
                LOG.warn("Could not read the endings file: {}", e.toString());
            }
        }
        cache = props;
        return props;
    }

    public static synchronized boolean isSeen(String id) {
        return UNLOCK_ALL || load().containsKey(id);
    }

    public static synchronized int seenCount() {
        int n = 0;
        for (Ending e : ALL) if (isSeen(e.id())) n++;
        return n;
    }

    /** Marks an ending as seen; safe to call twice. */
    public static synchronized void markSeen(String id) {
        Properties props = load();
        if (props.containsKey(id)) return;
        props.setProperty(id, Long.toString(System.currentTimeMillis()));
        try {
            Files.createDirectories(file().getParent());
            try (Writer out = Files.newBufferedWriter(file())) {
                props.store(out, "Endings seen");
            }
        } catch (IOException e) {
            LOG.warn("Could not write the endings file: {}", e.toString());
        }
    }
}
