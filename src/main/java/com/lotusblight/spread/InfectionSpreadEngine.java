package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The infection spread engine, replacing the old single-offset-per-anchor
 * scan in LotusEvents.
 *
 * Each active, chunk-loaded outbreak keeps a small in-memory "frontier" of
 * recently infected block positions. Every engine pass, each outbreak
 * spends a phase-scaled number of attempts: pick a random frontier block as
 * the source, look at a random neighbour within the phase's radius, and try
 * to convert it via {@link SpreadTables} — a real (bounded) tile-conversion
 * model instead of one fixed random offset from a single anchor point. Every
 * successful conversion updates {@link OutbreakSavedData} incrementally so
 * nothing downstream (boss bar, map, minimap) ever has to rescan a volume.
 */
public class InfectionSpreadEngine {

    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.2f, 0.95f, 0.35f), 1.0f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.55f), 1.0f);
    // Was 48 - with the much wider phase 3/4 SPREAD_RADIUS (InfectionPhases), a 48-slot recent-
    // positions window meant the frontier was almost entirely refilled by the LAST few dozen
    // conversions, so new spread kept re-rolling neighbours of the same small recent cluster
    // instead of actually fanning out across the wider radius it was now allowed to reach.
    private static final int FRONTIER_CAP = 220;
    /**
     * Phase 5 used to have no cap at all - the frontier grew by one entry per conversion forever
     * and pickFrontierSource copies it on every attempt, so a long-lived phase 5 outbreak slowly
     * turned into a per-pass lag spike and a memory leak. A much wider window still lets it fan
     * out far past the phase 4 footprint.
     */
    private static final int FRONTIER_CAP_PHASE5 = 1200;
    /**
     * randomNeighbour walks +/-1 in Y per hop, and above ground there is only air to convert, so the
     * frontier used to drift down into the stone and most conversions happened where nobody sees
     * them (a phase 5 save had ~4000 lotus stone underground and only a few dozen infected blocks on
     * the actual surface). Phases 1-2 only grow across the top block of each column, phases 3-4
     * send a small share of attempts into the rock, and phase 5 splits them evenly between the
     * surface and the depths.
     */
    private static final int SURFACE_SNAP_RANGE = 8;
    /** One attempt in this many goes underground instead of across the surface; 0 = never. Indexed by phase. */
    private static final int[] UNDERGROUND_ONE_IN = {0, 0, 0, 6, 6, 2};
    /** How far under the surface a fresh underground pocket may start. Indexed by phase. */
    private static final int[] UNDERGROUND_MAX_DEPTH = {0, 0, 0, 16, 16, 48};
    /** Phase 5: lotus stone this deep or deeper may come out as lotus ore instead. */
    private static final int ORE_MIN_DEPTH = 6;
    /** Over water, one surface attempt in this many stays on top (a shoot); the rest go to the seabed. */
    private static final int WATER_SURFACE_ONE_IN = 3;
    /** Land roots are solid and unbreakable - rare enough not to wall the ground off. */
    private static final int LAND_ROOT_ONE_IN = 24;
    /** Seabed roots, one waterlogged root on top of freshly infected bottom now and then. */
    private static final int SEABED_ROOT_ONE_IN = 8;
    private static final int ORE_ONE_IN = 30;
    private static final double SPORE_RADIUS = 3.5;

    /**
     * Streams Reflowing has no public API, but it doesn't need one: it works by mixin-patching
     * vanilla's own FluidState#getFlow (WaterFluidMixin/FlowingFluidMixin), so any code that
     * calls the vanilla flow API — including findWaterDownstream below — already reads SR's
     * realistic river flow field for free once SR is installed. Detecting its presence here only
     * lets us decide to TRUST that flow more (chase it further per attempt, spend more attempts
     * on water) — a soft dependency with a strong effect when present, per design decision.
     */
    private static final boolean STREAMS_REFLOWING_LOADED = ModList.get().isLoaded("streamsreflowing");

    /** Non-persisted per-outbreak set of recently infected positions. Lazily reseeded from the anchor if missing (e.g. after a restart). */
    private final Map<UUID, Deque<BlockPos>> frontiers = new HashMap<>();
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();

    private static InfectionSpreadEngine instance;

    public InfectionSpreadEngine() {
        instance = this;
    }

    /**
     * For /lotus timewarp (see LotusCommands) - a SAFE alternative to actually speeding up the
     * game (real "tick warp" means re-entering the server's own tick loop from inside itself,
     * which risks corrupting whatever it's mid-iteration over; not worth the risk for a testing
     * convenience). This just runs extra passes of this engine's own real tick logic in a tight
     * loop, skipping the normal SPREAD_INTERVAL_TICKS wait between them.
     */
    public static void forceTicks(ServerLevel level, int passes) {
        if (instance == null) return;
        for (int i = 0; i < passes; i++) {
            instance.biomeCatchUpUsed = false;
            instance.tickLevel(level);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!com.lotusblight.story.StoryState.lotusShown(event.getServer())) return;
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % 10 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    updateBossBar(level, player);
                }
            }
        }

        if (server.getTickCount() % LotusConfig.SPREAD_INTERVAL_TICKS.get() != 0) return;
        biomeCatchUpUsed = false;
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    /**
     * Was a hard on/off gate on ACTIVE_CHUNK_RADIUS (default already maxed at 32 chunks/512 blocks,
     * with no config headroom to raise it further) - any outbreak nobody had ever stood within 512
     * blocks of simply never ticked at all, reading as a flat "Цветение, 0 прогресса" forever on
     * the map no matter how long the world ran. "лучше все очаги на карте с каждой далёкостью
     * развивать всё медленнее и медленнее" - every outbreak on the server now always gets SOME
     * attempts, just scaled down the further it is from the nearest player, down to a slow crawl
     * rather than a hard stop. Reads naturally as lore too: the lotus's influence is strongest
     * where it's actually being watched/fed by a nearby presence, and fades - but never vanishes -
     * the further from any observer it is.
     */
    /** One old-outbreak biome catch-up per engine pass across all levels (see tickOutbreak). */
    private boolean biomeCatchUpUsed;

    private void tickLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        List<OutbreakRecord> outbreaks = new ArrayList<>(data.allOutbreaks());
        if (level.players().isEmpty()) return;
        for (OutbreakRecord outbreak : outbreaks) {
            if (!level.hasChunkAt(outbreak.pos())) continue;
            double distance = nearestPlayerDistance(level, outbreak.pos());
            tickOutbreak(level, data, outbreak, distanceSpeedMultiplier(distance));
        }
    }

    private double nearestPlayerDistance(ServerLevel level, BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dist = Math.sqrt(player.blockPosition().distSqr(pos));
            if (dist < best) best = dist;
        }
        return best;
    }

    /** Full speed within FULL_SPEED_RADIUS, then a smooth linear falloff out to FALLOFF_RANGE past it, floored at a slow but nonzero crawl - never a hard cutoff. */
    private static final double FULL_SPEED_RADIUS = 128.0;
    private static final double FALLOFF_RANGE = 3000.0;
    private static final double MIN_DISTANCE_MULTIPLIER = 0.05;

    private double distanceSpeedMultiplier(double distance) {
        if (distance <= FULL_SPEED_RADIUS) return 1.0;
        double t = Math.min(1.0, (distance - FULL_SPEED_RADIUS) / FALLOFF_RANGE);
        return Mth.lerp(t, 1.0, MIN_DISTANCE_MULTIPLIER);
    }

    private void tickOutbreak(ServerLevel level, OutbreakSavedData data, OutbreakRecord outbreak, double speedMultiplier) {
        Deque<BlockPos> frontier = frontiers.computeIfAbsent(outbreak.id(), id -> {
            Deque<BlockPos> seeded = new ArrayDeque<>();
            seeded.add(outbreak.pos());
            return seeded;
        });

        int phase = outbreak.phase();
        // hasNearbyLand's sampling used to be the WHOLE check - 8 random points in a 65x65 square
        // around the anchor. A large pond/lake/wide river (exactly what GuaranteedSpawnManager
        // likes to seed outbreaks next to) is easily bigger than that square, so every sample could
        // land on water and this outbreak got misclassified as "stuck in the ocean": phase capped
        // at 2 (OCEAN_PHASE_CAP) and its attempts skewed toward the ocean bonus instead of actual
        // land conversion, even though it was sitting on an ordinary lake, not open sea. Now
        // requires the anchor to actually BE in a vanilla ocean-tagged biome first - a lake in a
        // plains/forest/taiga biome can never trigger this misclassification, no matter how wide it is.
        boolean openOcean = level.getBiome(outbreak.pos()).is(net.minecraft.tags.BiomeTags.IS_OCEAN) && !hasNearbyLand(level, outbreak.pos());
        int attempts = InfectionPhases.attemptsPerTick(phase);
        if (STREAMS_REFLOWING_LOADED && LotusConfig.STREAMS_COMPATIBILITY.get() && isNearWater(level, frontier, outbreak.pos())) {
            // A real, mixin-driven river/stream network is a much stronger signal than vanilla's flat water — lean on it harder.
            attempts += 2;
        }
        // The dialogue branch a nearby player locked in now actually does something to the world,
        // not just gate the grafting rod tool: an ALLIANCE player is actively helping this outbreak
        // grow, a RESISTANCE player is actively suppressing it just by presence.
        attempts = Math.max(0, attempts + branchInfluence(level, outbreak.pos()));
        // SPREAD_RADIUS used to have zero callers (InfectionPhases' own per-phase array did all the
        // actual work) despite being exposed as a tunable "search radius around an active lotus
        // heart". Treat its default (6) as a no-op baseline and apply the delta on top of the
        // normal phase-scaled radius, so the slider still means something without flattening the
        // phase 1->4 escalation into one fixed number.
        int radius = Math.max(1, InfectionPhases.spreadRadius(phase) + (LotusConfig.SPREAD_RADIUS.get() - 6));
        if (isInBlessingBiome(level, outbreak.pos())) {
            // The wiki has always claimed "Blessing slows the spread of the lotus" — until now
            // nothing anywhere actually checked the biome to make that true.
            attempts = Math.max(0, attempts - BLESSING_ATTEMPTS_PENALTY);
            radius = Math.max(1, radius - BLESSING_RADIUS_PENALTY);
        } else if (isInLotusTerritory(level, outbreak.pos(), phase)) {
            // "МНЕ НУЖНА МЕХАНИКА ЧТО НОРМАЛЬНО РАБОТАЕТ И ВЛИЯЕТ" - a purely visual "looks like
            // lotus_marsh" fix wasn't the ask; this is the real, mirrored opposite of the Blessing
            // penalty above - an actual growth-rate bonus, not cosmetics. True territory is either
            // the real lotus_marsh biome (genuine biome-id correspondence, no Mixin needed since
            // we're only reading it, not writing it) or an outbreak that has already earned its own
            // mini-biome status (phase 4+) — both read as "home turf" for the Lotus.
            attempts += LOTUS_ATTEMPTS_BONUS;
            radius += LOTUS_RADIUS_BONUS;
        }
        if (openOcean) {
            // Open water is nothing but a spread medium for this infection, so it races through
            // it much faster than it eats through land — but "захват ближников"/mini-biome only
            // make sense once there are actual trees and ground to take over, so an outbreak stuck
            // in the middle of an ocean is capped below phase 3 until it actually finds a shore.
            attempts += OCEAN_ATTEMPTS_BONUS;
            radius += OCEAN_RADIUS_BONUS;
        }
        if (outbreak.isSuppressed(level.getGameTime())) {
            // A dusted lotus shoot nearby (see LotusEvents' cleansing powder) - half the growth.
            attempts /= SUPPRESSED_ATTEMPTS_DIVISOR;
        }
        if (attempts == 0) return;
        // Distance-from-nearest-player falloff (see distanceSpeedMultiplier) and the global pace.
        // The fraction is rolled rather than rounded, so a slow or distant outbreak still keeps
        // crawling forward on average instead of being floored up to a full attempt every pass.
        double scaled = attempts * GLOBAL_SPEED * speedMultiplier;
        attempts = (int) scaled + (level.random.nextDouble() < scaled - (int) scaled ? 1 : 0);
        if (attempts == 0) return;
        int converted = 0;
        bonusCounted = 0;

        for (int i = 0; i < attempts; i++) {
            BlockPos source = pickFrontierSource(level, frontier, outbreak.pos());
            BlockPos convertedPos = trySpreadOnce(level, source, radius, phase);
            if (convertedPos != null) {
                pushFrontier(frontier, convertedPos, phase);
                data.incrementChunkCount(new ChunkPos(convertedPos), 1);
                infectNearbyLiving(level, convertedPos);
                converted++;
            }
        }

        if (InfectionPhases.canGrowVineBarrier(phase) && level.random.nextDouble() < InfectionPhases.vineBarrierChance()) {
            BlockPos candidate = frontier.isEmpty() ? outbreak.pos() : new ArrayList<>(frontier).get(level.random.nextInt(frontier.size()));
            VineBarrierGenerator.tryGrow(level, outbreak.id(), candidate.above());
        }

        converted += bonusCounted;
        bonusCounted = 0;
        int newCount = outbreak.infectedBlockCount() + converted;
        int newPhase = InfectionPhases.phaseForBlockCount(newCount);
        if (openOcean) {
            // hasNearbyLand is a random sample that skips unloaded columns, so an ocean-biome
            // outbreak can read as "open ocean" on one pass and not the next. Only hold back growth
            // here - never push an outbreak below a phase it already has.
            newPhase = Math.min(newPhase, Math.max(OCEAN_PHASE_CAP, outbreak.phase()));
        }
        // Was only capped for the openOcean case - a maxPhaseCap-limited child outbreak (see
        // RootGrowthEngine) could keep computing an uncapped newPhase from its raw block count
        // forever, past the point outbreak.phase() itself ever moves (withPhase already clamps that
        // to maxPhaseCap). newPhase > outbreak.phase() then stayed true on every single tick instead
        // of firing once, replaying the phase-up particles/sound/announcement and the phase-4 tree
        // burst indefinitely, and maybeSpawnHeart below saw a phase the outbreak was never actually
        // allowed to reach.
        newPhase = Math.min(newPhase, outbreak.maxPhaseCap());
        float progress = InfectionPhases.progressWithinPhase(newPhase, newCount);

        OutbreakRecord updated = outbreak.withInfectedBlockCount(newCount).withPhase(newPhase).withProgress(progress)
                .withPeakPhase(newPhase);
        if (!biomeCatchUpUsed && outbreak.peakPhase() >= 4 && !outbreak.biomeConverted()) {
            // Reached phase 4 back when the rewrite only touched Y=0 (or before it existed) - redo it
            // once. At most one per pass, so a save full of old outbreaks doesn't do them all at once.
            biomeCatchUpUsed = true;
            com.lotusblight.worldgen.LotusBiomeConverter.convertToLotusMarsh(level, outbreak.pos());
            updated = updated.withBiomeConverted(true);
        }
        // Compared against the peak, not the current phase: the count can drop (mining, cleansing)
        // and climb back over the same threshold, which used to replay the announcement, the tree
        // burst and the biome rewrite every time.
        if (newPhase > outbreak.peakPhase()) {
            level.sendParticles(GREEN, outbreak.pos().getX() + 0.5, outbreak.pos().getY() + 1.0, outbreak.pos().getZ() + 0.5, 32, 1.4, 0.7, 1.4, 0.04);
            level.playSound(null, outbreak.pos(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.55f, 0.45f);
            announcePhaseUp(level, outbreak.pos(), newPhase);
            dropPagesForWitnesses(level, outbreak.pos(), newPhase);
            // Was ">= 4" - fired a SECOND full burst (up to 30 trees + 70 grass patches) when an
            // outbreak advanced from phase 4 to phase 5, on top of the one it already got reaching
            // phase 4 - looked like trees spawning themselves out of nowhere (bug #6). Only the
            // exact transition into phase 4 should trigger this, not every phase at or above it.
            if (newPhase >= 4 && outbreak.peakPhase() < 4) {
                guaranteeMiniBiomeGrowth(level, outbreak.pos());
                com.lotusblight.worldgen.LotusBiomeConverter.convertToLotusMarsh(level, outbreak.pos());
                updated = updated.withBiomeConverted(true);
            }
            maybeSpawnHeart(level, data, outbreak, newPhase);
        }
        data.updateOutbreak(updated);
    }

    private static final double BRANCH_INFLUENCE_RADIUS = 48.0;
    private static final int BRANCH_INFLUENCE_STRENGTH = 2;

    /**
     * Once most attempts started landing on the surface (instead of drifting into the rock and
     * failing), the same numbers covered half a test world in two in-game days. Scales every
     * outbreak's final attempt count - phase table, territory/branch/ocean bonuses and all - so
     * their proportions stay intact; aimed at roughly 6-8 days for the same spread.
     */
    private static final double GLOBAL_SPEED = 0.3;
    private static final int SUPPRESSED_ATTEMPTS_DIVISOR = 2;
    private static final int BLESSING_ATTEMPTS_PENALTY = 1;
    private static final int BLESSING_RADIUS_PENALTY = 1;
    private static final int LOTUS_ATTEMPTS_BONUS = 2;
    private static final int LOTUS_RADIUS_BONUS = 2;
    /** Matches InfectionPhases#canGrowVineBarrier's own "mini-biome" threshold - an outbreak this mature counts as the Lotus's home turf even outside the real biome. */
    private static final int LOTUS_TERRITORY_PHASE = 4;

    /** Real vanilla Blessing biome OR a meteorite impact site's own "world property" (see MeteoriteSpreadEngine#isBlessingTerritory) - both read as Blessing territory for this check. */
    private boolean isInBlessingBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(com.lotusblight.registry.ModBiomes.BLESSING_BIOME)
                || MeteoriteSpreadEngine.isBlessingTerritory(level, pos);
    }

    /** Real vanilla lotus_marsh biome (genuine biome-id correspondence) OR an outbreak that has already grown into its own mini-biome (phase 4+) - see the "СООТВЕТСТВИЕ БИОМОВ" discussion this mirrors the Blessing penalty for. */
    private boolean isInLotusTerritory(ServerLevel level, BlockPos pos, int phase) {
        return level.getBiome(pos).is(com.lotusblight.registry.ModBiomes.LOTUS_BIOME) || phase >= LOTUS_TERRITORY_PHASE;
    }

    private static final int OCEAN_ATTEMPTS_BONUS = 3;
    private static final int OCEAN_RADIUS_BONUS = 2;
    private static final int OCEAN_PHASE_CAP = 2;
    private static final int LAND_SEARCH_RADIUS = 32;
    private static final int LAND_SEARCH_SAMPLES = 8;

    /**
     * Cheap sample-based check: does any nearby column actually break the surface above water?
     * Uses the WORLD_SURFACE heightmap, whose topmost non-air block is the top of the water
     * column itself out in open ocean (water isn't air) — so if that block is water, this sample
     * found no land. A handful of random samples in a wide radius is enough to tell "still out at
     * sea" from "reached a shore" without scanning the whole area every tick.
     */
    private boolean hasNearbyLand(ServerLevel level, BlockPos anchor) {
        for (int i = 0; i < LAND_SEARCH_SAMPLES; i++) {
            int dx = level.random.nextInt(LAND_SEARCH_RADIUS * 2 + 1) - LAND_SEARCH_RADIUS;
            int dz = level.random.nextInt(LAND_SEARCH_RADIUS * 2 + 1) - LAND_SEARCH_RADIUS;
            int x = anchor.getX() + dx;
            int z = anchor.getZ() + dz;
            if (!level.hasChunkAt(new BlockPos(x, anchor.getY(), z))) continue;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (!level.getFluidState(new BlockPos(x, surfaceY, z)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Sums +/- influence from every player within range who has locked in a dialogue branch. */
    private int branchInfluence(ServerLevel level, BlockPos anchor) {
        int influence = 0;
        AABB box = new AABB(anchor).inflate(BRANCH_INFLUENCE_RADIUS);
        for (ServerPlayer player : level.players()) {
            if (!box.contains(player.getX(), player.getY(), player.getZ())) continue;
            int branch = LotusPlayerState.getDialogueBranch(player);
            if (branch == LotusPlayerState.BRANCH_ALLIANCE) influence += BRANCH_INFLUENCE_STRENGTH;
            else if (branch == LotusPlayerState.BRANCH_RESISTANCE) influence -= BRANCH_INFLUENCE_STRENGTH;
        }
        return influence;
    }

    /**
     * A uniformly random pick here used to mean an outbreak anywhere near a real lake/river
     * quietly starved its own land spread: every converted water tile pushes a new water frontier
     * entry, and a lake holds far more tiles than any shoreline does, so the frontier fills up
     * with water positions over time and land sources become statistically rare to ever draw.
     * Testers kept reporting "only trees change, the ground/water look untouched" - the ground
     * genuinely wasn't getting its fair share of attempts, not just an illusion. Now tries a
     * handful of random draws first looking specifically for a non-water entry before falling
     * back to a fully random pick, so land conversion keeps pace instead of being crowded out.
     */
    private static final int LAND_BIAS_DRAWS = 6;

    /**
     * The land-bias draws above only ever excluded water - once phase 3 unlocks tree conversion,
     * every converted log/leaf pushes a CANOPY-height position into the frontier too, and nothing
     * filtered those out. A frontier that fills up with tree-top entries starves ground conversion
     * exactly the way it used to be starved by water (see the doc comment above): randomNeighbour's
     * own dy range is only +/-1 from whatever source got picked, so an attempt sourced from a
     * canopy position searches more canopy, essentially never reaching back down to ground level.
     * This is what let an outbreak reach phase 4 (and fire the guaranteed mini-biome burst) almost
     * entirely through water+tree conversions, with the ground underneath never actually touched -
     * "инфекция только деревья трогает". Bias against canopy sources the same way water already is.
     */
    private boolean isCanopySource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    private BlockPos pickFrontierSource(ServerLevel level, Deque<BlockPos> frontier, BlockPos anchor) {
        if (frontier.isEmpty()) return anchor;
        List<BlockPos> asList = null;
        for (int attempt = 0; attempt < LAND_BIAS_DRAWS; attempt++) {
            if (asList == null) asList = new ArrayList<>(frontier);
            BlockPos candidate = asList.get(level.random.nextInt(asList.size()));
            if (!level.hasChunkAt(candidate)) continue;
            if (!level.getFluidState(candidate).is(Fluids.WATER) && !isCanopySource(level, candidate)) {
                return candidate;
            }
        }
        int index = level.random.nextInt(frontier.size());
        int i = 0;
        for (BlockPos pos : frontier) {
            if (i++ == index) return pos;
        }
        return anchor;
    }

    private static final int SHOOT_SPACING = 4;

    /**
     * Without this, both shoot-placement sites above kept refilling the exact same tiny patch of
     * water edge every tick — visually reads as "30 mini lotuses crammed into one square meter"
     * instead of the infection actually spreading outward. A shoot only places if nothing else in
     * a small radius already has one, forcing growth to walk outward across the frontier instead
     * of endlessly re-rolling the same crowded spot.
     */
    private boolean hasNearbyShoot(ServerLevel level, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-SHOOT_SPACING, -1, -SHOOT_SPACING), pos.offset(SHOOT_SPACING, 1, SHOOT_SPACING))) {
            if (check.equals(pos) || !level.hasChunkAt(check)) continue;
            BlockState state = level.getBlockState(check);
            if (state.is(ModBlocks.LOTUS_SHOOT.get()) || state.is(ModBlocks.INFECTED_LOTUS.get()) || state.is(ModBlocks.LOTUS_HEART.get())) {
                return true;
            }
        }
        return false;
    }

    /** Phase 5 keeps a much wider window than the earlier phases, so it isn't "locally contained" the way they are. */
    private void pushFrontier(Deque<BlockPos> frontier, BlockPos pos, int phase) {
        frontier.addLast(pos.immutable());
        int cap = phase >= 5 ? FRONTIER_CAP_PHASE5 : FRONTIER_CAP;
        while (frontier.size() > cap) {
            frontier.pollFirst();
        }
    }

    /** Returns the converted position on success, or null if this attempt did nothing. */
    private BlockPos trySpreadOnce(ServerLevel level, BlockPos source, int radius, int phase) {
        // Frontier entries (and the fallback pick) can sit in chunks that unloaded since they were
        // converted - every block read below would force a synchronous chunk load on the tick.
        if (!level.hasChunkAt(source)) return null;
        BlockPos target = randomNeighbour(level, source, radius);
        if (!level.hasChunkAt(target)) return null;
        boolean onSurface = false;
        int depthBelowSurface = 0;

        // Water has priority: infection follows the actual water layout (soft Streams Reflowing compatibility).
        if (LotusConfig.STREAMS_COMPATIBILITY.get() && level.getFluidState(source).is(Fluids.WATER)) {
            // With Streams Reflowing loaded, vanilla's getFlow() reports its realistic river flow
            // field (direction AND speed — see STREAMS_REFLOWING_LOADED javadoc) instead of vanilla's
            // weak default. Fast-flowing water (a real river) lets infection chase the current several
            // hops in one attempt; still/slow water (a lake) behaves like before. This is what makes
            // the current directly drive how far and how fast the infection actually travels.
            int hops = STREAMS_REFLOWING_LOADED ? 1 + flowSpeedHops(level, source) : 1;
            target = source;
            for (int hop = 0; hop < hops; hop++) {
                BlockPos next = findWaterDownstream(level, target);
                if (next.equals(target) || !level.hasChunkAt(next)) break;
                target = next;
            }
        } else {
            int surfaceY = surfaceY(level, target);
            int sourceSurfaceY = surfaceY(level, source);
            int phaseIndex = Mth.clamp(phase, 0, UNDERGROUND_ONE_IN.length - 1);
            int undergroundOneIn = UNDERGROUND_ONE_IN[phaseIndex];
            boolean underground = undergroundOneIn > 0 && level.random.nextInt(undergroundOneIn) == 0;
            if (!underground) {
                // Compared column to column, so a source deep in the rock can still feed the
                // surface above it; the range keeps a step from jumping onto a cliff top.
                if (Math.abs(surfaceY - sourceSurfaceY) > SURFACE_SNAP_RANGE) return null;
                target = new BlockPos(target.getX(), surfaceY, target.getZ());
                onSurface = true;
                if (level.getFluidState(target).is(Fluids.WATER) && level.random.nextInt(WATER_SURFACE_ONE_IN) != 0) {
                    // Over water most attempts go to the bottom instead: the seabed gets infected
                    // and the surface itself only ever gets the occasional shoot.
                    int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, target.getX(), target.getZ()) - 1;
                    target = new BlockPos(target.getX(), floorY, target.getZ());
                }
            } else if (sourceSurfaceY - source.getY() < 2) {
                // A surface source seeds a new pocket somewhere below; an underground one keeps
                // growing its own vein through the plain 3D neighbour picked above.
                int depth = 1 + level.random.nextInt(UNDERGROUND_MAX_DEPTH[phaseIndex]);
                target = new BlockPos(target.getX(), Math.max(level.getMinBuildHeight(), surfaceY - depth), target.getZ());
            } else if (surfaceY - target.getY() > UNDERGROUND_MAX_DEPTH[phaseIndex]) {
                // A vein left over from a deeper phase (the count can drop) stops at this phase's depth.
                return null;
            }
            depthBelowSurface = surfaceY(level, target) - target.getY();
            if (!onSurface && depthBelowSurface < 1) return null;
            // Aquifers and underground lakes aren't decorated - roots and pads belong on the surface.
            if (!onSurface && !level.getFluidState(target).isEmpty()) return null;
        }

        BlockState targetState = level.getBlockState(target);

        // "заражённая вода...ПЕРЕПИСЫВАНИЕМ ВОДЫ НЕ ЗАНИМАТСЯ" - infected water used to be a real,
        // separate fluid (own block/bucket) that spread would swap plain water source blocks into,
        // then decorate on a later tick. Water is never rewritten anymore - infection of water is a
        // purely conceptual/data thing (biome territory + fog tint), so this decorates a clean
        // water source directly, in one tick, without ever touching the fluid itself.
        if (level.getFluidState(target).is(Fluids.WATER) && level.getFluidState(target).isSource()
                && !targetState.is(ModBlocks.LOTUS_ROOTS.get())) {
            // Roots are solid and can't be mined - on the top layer they turned whole lakes and the
            // sea into a floor nobody could swim or sail through. They only grow under the surface.
            // "No water above", not "air above" - the layer right under a shoot is still the surface.
            boolean topLayer = level.getFluidState(target.above()).isEmpty();
            if (!topLayer) {
                if (level.random.nextInt(3) != 0) return null;
                level.setBlock(target, ModBlocks.LOTUS_ROOTS.get().defaultBlockState()
                        .setValue(BlockStateProperties.WATERLOGGED, true), 3);
                bloom(level, target, GREEN);
                return target;
            }
            // The shoot carries its own pad (see LotusShootBlock), so it sits right on the water.
            BlockPos flowerPos = target.above();
            if (level.getBlockState(flowerPos).isAir() && !hasNearbyShoot(level, flowerPos)) {
                level.setBlock(flowerPos, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
                bloom(level, flowerPos, PINK);
                return flowerPos;
            }
            return null;
        }

        // Grass follows its own rule: only converts once the soil beneath it is already infected.
        if (SpreadTables.isCleanGrass(targetState)) {
            BlockState below = level.getBlockState(target.below());
            if (SpreadTables.isInfectedGround(below)) {
                BlockState infected = SpreadTables.infectedGrass(targetState);
                // infectedGrass() returns null for tall grass/ferns/short grass by design - they
                // "die off" rather than convert (see its own doc comment) - but null used to mean
                // this branch did nothing at all, so they just sat there forever, untouched, even
                // on fully infected soil. Actually remove them instead of silently no-opping.
                level.setBlock(target, infected != null ? infected : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                bloom(level, target, GREEN);
                return target;
            }
            return null;
        }

        // Logs/leaves only convert from phase 3 onward, matching the захват-ближников escalation.
        if (phase >= 3 && (SpreadTables.isCleanLog(targetState) || SpreadTables.isCleanLeaves(targetState))) {
            level.setBlock(target, SpreadTables.isCleanLog(targetState) ? SpreadTables.infectedLog() : SpreadTables.infectedLeaves(), 3);
            bloom(level, target, GREEN);
            return target;
        }

        // Generic ground table (dirt/sand/gravel/stone/terracotta -> infected analogue).
        BlockState groundReplacement = SpreadTables.infectedGroundReplacement(targetState);
        if (groundReplacement != null) {
            if (phase >= 5 && !onSurface && depthBelowSurface >= ORE_MIN_DEPTH && groundReplacement.is(ModBlocks.LOTUS_STONE.get())
                    && level.random.nextInt(ORE_ONE_IN) == 0) {
                groundReplacement = ModBlocks.LOTUS_ORE.get().defaultBlockState();
            }
            level.setBlock(target, groundReplacement, 3);
            if (!onSurface) {
                // Nothing grows in caves - roots and trees are for the surface.
                bloom(level, target, GREEN);
                return target;
            }
            BlockPos above = target.above();
            // Grass/ferns on top die off with the soil (same rule as the isCleanGrass branch above,
            // which surface targets never reach since the heightmap sits under plants).
            if (SpreadTables.isCleanGrass(level.getBlockState(above))) {
                level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            if (level.getFluidState(above).is(Fluids.WATER) && level.getFluidState(above).isSource()
                    && !level.getFluidState(above.above()).isEmpty() && level.random.nextInt(SEABED_ROOT_ONE_IN) == 0) {
                level.setBlock(above, ModBlocks.LOTUS_ROOTS.get().defaultBlockState()
                        .setValue(BlockStateProperties.WATERLOGGED, true), 3);
                countPlaced(level, above);
            }
            if (level.getBlockState(above).isAir()) {
                // Before this, the mini-biome (phase 4) never grew anything of its own — logs and
                // leaves only ever came from converting a vanilla tree that happened to already be
                // standing there. An outbreak that spread across open stone or sand had no way to
                // ever grow a single tree. growOwnVegetation gives it real, self-seeding flora.
                if (!growOwnVegetation(level, phase, above)) {
                    if (level.random.nextInt(LAND_ROOT_ONE_IN) == 0) {
                        level.setBlock(above, ModBlocks.LOTUS_ROOTS.get().defaultBlockState(), 3);
                    }
                }
            }
            bloom(level, target, GREEN);
            return target;
        }

        // A small shoot can bloom on open air directly above still-clean water reached at the
        // edge of the radius. This used to place the full 4-tall LOTUS_MAIN anchor here — the
        // same "one indestructible pillar per outbreak" block meant for GuaranteedSpawnManager —
        // so every ordinary spread tick was littering the world with duplicate anchor pillars
        // instead of small decorative flowers. LOTUS_SHOOT is the correct block: cosmetic, not
        // an anchor, and already used for the equivalent case a few lines up (water source ->
        // shoot). Throttled so it doesn't outbid ground conversion at every single attempt.
        if (targetState.isAir() && level.getFluidState(target.below()).is(Fluids.WATER) && level.random.nextInt(3) == 0
                && !hasNearbyShoot(level, target)) {
            level.setBlock(target, ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
            bloom(level, target, PINK);
            return target;
        }

        return null;
    }

    /**
     * Extra downstream hops earned from the current's actual speed, not just its presence.
     * Vanilla's own flow magnitude is nearly flat; Streams Reflowing's mixin-patched getFlow()
     * reports real hydraulic speed, so a fast river genuinely pushes infection further per
     * attempt than a barely-moving lake edge does. Thresholds are heuristic since SR exposes no
     * documented scale — tuned to feel like "river" clearly outruns "lake" without either
     * degenerating to 0 or exploding to an unbounded chase.
     */
    private int flowSpeedHops(ServerLevel level, BlockPos pos) {
        var flow = level.getFluidState(pos).getFlow(level, pos);
        double speed = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
        if (speed > 0.08) return 2;
        if (speed > 0.02) return 1;
        return 0;
    }

    /** Cheap sample of a handful of frontier positions to decide whether this outbreak currently touches water at all. */
    private boolean isNearWater(ServerLevel level, Deque<BlockPos> frontier, BlockPos anchor) {
        int checked = 0;
        for (BlockPos pos : frontier) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getFluidState(pos).is(Fluids.WATER) || level.getFluidState(pos.below()).is(Fluids.WATER)) return true;
            if (++checked >= 6) break;
        }
        return level.getFluidState(anchor).is(Fluids.WATER) || level.getFluidState(anchor.below()).is(Fluids.WATER);
    }

    /** Top non-leaf block of the column - the ground under trees, the water surface on a lake. */
    private static int surfaceY(ServerLevel level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
    }

    private BlockPos randomNeighbour(ServerLevel level, BlockPos source, int radius) {
        int dx = level.random.nextInt(radius * 2 + 1) - radius;
        int dy = level.random.nextInt(3) - 1;
        int dz = level.random.nextInt(radius * 2 + 1) - radius;
        return source.offset(dx, dy, dz);
    }

    private BlockPos findWaterDownstream(ServerLevel level, BlockPos source) {
        var flow = level.getFluidState(source).getFlow(level, source);
        BlockPos best = source;
        double bestScore = -Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(source.offset(-1, -1, -1), source.offset(1, 0, 1))) {
            if (!level.hasChunkAt(candidate) || !level.getFluidState(candidate).is(Fluids.WATER)) continue;
            double dx = candidate.getX() - source.getX();
            double dz = candidate.getZ() - source.getZ();
            double score = dx * flow.x + dz * flow.z - Math.max(0, candidate.getY() - source.getY()) * 0.75;
            if (!candidate.equals(source) && score > bestScore) {
                best = candidate.immutable();
                bestScore = score;
            }
        }
        return best.equals(source) ? source.relative(Direction.getRandom(level.random)) : best;
    }

    /**
     * Used to skip Player entirely - "заражение должно быть видно и влиять на игрока", but the
     * spore effect (real damage + Weakness, see LotusSporeEffect's own doc comment) only ever
     * touched nearby mobs, never the player standing right next to the same conversion. Now the
     * player breathes the same spores as everything else nearby.
     */
    private void infectNearbyLiving(ServerLevel level, BlockPos source) {
        AABB box = new AABB(source).inflate(SPORE_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getType().getCategory() == MobCategory.MISC) continue;
            entity.addEffect(new MobEffectInstance(ModEffects.LOTUS_SPORES.get(), 240, 0));
        }
    }

    // "Заросли, а не декорация" — the mini-biome is supposed to read as a dense, oppressive
    // thicket (see the reference the user gave: overlapping canopies, thick undergrowth), not
    // scattered single trees every ~30 conversions. Tripled the odds and widened/heightened the
    // canopy so trees actually overlap into a real canopy instead of standing apart.
    private static final int MINI_TREE_CHANCE_PHASE4 = 10;
    private static final int MINI_TREE_CHANCE_PHASE3 = 20;

    /**
     * Self-seeding flora for freshly-converted ground, instead of relying entirely on whatever
     * vanilla grass/trees happened to already be standing there. Used to gate the mini-tree behind
     * phase 4 only, which — on top of ATTEMPTS_PER_TICK's own phase 1-3 numbers — made everything
     * before the mini-biome read as thin ("нищенски") next to it instead of a smaller version of
     * the same density. Phase 3 now grows the same trees too, just rarer, so the canopy visibly
     * fills in a step before the mini-biome rather than appearing all at once. Any infected
     * phase >= 2 ground has a chance to grow blossom grass directly, not just via the separate
     * "grass converts if soil below is infected" rule (which only ever recolors pre-existing
     * grass). Returns true if it planted anything, so the caller doesn't also drop a root on the
     * same spot.
     */
    /** lotus_mimic had a whole trap mechanic (LotusMimicBlock#entityInside) but was never actually placed anywhere in the mod at all - not rare, just completely absent (bug #18). Phase 3+ only, rarer than the mini-tree, so it's a real surprise rather than a common sight. */
    private static final int MIMIC_CHANCE = 60;

    private boolean growOwnVegetation(ServerLevel level, int phase, BlockPos above) {
        if (phase >= 3 && level.random.nextInt(MIMIC_CHANCE) == 0) {
            level.setBlock(above, ModBlocks.LOTUS_MIMIC.get().defaultBlockState(), 3);
            return true;
        }
        if (phase >= 3) {
            int chance = phase >= 4 ? MINI_TREE_CHANCE_PHASE4 : MINI_TREE_CHANCE_PHASE3;
            if (level.random.nextInt(chance) == 0) {
                return tryGrowMiniTree(level, above);
            }
        }
        return false;
    }

    // Scaled up alongside phase 4's SPREAD_RADIUS (12) and ATTEMPTS_PER_TICK (22) - outbreaks are
    // deliberately rarer now (see GuaranteedSpawnManager/RootGrowthEngine's spawn-chance cuts), so
    // each one reaching phase 4 needs to actually deliver a real mini-biome, not a token gesture.
    private static final int GUARANTEED_GROWTH_RADIUS = 26;
    private static final int GUARANTEED_TREE_ATTEMPTS = 30;

    /**
     * Reaching phase 4 used to just raise MINI_TREE_CHANCE's odds and leave the actual canopy to
     * however many spread attempts happened to land on infected ground with air above afterward -
     * a freshly-matured outbreak could sit there reading as barely-there for minutes (or, on a
     * quiet/low-traffic outbreak, indefinitely) while EpicenterManager's hand-placed guaranteed
     * epicenter looked like a real thicket from the moment it existed. This forces a burst of the
     * same trees/grass right when phase 4 is first reached, so "just hit the mini-biom" reads as
     * one immediately, not as a promise the RNG gets around to eventually.
     */
    private void guaranteeMiniBiomeGrowth(ServerLevel level, BlockPos anchor) {
        int treesPlanted = 0;
        for (int i = 0; i < GUARANTEED_TREE_ATTEMPTS * 4 && treesPlanted < GUARANTEED_TREE_ATTEMPTS; i++) {
            BlockPos above = randomGroundAbove(level, anchor);
            if (above != null && tryGrowMiniTree(level, above)) {
                treesPlanted++;
            }
        }
    }

    /** A random air block directly above infected/clean ground within GUARANTEED_GROWTH_RADIUS, or null if the sampled spot isn't usable. */
    private BlockPos randomGroundAbove(ServerLevel level, BlockPos anchor) {
        int dx = level.random.nextInt(GUARANTEED_GROWTH_RADIUS * 2 + 1) - GUARANTEED_GROWTH_RADIUS;
        int dz = level.random.nextInt(GUARANTEED_GROWTH_RADIUS * 2 + 1) - GUARANTEED_GROWTH_RADIUS;
        BlockPos candidate = anchor.offset(dx, 0, dz);
        if (!level.hasChunkAt(candidate)) return null;
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
        BlockPos ground = new BlockPos(candidate.getX(), surfaceY - 1, candidate.getZ());
        if (Math.abs(ground.getY() - anchor.getY()) > 6) return null; // stay near the anchor's own elevation
        // Used to also accept isCleanGround (untouched vanilla terrain) - the phase-4 guaranteed
        // burst (see guaranteeMiniBiomeGrowth) could then scatter ~30 lotus trees across ground
        // that was never actually infected, anywhere within GUARANTEED_GROWTH_RADIUS of the
        // anchor. That's exactly what read as "only the trees ever change, the ground doesn't" -
        // the trees weren't tied to real ground conversion at all. Only grow on ground the
        // infection has actually already converted.
        BlockState below = level.getBlockState(ground);
        if (!SpreadTables.isInfectedGround(below)) return null;
        return ground.above();
    }

    /** A 3-5 tall lotus-log trunk with a thick, multi-layer leaf canopy — the mini-biome's own tree, grown rather than converted. */
    /** Blocks placed beside the converted ground in this pass (trees, seabed roots): the world loses them when mined, so they count. */
    private int bonusCounted;

    private void countPlaced(ServerLevel level, BlockPos pos) {
        OutbreakSavedData.get(level).incrementChunkCount(new ChunkPos(pos), 1);
        bonusCounted++;
    }

    private boolean tryGrowMiniTree(ServerLevel level, BlockPos base) {
        // The canopy reaches 2 blocks out from the trunk, which can cross into an unloaded chunk.
        if (!level.hasChunkAt(base.offset(-2, 0, -2)) || !level.hasChunkAt(base.offset(2, 0, 2))
                || !level.hasChunkAt(base.offset(-2, 0, 2)) || !level.hasChunkAt(base.offset(2, 0, -2))) {
            return false;
        }
        int trunkHeight = 3 + level.random.nextInt(3);
        for (int i = 0; i < trunkHeight; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) return false;
        }
        for (int i = 0; i < trunkHeight; i++) {
            level.setBlock(base.above(i), ModBlocks.LOTUS_LOG.get().defaultBlockState(), 3);
            countPlaced(level, base.above(i));
        }
        // Two overlapping canopy layers (wide lower ring + narrower top) so the leaves read as a
        // thick crown from a distance, not a single flat slab.
        BlockPos lowerRing = base.above(trunkHeight - 1);
        for (BlockPos leaf : BlockPos.betweenClosed(lowerRing.offset(-2, 0, -2), lowerRing.offset(2, 1, 2))) {
            if (level.getBlockState(leaf).isAir()) {
                level.setBlock(leaf, ModBlocks.LOTUS_LEAVES.get().defaultBlockState(), 3);
                countPlaced(level, leaf);
            }
        }
        BlockPos canopyCenter = base.above(trunkHeight + 1);
        for (BlockPos leaf : BlockPos.betweenClosed(canopyCenter.offset(-1, 0, -1), canopyCenter.offset(1, 1, 1))) {
            if (level.getBlockState(leaf).isAir()) {
                level.setBlock(leaf, ModBlocks.LOTUS_LEAVES.get().defaultBlockState(), 3);
                countPlaced(level, leaf);
            }
        }
        return true;
    }

    private static final double PHASE_UP_ANNOUNCE_RADIUS = 64.0;
    private static final int FIRST_PAGE_PHASE = 3;

    /**
     * Another way to the Object Zero diary besides guardian kills: whoever is close when an outbreak
     * reaches phase 3, 4 or 5 for the first time finds a page they're still missing.
     */
    private void dropPagesForWitnesses(ServerLevel level, BlockPos pos, int newPhase) {
        if (newPhase < FIRST_PAGE_PHASE) return;
        double rangeSq = PHASE_UP_ANNOUNCE_RADIUS * PHASE_UP_ANNOUNCE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) {
                com.lotusblight.item.ScientistPageItem.giveMissingPage(player);
            }
        }
    }

    /**
     * A phase-up already got particles + a sound at the anchor, but nothing actually told the
     * player WHAT just happened - easy to miss entirely, or notice the boss bar's number changed
     * without registering it as an event. An action-bar line naming the new phase makes each
     * escalation a felt beat instead of something only visible if you happen to be staring at the
     * boss bar right at that moment.
     */
    private void announcePhaseUp(ServerLevel level, BlockPos pos, int newPhase) {
        double rangeSq = PHASE_UP_ANNOUNCE_RADIUS * PHASE_UP_ANNOUNCE_RADIUS;
        Component message = Component.literal("Очаг перешёл в фазу " + newPhase + ": " + InfectionPhases.phaseName(newPhase) + "!");
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /**
     * Reaching phase 4 for the first time upgrades the outbreak's anchor pillar into the lotus
     * heart, replacing the whole 4-part stem/crown structure. Before this, LOTUS_HEART was only
     * ever placed by LotusSeedItem the instant a player planted a seed — completely disconnected
     * from phase progression, so a heart could exist next to a barely-infected patch while a
     * fully matured phase-4 outbreak never grew one at all.
     */
    private void maybeSpawnHeart(ServerLevel level, OutbreakSavedData data, OutbreakRecord outbreak, int newPhase) {
        if (newPhase < 4 || outbreak.peakPhase() >= 4) return;
        // Only one lotus heart is meant to ever exist in the world, like the End portal - a
        // singular landmark, not something every sufficiently-grown outbreak earns. Whichever
        // outbreak gets here first claims it; every other one still reaches phase 4 (the
        // mini-biome itself, vegetation, vine barriers) but never grows its own heart block.
        if (!data.claimHeart()) return;
        promoteAnchorToHeart(level, outbreak.pos());
    }

    /**
     * Public/static so admin commands (see com.lotusblight.command.LotusCommands) can force this
     * same transition for testing without needing an InfectionSpreadEngine instance or waiting for
     * a real phase-4 transition. Returns false if there's no ordinary anchor pillar at pos to
     * upgrade (e.g. it's already a heart, or nothing's there).
     */
    public static boolean promoteAnchorToHeart(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.INFECTED_LOTUS.get())) return false;
        level.setBlock(pos, ModBlocks.LOTUS_HEART.get().defaultBlockState(), 3);
        level.sendParticles(PINK, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 1.0, 1.0, 1.0, 0.05);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
        return true;
    }

    private void bloom(ServerLevel level, BlockPos pos, DustParticleOptions color) {
        level.sendParticles(color, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 5, 0.3, 0.25, 0.3, 0.01);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.3f, 1.5f);
    }

    /**
     * updateBossBar only ever calls bar.removePlayer() from inside the "no nearby outbreak" branch,
     * which never runs for a player who simply stops appearing in level.players() by disconnecting -
     * leaving one stale ServerPlayer reference in that bar's player set per such disconnect, forever.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerBossEvent bar = bossBars.remove(event.getEntity().getUUID());
        if (bar != null && event.getEntity() instanceof ServerPlayer player) {
            bar.removePlayer(player);
        }
    }

    private void updateBossBar(ServerLevel level, ServerPlayer player) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord nearest = data.nearestOutbreak(player.blockPosition(), 96.0, false);
        ServerBossEvent bar = bossBars.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(Component.literal("Разрастание лотоса"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            created.setVisible(false);
            return created;
        });
        if (nearest == null) {
            bar.removePlayer(player);
            bar.setVisible(false);
            return;
        }
        bar.setName(Component.literal(InfectionPhases.phaseName(nearest.phase()) + " — " + Math.round(nearest.progress() * 100.0f) + "%"));
        bar.setProgress(Mth.clamp(nearest.progress(), 0.0f, 1.0f));
        bar.addPlayer(player);
        bar.setVisible(true);
    }
}
