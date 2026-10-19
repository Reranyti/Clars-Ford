package com.lotusblight.worldgen;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.InfectionPhases;
import com.lotusblight.spread.InfectionSpreadEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Guarantees the overworld always has at least one already-mature mini-biome
 * "epicenter" — a phase-4 outbreak with its lotus heart already grown — far
 * from spawn (5000-9000 blocks out), instead of every outbreak in the world
 * starting as a fresh seed that has to grow into a mini-biome on its own.
 *
 * Each candidate column probed this far from spawn is virtually guaranteed
 * to be in an ungenerated chunk, forcing synchronous chunk generation. The
 * first version of this class did up to 40 of those probes inside a single
 * tick, which — confirmed live via a ModernFix watchdog dump — could take a
 * single server tick over 40 SECONDS and leave the server 1900 ticks behind,
 * looking exactly like severe lag (block-break rubber-banding etc.) even in
 * singleplayer. This version budgets exactly one probe every
 * {@link #PROBE_INTERVAL_TICKS} ticks, so the worst a single tick ever pays
 * is one chunk's worth of generation.
 */
public final class EpicenterManager {
    private static final Random RANDOM = new Random();
    private static final int MIN_DISTANCE = 5000;
    private static final int MAX_DISTANCE = 9000;
    private static final int SEARCH_RADIUS = 64;
    private static final int PROBE_INTERVAL_TICKS = 4;
    private static final int PROBES_PER_CENTER = 40;
    private static final int MAX_TOTAL_PROBES = 2000;

    private boolean giveUp = false;
    private int totalProbes = 0;
    private BlockPos currentCenter;
    private int probesOnCurrentCenter = 0;

    /** A world is closing: the next one probes from the beginning. */
    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        giveUp = false;
        totalProbes = 0;
        currentCenter = null;
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
        OutbreakSavedData data = OutbreakSavedData.get(overworld);
        if (data.isEpicenterResolved()) {
            giveUp = true;
            return;
        }

        if (totalProbes >= MAX_TOTAL_PROBES) {
            data.markEpicenterResolved();
            giveUp = true;
            return;
        }

        if (currentCenter == null) {
            currentCenter = pickRandomCenter();
            probesOnCurrentCenter = 0;
        }

        totalProbes++;
        probesOnCurrentCenter++;
        BlockPos water = probeOnce(overworld, currentCenter, SEARCH_RADIUS);
        if (water != null) {
            if (placeEpicenter(overworld, data, water)) {
                data.markEpicenterResolved();
                giveUp = true;
                return;
            }
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

    private boolean placeEpicenter(ServerLevel level, OutbreakSavedData data, BlockPos water) {
        BlockPos flowerPos = water.above().immutable();
        if (!level.getBlockState(flowerPos).isAir()) return false;

        level.setBlock(flowerPos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        // Claim the world's one-and-only heart here too, same as InfectionSpreadEngine's natural
        // path - this guaranteed distant epicenter runs early, so it should normally win the
        // claim before any player-grown outbreak ever reaches phase 4. If one already did, the
        // epicenter stays a plain anchor instead of becoming a second heart.
        if (data.claimHeart()) {
            InfectionSpreadEngine.promoteAnchorToHeart(level, flowerPos);
        }
        OutbreakRecord epicenter = data.registerOutbreak(flowerPos, level.getGameTime(), true)
                .withPhase(4)
                .withPeakPhase(4)
                .withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(4));
        data.updateOutbreak(epicenter);
        return true;
    }

    /** A single random column scan near center — at most one chunk's worth of generation. */
    private BlockPos probeOnce(ServerLevel level, BlockPos center, int radius) {
        int top = Math.min(level.getMaxBuildHeight() - 2, 160);
        int bottom = Math.max(level.getMinBuildHeight() + 1, 32);
        int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
        int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource()
                    && level.getBlockState(pos.above()).isAir()
                    && WaterClearance.hasClearWaterAround(level, pos, WaterClearance.REQUIRED_RADIUS)) {
                return pos;
            }
        }
        return null;
    }
}
