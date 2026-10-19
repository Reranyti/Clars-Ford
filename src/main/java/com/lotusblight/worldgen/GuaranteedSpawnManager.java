package com.lotusblight.worldgen;

import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.lib.LotusLib;
import com.lotusblight.lib.LotusTaskQueue;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * World-generation-wide guarantee that says "at least one lotus grows in the
 * nearest body of water" — not just the new-player tutorial pond handled by
 * {@link com.lotusblight.world.LotusEvents}, but a background sweep over
 * freshly generated terrain everywhere.
 *
 * Driven by chunk-load events, throttled and budgeted the same way
 * {@code LotusEvents} budgets its own worldgen queue: a
 * {@link LotusTaskQueue} collects candidate chunk centers and
 * {@link LotusLib#process} drains a bounded number of them per server tick,
 * so a big burst of newly generated chunks (e.g. a player flying fast, or an
 * elytra flight over fresh terrain) never causes a scan/placement spike.
 */
public final class GuaranteedSpawnManager {
    private static final Random RANDOM = new Random();
    /** How far from a chunk's center we search for the nearest body of water. */
    private static final int WATER_SEARCH_RADIUS = 48;
    /**
     * If a registered outbreak already exists within this many blocks of the water, it counts as
     * covered. Raised from 112: inside a dense lotus_marsh biome (its whole point is lots of
     * small ponds close together), 112 still let this seed a separate independent outbreak on
     * nearly every pond - once each one matured, the entire marsh filled with its own lotus heart
     * at roughly the same time.
     */
    // Was 160 - each outbreak is now a much bigger commitment (wider spread radius, a guaranteed
    // mini-biome burst at phase 4), so anchors need real distance between them or the world fills
    // up with overlapping heavyweight outbreaks instead of a few genuinely significant ones.
    private static final double OUTBREAK_COVERAGE_RADIUS = 320.0;
    /** Not every loaded chunk is sampled — keeps the periodic scan cheap and matches the throttling style used elsewhere.
     *  Widened from 6 to 14, then to 40: outbreaks are now rare-but-significant landmarks, not
     *  something a player should run into "smothering" them within their first few minutes. */
    private static final int CHUNK_SAMPLE_RATE = 40;
    private static final int WATER_SEARCH_ATTEMPTS = 20;

    private static final LotusTaskQueue<GlobalPos> PENDING_WATER_CHECKS = new LotusTaskQueue<>();

    /** A world is closing: chunks queued in it mean nothing in the next one. */
    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING_WATER_CHECKS.clear();
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl && !com.lotusblight.story.StoryState.lotusShown(sl.getServer())) return;
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (RANDOM.nextInt(CHUNK_SAMPLE_RATE) != 0) return;
        ChunkPos chunk = event.getChunk().getPos();
        BlockPos center = new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8);
        PENDING_WATER_CHECKS.offer(GlobalPos.of(level.dimension(), center), com.lotusblight.LotusConfig.MAX_PENDING_WORLDGEN_TASKS.get());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!com.lotusblight.story.StoryState.lotusShown(event.getServer())) return;
        MinecraftServer server = event.getServer();
        LotusLib.process(PENDING_WATER_CHECKS, LotusLib.MAX_WORLDGEN_TASKS_PER_TICK, queued -> {
            ServerLevel level = server.getLevel(queued.dimension());
            if (level != null && level.hasChunkAt(queued.pos())) {
                ensureWaterHasOutbreak(level, queued.pos());
            }
        });
    }

    private void ensureWaterHasOutbreak(ServerLevel level, BlockPos center) {
        BlockPos water = findNearestWater(level, center, WATER_SEARCH_RADIUS);
        if (water == null) return;
        OutbreakSavedData data = OutbreakSavedData.get(level);
        if (data.nearestOutbreak(water, OUTBREAK_COVERAGE_RADIUS, false) != null) return;
        BlockPos flowerPos = water.above();
        if (!level.getBlockState(flowerPos).isAir()) return;
        level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        data.registerOutbreak(flowerPos.immutable(), level.getGameTime(), true);
    }

    private BlockPos findNearestWater(ServerLevel level, BlockPos center, int radius) {
        int top = Math.min(level.getMaxBuildHeight() - 2, 160);
        int bottom = Math.max(level.getMinBuildHeight() + 1, 32);
        for (int attempt = 0; attempt < WATER_SEARCH_ATTEMPTS; attempt++) {
            int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
            // The center chunk is confirmed loaded by the caller, but a 48-block/3-chunk radius
            // around it routinely lands in the not-yet-generated fringe during fast flight,
            // forcing synchronous chunk generation — same bug class as EpicenterManager. Skip
            // ungenerated columns instead of forcing them.
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) continue;
            for (int y = top; y >= bottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource()
                        && level.getBlockState(pos.above()).isAir()
                        // The anchor placed here is the big lily-pad-sized LotusMainBlock, not a
                        // small decorative shoot — it needs real open water around it or it clips
                        // into the shore.
                        && WaterClearance.hasClearWaterAround(level, pos, WaterClearance.REQUIRED_RADIUS)) {
                    return pos;
                }
            }
        }
        return null;
    }
}
