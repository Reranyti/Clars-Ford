package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.MeteoriteSpreadRecord;
import com.lotusblight.data.MeteoriteSpreadSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Spread engine for the meteorite/purple Blessing biome - "метеориты распространяют биом абсолютно
 * на любой блок делая его любым фиолетовым оттенком", the follow-up this session's own StarFall
 * write-up deferred. A deliberate copy of MossyGlandSpreadEngine's frontier-based approach (same
 * reasoning as that class's own javadoc) rather than generalizing either engine further, seeded at
 * every StarFall war-ending meteorite impact (see StarFallLineReachedPacket#placePurpleBlessingPatch).
 */
public class MeteoriteSpreadEngine {
    private static final int RADIUS = 12;
    private static final int ATTEMPTS_PER_TICK = 30;
    private static final int FRONTIER_CAP = 400;

    private final Map<UUID, Deque<BlockPos>> frontiers = new HashMap<>();

    private static MeteoriteSpreadEngine instance;

    public MeteoriteSpreadEngine() {
        instance = this;
    }

    /** Called once per fresh meteorite impact (see StarFallLineReachedPacket) to start it spreading on its own over time, on top of the immediate one-shot patch already placed there. */
    public static void seed(ServerLevel level, BlockPos pos) {
        MeteoriteSpreadSavedData.get(level).registerSource(pos, level.getGameTime());
    }

    /** How far out an impact site's own "world property" (not a real Minecraft biome, see class doc) counts as Blessing territory for mechanics that check isInBlessingBiome. */
    private static final double TERRITORY_RADIUS = 96.0;

    /**
     * "место падения... ставит своё [свойство]... а потом уже захват через биом" - the impact site
     * itself is marked as Blessing territory the instant it's seeded (MeteoriteSpreadSavedData
     * already records it immediately, before a single block visibly converts), independent of how
     * far the actual block-by-block spread has physically reached since. Lets mechanics that only
     * ever checked the real vanilla Blessing biome (guardian spawn skip, spread penalty near
     * Blessing) treat a meteorite impact site the same way, without touching real biome data at all.
     */
    public static boolean isBlessingTerritory(ServerLevel level, BlockPos pos) {
        double rangeSq = TERRITORY_RADIUS * TERRITORY_RADIUS;
        for (MeteoriteSpreadRecord source : MeteoriteSpreadSavedData.get(level).allSources()) {
            if (source.pos().distSqr(pos) <= rangeSq) return true;
        }
        return false;
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
        MeteoriteSpreadSavedData data = MeteoriteSpreadSavedData.get(level);
        List<MeteoriteSpreadRecord> sources = new ArrayList<>(data.allSources());
        for (MeteoriteSpreadRecord source : sources) {
            if (!level.hasChunkAt(source.pos())) continue;
            if (!hasNearbyPlayer(level, source.pos())) continue;
            tickSource(level, data, source);
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

    private void tickSource(ServerLevel level, MeteoriteSpreadSavedData data, MeteoriteSpreadRecord source) {
        Deque<BlockPos> frontier = frontiers.computeIfAbsent(source.id(), id -> {
            Deque<BlockPos> seeded = new ArrayDeque<>();
            seeded.add(source.pos());
            return seeded;
        });

        int converted = 0;
        for (int i = 0; i < ATTEMPTS_PER_TICK; i++) {
            BlockPos pick = pickFrontierSource(level, frontier, source.pos());
            BlockPos convertedPos = trySpreadOnce(level, pick);
            if (convertedPos != null) {
                pushFrontier(frontier, convertedPos);
                converted++;
            }
        }

        if (converted > 0) {
            data.updateSource(source.withConvertedBlockCount(source.convertedBlockCount() + converted));
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

    /** Returns the converted position on success, or null if this attempt did nothing. Surface-only, same height-band restriction as MossyGlandSpreadEngine#trySpreadOnce. */
    private BlockPos trySpreadOnce(ServerLevel level, BlockPos source) {
        BlockPos target = randomNeighbour(level, source);
        if (!level.hasChunkAt(target)) return null;
        if (Math.abs(target.getY() - source.getY()) > 1) return null;

        BlockState targetState = level.getBlockState(target);
        BlockState replacement = MeteoriteGroundTables.purpleReplacement(targetState);
        if (replacement == null) return null;

        level.setBlock(target, replacement, 3);
        return target;
    }
}
