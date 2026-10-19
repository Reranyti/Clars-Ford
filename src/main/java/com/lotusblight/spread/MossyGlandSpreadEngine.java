package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.MossyGlandRecord;
import com.lotusblight.data.MossyGlandSavedData;
import com.lotusblight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spread engine for Mossy Glands, the World Lotus arc's second infection type - a deliberate copy
 * of InfectionSpreadEngine's own frontier-based approach (see "separate engine" decision) rather
 * than generalizing that already-tuned class, converting into real vanilla Lush Caves blocks
 * (MossyGroundTables) instead of our own lotus blocks. No phases yet - just a growing footprint,
 * radius and attempts fixed rather than escalating.
 */
public class MossyGlandSpreadEngine {
    // Was 6/6 - reported as "разрастается медленно"/"надо мега быстро" (bugs #18, #21), barely
    // noticeable against how much ground there is to convert. Radius and attempts both raised
    // well past the main infection's own phase 4 numbers (12/22) - Mossy Glands are meant to read
    // as an aggressive, fast-spreading anomaly once found, not a slower cousin of the real infection.
    private static final int RADIUS = 14;
    private static final int ATTEMPTS_PER_TICK = 40;
    private static final int FRONTIER_CAP = 400;
    /** Chance (1 in N) a successful ground conversion also grows a single azalea-family plant on top - real vanilla blocks, no new art, per the "vanilla but it multiplies" design. */
    private static final int AZALEA_CHANCE = 10;
    /** Mossy Glands are the surface counterpart of the real Lush Caves ("мшистые железы - поверхность, мшистые пещеры - глубина") - same lotus lineage, so the same mimic and vine-berries show up here too, just rarer than the azalea flourish. */
    private static final int MOSSY_VINE_CHANCE = 25;
    private static final int MOSSY_MIMIC_CHANCE = 80;

    private final Map<UUID, Deque<BlockPos>> frontiers = new HashMap<>();

    private static MossyGlandSpreadEngine instance;

    public MossyGlandSpreadEngine() {
        instance = this;
    }

    /** For /lotus timewarp - see InfectionSpreadEngine#forceTicks for why this doesn't touch the real server tick loop. */
    public static void forceTicks(ServerLevel level, int passes) {
        if (instance == null) return;
        for (int i = 0; i < passes; i++) {
            instance.tickLevel(level);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!com.lotusblight.story.StoryState.lotusShown(event.getServer())) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LotusConfig.SPREAD_INTERVAL_TICKS.get() != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    private void tickLevel(ServerLevel level) {
        MossyGlandSavedData data = MossyGlandSavedData.get(level);
        List<MossyGlandRecord> glands = new ArrayList<>(data.allGlands());
        for (MossyGlandRecord gland : glands) {
            if (!level.hasChunkAt(gland.pos())) continue;
            if (!hasNearbyPlayer(level, gland.pos())) continue;
            tickGland(level, data, gland);
        }
    }

    private boolean hasNearbyPlayer(ServerLevel level, BlockPos pos) {
        double blocks = LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        double rangeSq = blocks * blocks;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private void tickGland(ServerLevel level, MossyGlandSavedData data, MossyGlandRecord gland) {
        Deque<BlockPos> frontier = frontiers.computeIfAbsent(gland.id(), id -> {
            Deque<BlockPos> seeded = new ArrayDeque<>();
            seeded.add(gland.pos());
            return seeded;
        });

        int converted = 0;
        for (int i = 0; i < ATTEMPTS_PER_TICK; i++) {
            BlockPos source = pickFrontierSource(level, frontier, gland.pos());
            BlockPos convertedPos = trySpreadOnce(level, source);
            if (convertedPos != null) {
                pushFrontier(frontier, convertedPos);
                converted++;
            }
        }

        if (converted > 0) {
            data.updateGland(gland.withConvertedBlockCount(gland.convertedBlockCount() + converted));
        }
    }

    private BlockPos pickFrontierSource(ServerLevel level, Deque<BlockPos> frontier, BlockPos anchor) {
        if (frontier.isEmpty()) return anchor;
        int index = level.random.nextInt(frontier.size());
        int i = 0;
        for (BlockPos pos : frontier) {
            if (i++ == index) return pos;
        }
        return anchor;
    }

    private void pushFrontier(Deque<BlockPos> frontier, BlockPos pos) {
        frontier.addLast(pos.immutable());
        while (frontier.size() > FRONTIER_CAP) {
            frontier.pollFirst();
        }
    }

    private BlockPos randomNeighbour(ServerLevel level, BlockPos source) {
        int dx = level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
        int dy = level.random.nextInt(3) - 1;
        int dz = level.random.nextInt(RADIUS * 2 + 1) - RADIUS;
        return source.offset(dx, dy, dz);
    }

    /** Returns the converted position on success, or null if this attempt did nothing. Surface-only: only touches ground within 1 block of the source's own height. */
    private BlockPos trySpreadOnce(ServerLevel level, BlockPos source) {
        BlockPos target = randomNeighbour(level, source);
        if (!level.hasChunkAt(target)) return null;
        if (Math.abs(target.getY() - source.getY()) > 1) return null;

        BlockState targetState = level.getBlockState(target);
        BlockState replacement = MossyGroundTables.mossyReplacement(targetState);
        if (replacement == null) return null;

        level.setBlock(target, replacement, 3);

        BlockPos above = target.above();
        if (level.getBlockState(above).isAir()) {
            if (level.random.nextInt(MOSSY_MIMIC_CHANCE) == 0) {
                level.setBlock(above, ModBlocks.LOTUS_MIMIC.get().defaultBlockState(), 3);
            } else if (level.random.nextInt(MOSSY_VINE_CHANCE) == 0) {
                level.setBlock(above, ModBlocks.GLOW_BERRIES.get().defaultBlockState(), 3);
            } else if (level.random.nextInt(AZALEA_CHANCE) == 0) {
                level.setBlock(above, level.random.nextBoolean()
                        ? Blocks.AZALEA.defaultBlockState()
                        : Blocks.FLOWERING_AZALEA.defaultBlockState(), 3);
            }
        }
        return target;
    }
}
