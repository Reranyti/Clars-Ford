package com.lotusblight.world;

import com.lotusblight.data.LotusLabSavedData;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.data.WorldVersionData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

/**
 * Brings a world that was played on an older build up to date when a newer jar is put in the mods folder.
 *
 * <p>The author's rules for worlds (2026-10-04) are built in, not left to each step:
 * <ul>
 *   <li>a world the mod never made anything in is not touched at all: no record is written and no step runs;</li>
 *   <li>a step upgrades only what the MOD created (its lab, its infection, its blocks), never what the player built;</li>
 *   <li>a step must be safe to run twice. If one fails, the later ones wait for the next start and the world keeps the version it
 *       actually reached.</li>
 * </ul>
 * Terrain that was already generated is not regenerated: new chunks use the new generator on their own, old chunks stay as saved.
 *
 * <p>To ship a change that has to reach existing worlds: raise {@link #CURRENT}, add a {@link Step} with that number to {@link #STEPS}.
 */
public final class WorldMigrations {
    private static final Logger LOG = LogUtils.getLogger();

    /** The version of world content this build produces. */
    public static final int CURRENT = 1;

    /** One upgrade of what the mod itself put into a world. */
    public interface Step {
        int version();

        String name();

        void run(MinecraftServer server, ServerLevel overworld);
    }

    public static Step step(int version, String name, java.util.function.BiConsumer<MinecraftServer, ServerLevel> body) {
        return new Step() {
            @Override
            public int version() {
                return version;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public void run(MinecraftServer server, ServerLevel overworld) {
                body.accept(server, overworld);
            }
        };
    }

    /** Every step this build knows, oldest first. Version 1 only gives worlds their version record. */
    private static final List<Step> STEPS = List.of(
            step(1, "the world gets a version record", (server, overworld) -> { }));

    private WorldMigrations() {}

    /** A world the mod has already made something in: only such a world is ever migrated or recorded. */
    public static boolean isModWorld(ServerLevel overworld) {
        return LotusLabSavedData.get(overworld).isBuilt() || !OutbreakSavedData.get(overworld).allOutbreaks().isEmpty();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        int ran = apply(server, overworld, STEPS, CURRENT, isModWorld(overworld));
        if (ran > 0) LOG.info("World migrations: {} step(s) applied, the world is now at version {}", ran, WorldVersionData.get(overworld).version());
    }

    /** Runs what is due now (used by the command). */
    public static int migrateNow(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return apply(server, overworld, STEPS, CURRENT, isModWorld(overworld));
    }

    /**
     * Runs, oldest first, every step above the world's recorded version and up to {@code target}.
     *
     * @return how many steps ran (0 for a world the mod never touched, and for one that is already up to date)
     */
    public static int apply(MinecraftServer server, ServerLevel overworld, List<Step> steps, int target, boolean modWorld) {
        if (!modWorld) return 0;
        WorldVersionData data = WorldVersionData.get(overworld);
        int ran = 0;
        for (Step s : steps.stream().sorted(Comparator.comparingInt(Step::version)).toList()) {
            if (s.version() <= data.version() || s.version() > target) continue;
            try {
                s.run(server, overworld);
            } catch (RuntimeException e) {
                LOG.error("World migration '{}' (version {}) failed, the rest wait for the next start: {}", s.name(), s.version(), e.toString());
                return ran;
            }
            data.setVersion(s.version());
            ran++;
            LOG.info("World migration {}: {}", s.version(), s.name());
        }
        if (data.version() < target) data.setVersion(target);          // no step of its own between: nothing to do, the number just moves up
        return ran;
    }

    public static String status(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        boolean mod = isModWorld(overworld);
        return mod ? String.format("Версия мира: %d, версия этой сборки: %d.", WorldVersionData.get(overworld).version(), CURRENT)
                : "Мод в этом мире ещё ничего не создал: мир не трогается, версии нет.";
    }
}
