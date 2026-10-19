package com.lotusblight.spread.roots;

import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.lib.LotusLib;
import com.lotusblight.lib.LotusTaskQueue;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.SpreadTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Roots and mini-lotuses" system.
 *
 * For every active outbreak (phase &gt;= 2) this periodically grows a bounded
 * root chain outward from the anchor across adjacent infected/soil ground
 * and underwater positions, reusing {@code LotusRootsBlock}-style
 * waterlogging for the cosmetic land segments and the new
 * {@link com.lotusblight.world.TangledRootsBlock} (real collision + a brief
 * slowness debuff) for the underwater segments, so an infected waterway
 * visibly gets harder to swim through over time.
 *
 * As the chain grows it has a small chance to seed a hidden "child" outbreak
 * a short distance from the parent — a mini-lotus that starts its own
 * (capped) growth without instantly matching the parent's scale.
 *
 * A child outbreak's phase cap now lives on {@link OutbreakRecord#maxPhaseCap} itself, persisted
 * so it survives restarts and self-enforcing since {@link OutbreakRecord#withPhase} clamps against
 * it directly - InfectionSpreadEngine never needs to know a cap exists at all.
 *
 * Work is budgeted through {@link LotusTaskQueue} / {@link LotusLib#process}
 * exactly like the worldgen/spread queues elsewhere in the mod: at most one
 * pending task per outbreak is enqueued on the periodic sweep, and only a
 * small number of tasks are drained per server tick.
 */
public final class RootGrowthEngine {

    private static final DustParticleOptions ROOT_GREEN = new DustParticleOptions(new Vector3f(0.15f, 0.6f, 0.3f), 0.9f);

    /** How often (in ticks) we sweep outbreaks and enqueue at most one growth task each. */
    private static final int SWEEP_INTERVAL_TICKS = 40;
    /** Max queued growth tasks actually processed per server tick — keeps this bounded like MAX_SPREAD_TASKS_PER_TICK. */
    private static final int MAX_ROOT_TASKS_PER_TICK = 4;

    private static final int MAX_CHAIN_LENGTH = 24;
    private static final int MAX_ROOTS_PER_OUTBREAK = 48;

    // Was 0.05 - each outbreak now carries much more weight (bigger radius/attempts, a real
    // mini-biome burst on phase 4), so there need to be fewer of them, not more, to avoid
    // smothering the player early and to keep any one anchor feeling significant.
    private static final double CHILD_OUTBREAK_CHANCE = 0.015;
    private static final int CHILD_MIN_CHAIN_LENGTH = 3;
    private static final int CHILD_MAX_PHASE = 2;

    private final LotusTaskQueue<RootGrowthTask> queue = new LotusTaskQueue<>();
    private final Set<UUID> queuedOutbreaks = new HashSet<>();
    private final Map<UUID, RootChainState> chains = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!com.lotusblight.story.StoryState.lotusShown(event.getServer())) return;
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % SWEEP_INTERVAL_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                sweepLevel(level);
            }
        }

        LotusLib.process(queue, MAX_ROOT_TASKS_PER_TICK, task -> growOnce(server, task));
    }

    private void sweepLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);

        double activeBlocks = com.lotusblight.LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        double activeRangeSq = activeBlocks * activeBlocks;

        for (OutbreakRecord outbreak : new ArrayList<>(data.allOutbreaks())) {
            if (outbreak.phase() < 2) continue;
            if (!level.hasChunkAt(outbreak.pos())) continue;
            if (!withinActiveRange(level, outbreak.pos(), activeRangeSq)) continue;
            RootChainState chain = chains.get(outbreak.id());
            if (chain != null && (chain.exhausted || chain.chainLength >= MAX_CHAIN_LENGTH || chain.rootsGrown >= MAX_ROOTS_PER_OUTBREAK)) {
                continue;
            }
            if (!queuedOutbreaks.add(outbreak.id())) continue; // at most one pending task per outbreak at a time
            // Unlike GuaranteedSpawnManager/LotusEvents' worldgen queues, this previously called
            // the uncapped offer(T) overload - with drain fixed at MAX_ROOT_TASKS_PER_TICK/tick,
            // enough concurrently-eligible outbreaks (e.g. many phase 2+ anchors near a player)
            // grew this queue every sweep with nothing ever rejecting an offer. Capped the same
            // way the sibling worldgen queues already are.
            // A dropped offer must release the "already queued" mark too, or this outbreak never gets
            // another root task for the rest of the session.
            if (!queue.tryOffer(new RootGrowthTask(outbreak.id(), level.dimension()), com.lotusblight.LotusConfig.MAX_PENDING_WORLDGEN_TASKS.get())) {
                queuedOutbreaks.remove(outbreak.id());
            }
        }
    }

    private boolean withinActiveRange(ServerLevel level, BlockPos pos, double rangeSq) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private void growOnce(MinecraftServer server, RootGrowthTask task) {
        queuedOutbreaks.remove(task.outbreakId());
        ServerLevel level = server.getLevel(task.dimension());
        if (level == null) return;

        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord outbreak = data.getOutbreak(task.outbreakId());
        if (outbreak == null) return;

        RootChainState chain = chains.computeIfAbsent(task.outbreakId(), id -> new RootChainState(outbreak.pos()));
        if (chain.exhausted || chain.chainLength >= MAX_CHAIN_LENGTH || chain.rootsGrown >= MAX_ROOTS_PER_OUTBREAK) {
            chain.exhausted = true;
            return;
        }
        if (!level.hasChunkAt(chain.tip)) return;

        BlockPos next = findNextRootPos(level, chain.tip);
        if (next == null) {
            chain.exhausted = true; // dead end — stop trying to extend this outbreak's chain
            return;
        }

        boolean underwater = level.getFluidState(next).is(Fluids.WATER) && level.getFluidState(next).isSource();
        // TangledRootsBlock (real collision, briefly slowed entities) is gone - underwater growth
        // now places the same LOTUS_ROOTS as everywhere else, just waterlogged.
        level.setBlock(next, ModBlocks.LOTUS_ROOTS.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, underwater), 3);
        if (underwater) {
            data.incrementChunkCount(new ChunkPos(next), 1);
            // counted in the outbreak too: mining a waterlogged root takes it back down
            OutbreakRecord fresh = data.getOutbreak(task.outbreakId());
            if (fresh != null) data.updateOutbreak(fresh.withInfectedBlockCount(fresh.infectedBlockCount() + 1));   // the spread pass re-derives the phase
        }
        level.sendParticles(ROOT_GREEN, next.getX() + 0.5, next.getY() + 0.4, next.getZ() + 0.5, 3, 0.2, 0.15, 0.2, 0.01);
        level.playSound(null, next, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.2f, 0.7f);

        chain.tip = next.immutable();
        chain.chainLength++;
        chain.rootsGrown++;

        maybeSpawnChildOutbreak(level, data, outbreak, chain, next);
    }

    /**
     * Registering a child outbreak used to place no block at all — the mini-lotus was
     * invisible data until InfectionSpreadEngine happened to convert something near it, which
     * (before that engine's own pillar bug was fixed) meant the first visible sign of a child
     * outbreak was a full-size anchor pillar. Now the child gets its own small LOTUS_SHOOT the
     * moment it's registered, directly above the root tip that spawned it, so a mini-lotus is
     * always a visible flower first and a growth source second.
     */
    /**
     * hasNearbyShoot alone only ruled out placing a child within 2 blocks of another shoot/anchor
     * — nothing stopped a winding root chain from looping back near its OWN parent (or another
     * outbreak entirely) and registering a second full anchor a few blocks away, reading in-world
     * as "2-3 main lotuses on one square meter". GuaranteedSpawnManager already keeps its own
     * anchors this far apart (OUTBREAK_COVERAGE_RADIUS); children need the same real check.
     */
    private static final double CHILD_MIN_DISTANCE_FROM_OUTBREAK = 40.0;

    private void maybeSpawnChildOutbreak(ServerLevel level, OutbreakSavedData data, OutbreakRecord parent, RootChainState chain, BlockPos at) {
        if (chain.chainLength < CHILD_MIN_CHAIN_LENGTH) return;
        if (level.random.nextDouble() >= CHILD_OUTBREAK_CHANCE) return;
        // The mini-lotus floats on water like a lily pad - a root tip on dry ground can't hold one.
        if (!level.getFluidState(at).is(net.minecraft.world.level.material.Fluids.WATER)) return;

        BlockPos flowerPos = at.above();
        if (!level.getBlockState(flowerPos).isAir() || hasNearbyShoot(level, flowerPos)) return;
        if (data.nearestOutbreak(flowerPos, CHILD_MIN_DISTANCE_FROM_OUTBREAK, false) != null) return;

        OutbreakRecord child = data.registerOutbreak(flowerPos.immutable(), level.getGameTime(), true);
        data.updateOutbreak(child.withMaxPhaseCap(CHILD_MAX_PHASE));
        level.setBlock(flowerPos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
        level.sendParticles(ROOT_GREEN, flowerPos.getX() + 0.5, flowerPos.getY() + 0.6, flowerPos.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.02);
    }

    private static final int SHOOT_SPACING = 4;

    /** Same spacing protection InfectionSpreadEngine uses for ordinary spread — this code path spawns shoots independently and was missing it entirely. */
    private boolean hasNearbyShoot(ServerLevel level, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-SHOOT_SPACING, -1, -SHOOT_SPACING), pos.offset(SHOOT_SPACING, 1, SHOOT_SPACING))) {
            if (check.equals(pos)) continue;
            var state = level.getBlockState(check);
            if (state.is(ModBlocks.LOTUS_SHOOT.get()) || state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.LOTUS_HEART.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks one step outward (a random horizontal direction from the current
     * tip) for the next valid root position: either an air block directly
     * above infected/clean ground, or a water-source block whose floor is
     * infected/clean ground. Returns {@code null} if nothing usable is found
     * nearby, which ends that outbreak's chain.
     */
    private BlockPos findNextRootPos(ServerLevel level, BlockPos tip) {
        List<Direction> directions = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        java.util.Collections.shuffle(directions, new java.util.Random(level.random.nextLong()));

        // Checked in this order (same level, then gently down/up) instead of strictly top-to-
        // bottom (+1..-2): scanning top-down let the chain jump straight to a candidate 2-3
        // blocks below the tip whenever the same-level spot didn't happen to pass first, so
        // consecutive links could differ by 3 blocks of height on a 1-block horizontal step —
        // reads as the thread skipping through a block instead of winding continuously.
        int[] dyOrder = {0, -1, 1, -2};
        for (Direction dir : directions) {
            BlockPos base = tip.relative(dir);
            for (int dy : dyOrder) {
                BlockPos probe = base.offset(0, dy, 0);
                if (!level.hasChunkAt(probe)) continue;

                BlockState below = level.getBlockState(probe.below());
                boolean groundOk = SpreadTables.isInfectedGround(below) || SpreadTables.isCleanGround(below);
                if (!groundOk) continue;

                BlockState here = level.getBlockState(probe);
                boolean underwaterSource = level.getFluidState(probe).is(Fluids.WATER) && level.getFluidState(probe).isSource();
                if (underwaterSource && !here.is(ModBlocks.LOTUS_ROOTS.get())) {
                    return probe.immutable();
                }
                if (here.isAir()) {
                    return probe.immutable();
                }
            }
        }
        return null;
    }
}
