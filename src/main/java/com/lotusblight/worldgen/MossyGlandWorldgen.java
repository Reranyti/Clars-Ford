package com.lotusblight.worldgen;

import com.lotusblight.data.MossyGlandSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Guarantees exactly NATURAL_GLAND_COUNT Mossy Gland seeds exist somewhere in the overworld,
 * scattered far from spawn - the same throttled one-probe-per-tick approach as EpicenterManager
 * (see its own doc comment for why: probing this far out forces synchronous chunk generation, and
 * doing more than one probe per tick measurably stalled the server). Runs until 3 exist in
 * MossyGlandSavedData, regardless of how they got there (including /lotus gland spawn), then
 * stops - no separate "resolved" flag needed.
 */
public final class MossyGlandWorldgen {
    public static final int NATURAL_GLAND_COUNT = 3;

    private static final Random RANDOM = new Random();
    private static final int MIN_DISTANCE = 3000;
    private static final int MAX_DISTANCE = 10000;
    private static final int SEARCH_RADIUS = 64;
    private static final int PROBE_INTERVAL_TICKS = 4;
    private static final int PROBES_PER_CENTER = 40;
    private static final int MAX_TOTAL_PROBES_PER_SEED = 2000;

    private boolean giveUp = false;
    private int totalProbes = 0;
    private BlockPos currentCenter;
    private int probesOnCurrentCenter = 0;

    /** A world is closing: the next one probes from the beginning. */
    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        giveUp = false;
        totalProbes = 0;
        probesOnCurrentCenter = 0;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!com.lotusblight.story.StoryState.lotusShown(event.getServer())) return;
        if (giveUp) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PROBE_INTERVAL_TICKS != 0) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        MossyGlandSavedData data = MossyGlandSavedData.get(overworld);
        if (data.allGlands().size() >= NATURAL_GLAND_COUNT) {
            giveUp = true;
            return;
        }

        if (totalProbes >= MAX_TOTAL_PROBES_PER_SEED) {
            // "куда всех разворовали? Мшистых желёз нет" - this used to set the class-level giveUp
            // flag, which onServerTick's own first check treats as "stop searching forever", not
            // just "this one seed attempt failed". A single unlucky 2000-probe stretch (e.g. a
            // center that landed in ocean) silently killed ALL THREE glands for the rest of the
            // server session, not just the current one - the doc comment's "give up on this one
            // seed" was never what the code actually did. Reset and try a fresh center instead;
            // only the per-seed counters are actually scoped to one attempt.
            totalProbes = 0;
            currentCenter = null;
            return;
        }

        if (currentCenter == null) {
            currentCenter = pickRandomCenter();
            probesOnCurrentCenter = 0;
        }

        totalProbes++;
        probesOnCurrentCenter++;
        BlockPos surface = probeOnce(overworld, currentCenter, SEARCH_RADIUS);
        if (surface != null) {
            // Convert the seed's own ground tile immediately, not just its (empty) frontier entry -
            // otherwise there's genuinely nothing to see here until the engine's first tick
            // converts a NEIGHBOUR of this position, which could be several seconds off.
            var replacement = com.lotusblight.spread.MossyGroundTables.mossyReplacement(overworld.getBlockState(surface));
            if (replacement != null) {
                overworld.setBlock(surface, replacement, 3);
            }
            data.registerGland(surface, overworld.getGameTime());
            totalProbes = 0;
            currentCenter = null;
            // Not marking giveUp - the next tick re-checks the gland count and either starts
            // hunting for the next seed or stops once all NATURAL_GLAND_COUNT exist.
            return;
        }

        if (probesOnCurrentCenter >= PROBES_PER_CENTER) {
            currentCenter = null; // exhausted this spot — a fresh random center starts next tick
        }
    }

    private BlockPos pickRandomCenter() {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        int distance = MIN_DISTANCE + RANDOM.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        int centerX = (int) Math.round(Math.cos(angle) * distance);
        int centerZ = (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(centerX, 64, centerZ);
    }

    /** A single random column scan near center — solid ground with air above, at most one chunk's worth of generation. */
    private BlockPos probeOnce(ServerLevel level, BlockPos center, int radius) {
        int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
        int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos ground = new BlockPos(x, surfaceY - 1, z);
        if (level.getFluidState(ground).isEmpty() && !level.getBlockState(ground).isAir()
                && level.getBlockState(ground.above()).isAir()) {
            return ground.immutable();
        }
        return null;
    }
}
