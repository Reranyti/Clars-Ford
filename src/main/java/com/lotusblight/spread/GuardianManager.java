package com.lotusblight.spread;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.ChatOverhaulBranchColor;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModItems;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Мобы: заражённые варианты существ и страж растения" (ALPHA_SCOPE.md) — the
 * one row of the alpha scope that was never built. Without this, an outbreak
 * was pure block-conversion tug-of-war: nothing ever opposed a player standing
 * next to a mature outbreak and cleansing it block by block. A mature (phase
 * &gt;= {@link #MIN_GUARDIAN_PHASE}) outbreak now keeps a small number of
 * hostile guardians nearby, giving RESISTANCE players (and anyone else who
 * gets close) an actual reason to be careful instead of a purely passive
 * garden to tidy up.
 *
 * Reuses vanilla {@link Wolf} instead of a fully custom entity: no new
 * model/texture pipeline needed for a first pass. A plain {@link Wolf} is
 * tameable and passive-until-provoked by default — neither fits a guardian —
 * so this adds hostile-targeting goals directly onto the instance (Mob's
 * {@code goalSelector}/{@code targetSelector} are public) and separately
 * cancels any interaction with a tagged guardian ({@link #onInteract} below)
 * so it can never actually be tamed. Tagged with {@link #GUARDIAN_TAG} so the
 * sweep can count existing guardians per outbreak without any extra
 * bookkeeping of its own.
 *
 * Follows the same throttling shape as {@link com.lotusblight.spread.roots.RootGrowthEngine}:
 * a periodic sweep bounded by active player range, at most one spawn per
 * outbreak per sweep.
 */
public final class GuardianManager {

    public static final String GUARDIAN_TAG = "lotus_guardian";
    private static final String GUARDIAN_PHASE_KEY = "LotusGuardianPhase";
    private static final int PHASE3_POWDER_DROPS = 2;
    private static final int PHASE4_POWDER_DROPS = 5;
    /** 1-in-8 per guardian kill — rare enough to feel like a real find, not a guaranteed second drop alongside the powder. */
    private static final int SCIENTIST_PAGE_DROP_CHANCE = 8;

    private static final int SWEEP_INTERVAL_TICKS = 100;
    private static final int MIN_GUARDIAN_PHASE = 3;
    private static final int MAX_GUARDIANS_PER_OUTBREAK = 2;
    private static final int SPAWN_SEARCH_RADIUS = 10;
    private static final int SPAWN_SEARCH_ATTEMPTS = 6;

    /**
     * A live proximity scan (countNearbyGuardians, GUARDIAN_CHECK_RADIUS=20 from the anchor) was
     * the ONLY thing capping guardian population - but guardians are plain Wolves with
     * setPersistenceRequired() (never naturally despawn) and normal Wolf AI, which wanders and
     * chases fleeing players far past 20 blocks. Once a guardian wandered outside that radius, the
     * sweep stopped counting it as "belonging" to its outbreak and happily spawned another one on
     * the next 100-tick pass, with no upper bound - a long-lived outbreak could accumulate an
     * ever-growing, never-despawning wolf pack over enough real time. Tracks actual spawned
     * guardian UUIDs per outbreak instead of relying on where they currently happen to be standing.
     */
    private final Map<UUID, List<UUID>> outbreakGuardians = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            sweepLevel(level);
            markGuardianSightings(level);
            dropStaleAllianceTargets(level);
        }
    }

    /**
     * "шабаке злые до сих пор даже после принятия альянса" - the ALLIANCE-exclusion predicate on
     * NearestAttackableTargetGoal only ever runs when a guardian is picking a NEW target
     * (canUse()); it is never re-checked while an existing attack is already in progress
     * (MeleeAttackGoal just keeps swinging at whatever Mob#getTarget() currently is, and vanilla's
     * own TargetGoal#canContinueToUse doesn't re-test the selector predicate either). A guardian
     * that started attacking a player a moment before they joined the Lotus would otherwise just
     * keep attacking them forever - the predicate closes the door to a NEW fight, but never ends
     * one already running. Same sweep cadence as the rest of this class; a few seconds' delay
     * before an angry guardian visibly calms down is an acceptable trade for not scanning every tick.
     */
    private void dropStaleAllianceTargets(ServerLevel level) {
        for (List<UUID> tracked : outbreakGuardians.values()) {
            for (UUID guardianId : tracked) {
                if (!(level.getEntity(guardianId) instanceof Wolf wolf)) continue;
                if (wolf.getTarget() instanceof Player target && LotusPlayerState.hasJoinedLotus(target)) {
                    wolf.setTarget(null);
                }
            }
        }
    }

    private static final double GUARDIAN_SIGHT_RADIUS = 24.0;

    /**
     * "(!) Стражи?" dialogue aside - the World Lotus warning a player not to kill her guardians
     * only makes sense once the player has actually met one. Same sweep cadence as
     * {@link #sweepLevel} rather than a dedicated per-tick check, since missing a sighting by up to
     * 100 ticks costs nothing (the aside just unlocks a little later).
     */
    private void markGuardianSightings(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (com.lotusblight.data.LotusPlayerState.hasSeenGuardian(player)) continue;
            var box = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(GUARDIAN_SIGHT_RADIUS);
            boolean sawGuardian = !level.getEntitiesOfClass(Wolf.class, box, w -> w.getTags().contains(GUARDIAN_TAG)).isEmpty();
            if (!sawGuardian) continue;
            com.lotusblight.data.LotusPlayerState.setSeenGuardian(player);
            com.lotusblight.map.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.lotusblight.map.PlayerStateSyncPacket(
                            com.lotusblight.data.LotusPlayerState.getDialogueBranch(player),
                            com.lotusblight.data.LotusPlayerState.hasFullMapVisibility(player),
                            com.lotusblight.data.LotusPlayerState.hasHeardInnerVoice(player), true,
                            com.lotusblight.data.LotusPlayerState.getAllianceCleanseUses(player)));
        }
    }

    private void sweepLevel(ServerLevel level) {
        OutbreakSavedData data = OutbreakSavedData.get(level);
        double activeBlocks = LotusConfig.ACTIVE_CHUNK_RADIUS.get() * 16.0;
        double activeRangeSq = activeBlocks * activeBlocks;

        for (OutbreakRecord outbreak : new ArrayList<>(data.allOutbreaks())) {
            if (outbreak.phase() < MIN_GUARDIAN_PHASE) continue;
            if (!level.hasChunkAt(outbreak.pos())) continue;
            // Same rule InfectionSpreadEngine#isInBlessingBiome already applies to spread itself -
            // the blessing biome is the infection's one sanctuary from spread, so it shouldn't be
            // patrolled by the infection's own guardians either - a meteorite impact site's own
            // "world property" (see MeteoriteSpreadEngine#isBlessingTerritory) counts the same way.
            if (level.getBiome(outbreak.pos()).is(com.lotusblight.registry.ModBiomes.BLESSING_BIOME)
                    || com.lotusblight.spread.MeteoriteSpreadEngine.isBlessingTerritory(level, outbreak.pos())) continue;
            if (!withinActiveRange(level, outbreak.pos(), activeRangeSq)) continue;
            if (livingGuardianCount(level, outbreak.id(), outbreak.pos()) >= MAX_GUARDIANS_PER_OUTBREAK) continue;

            trySpawnGuardian(level, outbreak);
        }
    }

    /**
     * Prunes dead/unloaded guardians from this outbreak's tracked list, then returns how many are
     * actually still alive.
     *
     * outbreakGuardians is a plain in-memory field, reset to empty every time a fresh GuardianManager
     * is constructed - which happens once per mod-constructor run, i.e. once per server process start
     * (not once per world). Guardians themselves are real Wolves with setPersistenceRequired(), so
     * they survive a restart just fine in the world - only the tracking map forgot about them,
     * letting trySpawnGuardian spawn a fresh batch on top of survivors after every restart with no
     * upper bound. The first time this outbreak is seen by a new GuardianManager instance (no tracked
     * list yet), reconcile by scanning for already-tagged guardian Wolves near its anchor instead of
     * assuming there are none.
     */
    private int livingGuardianCount(ServerLevel level, UUID outbreakId, BlockPos anchorPos) {
        List<UUID> tracked = outbreakGuardians.get(outbreakId);
        if (tracked == null) {
            var box = new net.minecraft.world.phys.AABB(anchorPos).inflate(GUARDIAN_SIGHT_RADIUS);
            tracked = new ArrayList<>();
            for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, box, w -> w.getTags().contains(GUARDIAN_TAG))) {
                tracked.add(wolf.getUUID());
            }
            outbreakGuardians.put(outbreakId, tracked);
        }
        tracked.removeIf(guardianId -> !(level.getEntity(guardianId) instanceof Wolf wolf) || !wolf.isAlive());
        return tracked.size();
    }

    private boolean withinActiveRange(ServerLevel level, BlockPos pos, double rangeSq) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= rangeSq) return true;
        }
        return false;
    }

    private void trySpawnGuardian(ServerLevel level, OutbreakRecord outbreak) {
        BlockPos spawnPos = findSpawnPos(level, outbreak.pos());
        if (spawnPos == null) return;

        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) return;

        wolf.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null, null);
        wolf.addTag(GUARDIAN_TAG);
        wolf.setCustomName(Component.literal("Страж лотоса"));
        wolf.setCustomNameVisible(true);
        wolf.setPersistenceRequired();
        // Hostile goals are added in onGuardianJoin once addFreshEntity fires the join event.
        applyGuardianStats(wolf, outbreak.phase());
        // Read back by onDrops below - a plain Wolf has no phase of its own, so it has to be
        // stashed somewhere that survives to death.
        wolf.getPersistentData().putInt(GUARDIAN_PHASE_KEY, outbreak.phase());

        level.addFreshEntity(wolf);
        outbreakGuardians.computeIfAbsent(outbreak.id(), id -> new ArrayList<>()).add(wolf.getUUID());
    }

    /**
     * Goals live only on the in-memory entity - nothing saves them to NBT. A guardian coming back
     * from disk (server restart, chunk reload) is rebuilt as a plain vanilla Wolf, so the hostile
     * goals have to be put back every time one joins a level, not just once at spawn.
     */
    @SubscribeEvent
    public void onGuardianJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Wolf wolf) || wolf.isTame() || !wolf.getTags().contains(GUARDIAN_TAG)) return;
        addHostileGoals(wolf);
    }

    public static void addHostileGoals(Wolf wolf) {
        // Vanilla Wolf only fights back once provoked - a guardian needs to actively hunt players
        // on sight instead, so these are added on top of (not instead of) Wolf's own default goals.
        wolf.goalSelector.addGoal(2, new GuardianAttackGoal(wolf));
        // A player who has joined the lotus (ALLIANCE branch) is helping this outbreak grow - see
        // InfectionSpreadEngine#branchInfluence - so its own guardians recognizing and sparing them
        // is the other half of that same choice actually mattering, not just a spread-rate number.
        wolf.targetSelector.addGoal(1, new GuardianHuntGoal(wolf));
    }

    /**
     * Own subclasses so taming can strip exactly these two - a plain instanceof MeleeAttackGoal /
     * NearestAttackableTargetGoal check also removed vanilla Wolf's own attack and skeleton-hunting
     * goals, leaving the new pet unable to fight for its owner.
     */
    private static final class GuardianAttackGoal extends MeleeAttackGoal {
        GuardianAttackGoal(Wolf wolf) {
            super(wolf, 1.0, true);
        }
    }

    private static final class GuardianHuntGoal extends NearestAttackableTargetGoal<Player> {
        GuardianHuntGoal(Wolf wolf) {
            super(wolf, Player.class, true, target -> target instanceof Player player && !LotusPlayerState.hasJoinedLotus(player));
        }
    }

    /** Vanilla Wolf baseline, used as the phase-3 (fresh mature outbreak) tier. */
    private static final double PHASE3_MAX_HEALTH = 8.0;
    private static final double PHASE3_ATTACK_DAMAGE = 4.0;
    /** Phase 4 (mini-biome) guardians read as a real escalation, not just a bigger flower bed. */
    private static final double PHASE4_MULTIPLIER = 2.0;

    public static void applyGuardianStats(Wolf guardian, int phase) {
        double multiplier = phase >= 4 ? PHASE4_MULTIPLIER : 1.0;

        var healthAttr = guardian.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(PHASE3_MAX_HEALTH * multiplier);
            guardian.setHealth((float) guardian.getMaxHealth());
        }
        var damageAttr = guardian.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(PHASE3_ATTACK_DAMAGE * multiplier);
        }

        if (phase >= 4) {
            // Distinct name so a player can tell the more dangerous tier apart before engaging.
            guardian.setCustomName(Component.literal("Сердце-страж лотоса"));
        }
    }

    /**
     * A plain Wolf drops nothing on death. Killing a guardian is the only source of combat risk
     * this mod has, so it needs a payoff - dropping {@link ModItems#CLEANSING_POWDER}, the RESISTANCE
     * branch's own anti-infection tool, closes the loop: fighting off a guardian directly funds
     * pushing its outbreak back, instead of powder only ever coming from the villager trade.
     */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.getTags().contains(GUARDIAN_TAG)) return;
        int phase = wolf.getPersistentData().getInt(GUARDIAN_PHASE_KEY);
        int count = phase >= 4 ? PHASE4_POWDER_DROPS : PHASE3_POWDER_DROPS;
        ItemEntity drop = new ItemEntity(wolf.level(), wolf.getX(), wolf.getY(), wolf.getZ(),
                new ItemStack(ModItems.CLEANSING_POWDER.get(), count));
        event.getDrops().add(drop);

        // Separate low-probability roll, not a second guaranteed drop alongside the powder above -
        // "Страницы дневника Объекта Ноль" (see ScientistPageItem/ObjectZeroPages) needs to read as
        // a rare find from actually fighting a guardian, the mod's one source of combat risk, not
        // just another guaranteed reward that dilutes the powder's own payoff.
        // "Постоянный дроп после получения страниц дневника" - createRandomStack() ignored what the
        // killer already had, so a lucky/unlucky streak of rolls could hand out the same variant
        // over and over forever with no way to ever complete the set. Only rolls this at all when
        // the kill can be attributed to a real player, and lets ScientistPageItem pick a variant
        // that specific player doesn't have yet - it returns null (no drop) once they own all 5.
        if (event.getSource().getEntity() instanceof Player killer
                && wolf.level().getRandom().nextInt(SCIENTIST_PAGE_DROP_CHANCE) == 0) {
            ItemStack pageStack = com.lotusblight.item.ScientistPageItem.createStackForFinder(wolf.level().getRandom(), killer);
            if (pageStack != null) {
                event.getDrops().add(new ItemEntity(wolf.level(), wolf.getX(), wolf.getY(), wolf.getZ(), pageStack));
            }
        }

        if (event.getSource().getEntity() instanceof ServerPlayer allianceKiller
                && LotusPlayerState.getDialogueBranch(allianceKiller) == LotusPlayerState.BRANCH_ALLIANCE) {
            onAllianceGuardianKilled(allianceKiller);
        }
    }

    /** "Часть лотоса ненавидит, когда собаки ЛЮБЯТ" - see LotusPlayerState#betrayAlliance. */
    public static final int ALLIANCE_BETRAYAL_KILL_COUNT = 20;

    private void onAllianceGuardianKilled(ServerPlayer player) {
        int total = LotusPlayerState.incrementAllianceGuardianKills(player);
        LotusPlayerState.addReputation(player, com.lotusblight.data.Faction.LOTUS, -3);
        if (total != ALLIANCE_BETRAYAL_KILL_COUNT) return;
        triggerBetrayal(player);
    }

    /**
     * "ты будешь испепелён мной. Если выживешь то лотос не захватит тебя" - the one-way switch onto
     * the traitor path, reached either by killing 20 guardians or by hitting
     * ALLIANCE_CLEANSE_BETRAYAL_USES. Plays out as a real ordeal, not just a chat message: the
     * "Нудная лекция" cutscene (see WorldLotusLectureOverlay - screen shake, hijacked mouse look),
     * paired with actual incineration (Blindness + fire damage the player has to survive).
     */
    private static final int INCINERATION_DURATION_TICKS = 20 * 8;
    private static final float INCINERATION_DAMAGE = 8.0f;

    private static void triggerBetrayal(ServerPlayer player) {
        if (!LotusPlayerState.betrayAlliance(player)) return;

        LotusPlayerState.setHeardWorldLotusLecture(player);
        ChatOverhaulBranchColor.applyBranchColor(player, LotusPlayerState.BRANCH_RESISTANCE);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayerStateSyncPacket(
                LotusPlayerState.getDialogueBranch(player), LotusPlayerState.hasFullMapVisibility(player),
                LotusPlayerState.hasHeardInnerVoice(player), LotusPlayerState.hasSeenGuardian(player),
                LotusPlayerState.getAllianceCleanseUses(player)));

        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, INCINERATION_DURATION_TICKS, 0));
        player.setSecondsOnFire(INCINERATION_DURATION_TICKS / 20);
        player.hurt(player.damageSources().onFire(), INCINERATION_DAMAGE);
    }

    private static final int ALLIANCE_CLEANSE_LOTONIRIYA_USES = 20;
    private static final int ALLIANCE_CLEANSE_WITHER_USES = 24;
    private static final int ALLIANCE_CLEANSE_BETRAYAL_USES = 30;
    /** Same duration LotusMimicBlock already uses for a Lotoniriya application - kept consistent instead of a new one-off number. */
    private static final int LOTONIRIYA_DURATION_TICKS = 20 * 18;
    private static final int CLEANSE_WITHER_DURATION_TICKS = 20 * 10;

    /**
     * The escalating punishment spec for an ALLIANCE player who keeps cleansing her own infection:
     * 18 uses turns the dialogue warning blunt (see LotusDialogueLibrary#cleanseWarningLine), 20
     * applies Lotoniriya immediately, 24 reapplies it and adds Regeneration-stripping + a 10s Wither,
     * 30 is a full betrayal onto the traitor path - the same one-way switch a guardian-kill
     * betrayal reaches, just through cleansing instead of combat.
     */
    public static void onAllianceCleanseUsesChanged(ServerPlayer player, int uses) {
        if (uses == ALLIANCE_CLEANSE_LOTONIRIYA_USES || uses == ALLIANCE_CLEANSE_WITHER_USES) {
            player.addEffect(new MobEffectInstance(ModEffects.LOTONIRIYA.get(), LOTONIRIYA_DURATION_TICKS, 0, false, true, true));
        }
        if (uses == ALLIANCE_CLEANSE_WITHER_USES) {
            player.removeEffect(MobEffects.REGENERATION);
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, CLEANSE_WITHER_DURATION_TICKS, 0));
        }
        if (uses == ALLIANCE_CLEANSE_BETRAYAL_USES) {
            triggerBetrayal(player);
        }
    }

    private static final double ALLY_DEFENSE_RADIUS = 24.0;

    /**
     * "Когда ты в альянсе волки ничего из себя не представляют и просто являются бесполезными
     * союзниками" - guardians already spare ALLIANCE players (never hunt them), but that only ever
     * made them neutral, not actually allied - they gave RESISTANCE a real threat/reward loop and
     * gave ALLIANCE nothing back at all. Any nearby guardian now steps in and targets whatever just
     * hurt an ALLIANCE player, the same "protect the ally" role a real companion would play,
     * instead of standing there as pure decoration while their own side's player gets hit.
     */
    @SubscribeEvent
    public void onAllyHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !LotusPlayerState.hasJoinedLotus(victim)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;

        var box = new net.minecraft.world.phys.AABB(victim.blockPosition()).inflate(ALLY_DEFENSE_RADIUS);
        for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, box, w -> w.getTags().contains(GUARDIAN_TAG))) {
            wolf.setTarget(attacker);
        }
    }

    private static final String NEUTRALIZED_BY_KEY = "LotusNeutralizedBy";
    /** "Повелитель лотоса...или пушистых хвостов?" - fires once a player's lifetime tamed-guardian count reaches this. */
    public static final int PACK_ADVANCEMENT_SIZE = 13;

    /**
     * A tagged guardian is a hostile Wolf, not a pet by default - block every interaction with it
     * (taming with bones, sitting, breeding, etc. all go through this same entry point on a Wolf)
     * so a player can't just right-click it into becoming a tame ally mid-fight.
     *
     * RESISTANCE gets a real, deliberate path around that instead: hand a still-hostile guardian a
     * lotus_alloy ingot first - it stops targeting whoever fed it (tracked per-wolf, not a blanket
     * "guardians are friendly now") without becoming an ally yet. THEN a bone on that same,
     * already-calmed guardian actually tames it. Once tamed it's a normal Wolf pet again (own
     * hostile goals stripped), not a guardian anymore.
     */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Wolf wolf) || !wolf.getTags().contains(GUARDIAN_TAG)) return;
        if (wolf.isTame()) return; // already someone's pet (a past guardian) - let normal Wolf interactions through
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);

        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_RESISTANCE) return;
        ItemStack held = player.getItemInHand(event.getHand());

        if (held.is(ModItems.LOTUS_ALLOY.get())) {
            wolf.getPersistentData().putUUID(NEUTRALIZED_BY_KEY, player.getUUID());
            wolf.setTarget(null);
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.displayClientMessage(Component.literal("Страж принюхивается к слитку и больше не видит в тебе врага."), true);
            return;
        }

        boolean neutralizedByThisPlayer = wolf.getPersistentData().hasUUID(NEUTRALIZED_BY_KEY)
                && wolf.getPersistentData().getUUID(NEUTRALIZED_BY_KEY).equals(player.getUUID());
        if (held.is(net.minecraft.world.item.Items.BONE) && neutralizedByThisPlayer) {
            wolf.removeTag(GUARDIAN_TAG);
            wolf.goalSelector.removeAllGoals(goal -> goal instanceof GuardianAttackGoal);
            wolf.targetSelector.removeAllGoals(goal -> goal instanceof GuardianHuntGoal);
            wolf.tame(player);
            wolf.setCustomNameVisible(false);
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.displayClientMessage(Component.literal("Страж лотоса признал тебя своим."), true);

            LotusPlayerState.addReputation(player, com.lotusblight.data.Faction.LOTUS, 3);
            int total = LotusPlayerState.incrementTamedGuardianCount(player);
            if (total == PACK_ADVANCEMENT_SIZE) {
            }
        }
    }

    @SubscribeEvent
    public void onPagePickedUp(PlayerEvent.ItemPickupEvent event) {
        ItemStack stack = event.getStack();
        if (!stack.is(ModItems.SCIENTIST_PAGE.get())) return;
        LotusPlayerState.markScientistPageFound(event.getEntity(), com.lotusblight.item.ScientistPageItem.variantOf(stack));
    }

    /** A flat, air-above-solid-ground spot within radius of the anchor, avoiding water/lava. */
    private BlockPos findSpawnPos(ServerLevel level, BlockPos anchor) {
        for (int i = 0; i < SPAWN_SEARCH_ATTEMPTS; i++) {
            int dx = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            int dz = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            BlockPos candidate = anchor.offset(dx, 0, dz);
            if (!level.hasChunkAt(candidate)) continue;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
            BlockPos ground = new BlockPos(candidate.getX(), surfaceY - 1, candidate.getZ());
            BlockPos above = ground.above();
            if (level.getBlockState(ground).isAir() || level.getFluidState(ground).isSource()) continue;
            if (level.getBlockState(ground).is(Blocks.LAVA) || level.getFluidState(ground).is(net.minecraft.world.level.material.Fluids.LAVA)) continue;
            // WORLD_SURFACE counts lily pads (and anything else that blocks motion) as "ground",
            // so this used to regularly find a lily pad - or, worse, LOTUS_SHOOT sitting ON TOP of
            // a lily pad (see InfectionSpreadEngine's own pad+shoot pairs around the main anchor) -
            // as the highest point over open water and spawn the guardian standing right on it, in
            // the middle of a lake ("стражи спавнятся в воде"). Checking LILY_PAD alone missed the
            // shoot-on-pad case entirely, since `ground` there resolves to the shoot, not the pad
            // underneath it - excluding LOTUS_SHOOT directly closes that specific gap, and the
            // fluid-below check on top catches any OTHER thin floating plant sitting right on
            // water, regardless of which block it happens to be.
            if (level.getBlockState(ground).is(Blocks.LILY_PAD) || level.getBlockState(ground).is(ModBlocks.LOTUS_SHOOT.get())) continue;
            if (!level.getFluidState(ground.below()).isEmpty()) continue;
            if (!level.getBlockState(above).isAir()) continue;
            return above.immutable();
        }
        return null;
    }
}
