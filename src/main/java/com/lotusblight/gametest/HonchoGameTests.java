package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.HonchoSavedData;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.entity.HonchoEntity;
import com.lotusblight.entity.HonchoMeetingManager;
import com.lotusblight.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side run-throughs of the trip meeting (HonchoMeetingManager) with a mock player - every
 * branch of the choice, the ways it can be interrupted, and the one-Honcho-per-world rule - plus the
 * cleansing powder on a lotus shoot. Honcho's state is world-wide, so each test sits in its own batch
 * and they run one after another.
 *
 * Only for ./gradlew runGameTestServer - the package is left out of the release jar.
 */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class HonchoGameTests {
    private static final String ARENA = "honcho_arena";
    /** Past the new player's spawn invulnerability, so damage checks mean something. */
    private static final int SETTLE_TICKS = 70;

    private HonchoGameTests() {}

    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        // Every Honcho left over from earlier tests goes first - with the id cleared, the meeting
        // would adopt one of them, and the framework kills it when it clears that test's area.
        for (HonchoEntity leftover : helper.getLevel().getEntities(ModEntities.HONCHO.get(), e -> true)) {
            leftover.discard();
        }
        HonchoSavedData.get(helper.getLevel().getServer().overworld()).reset();
        ServerPlayer player = mockPlayer(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(helper.getLevel(), spot.x, spot.y, spot.z, 0.0f, 0.0f);
        player.setGameMode(GameType.SURVIVAL);
        LotusPlayerState.resetHonchoProgress(player);
        return player;
    }

    /**
     * GameTestHelper#makeMockServerPlayerInLevel hands the player a Connection with no channel, and
     * Forge reads the channel on every mod packet sent to a player - the scene's own packets would
     * throw. An EmbeddedChannel gives it one that simply swallows everything.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                // Unique names - a player left behind by a failed test must never catch commands aimed at the next one.
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hct" + Integer.toHexString(helper.getLevel().random.nextInt())));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static HonchoEntity honcho(GameTestHelper helper) {
        HonchoEntity honcho = HonchoMeetingManager.findLoadedHoncho(helper.getLevel().getServer());
        check(honcho != null, "Хончо не найден");
        return honcho;
    }

    private static void start(ServerPlayer player) {
        String error = HonchoMeetingManager.forceMeeting(player);
        check(error == null, "встреча не началась: " + error);
    }

    /** During the meeting he must stand FACING the player (body and head), not turned away: the angle off the player's direction is reported. */
    @GameTest(template = ARENA, batch = "honcho_facing", timeoutTicks = 200)
    public static void meetingFacesThePlayer(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);
        helper.runAfterDelay(60, () -> {
            double dx = player.getX() - honcho.getX(), dz = player.getZ() - honcho.getZ();
            float want = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;      // the yaw that looks at the player
            float body = net.minecraft.util.Mth.wrapDegrees(honcho.yBodyRot - want);
            float head = net.minecraft.util.Mth.wrapDegrees(honcho.getYHeadRot() - want);
            check(Math.abs(body) < 25.0f, "тело Хончо развёрнуто от игрока на " + Math.round(body) + " градусов (yaw тела " + Math.round(honcho.yBodyRot) + ", нужен " + Math.round(want) + ")");
            check(Math.abs(head) < 25.0f, "голова Хончо развёрнута от игрока на " + Math.round(head) + " градусов");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_accept", timeoutTicks = 200)
    public static void acceptTakesHisHand(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);
        check(HonchoMeetingManager.isInMeeting(player), "сессии нет");
        check(honcho.isInMeeting(), "Хончо не ждёт");
        check(honcho.getScene() == HonchoEntity.SCENE_OFFER_HAND, "нет протянутой руки");
        check(honcho.distanceTo(player) < 3.0f, "Хончо далеко: " + honcho.distanceTo(player));
        check(LotusPlayerState.isHonchoMeetingPending(player), "флаг ожидания не стоит");

        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_ACCEPT);
        check(LotusPlayerState.hasMetHoncho(player), "встреча не засчитана");
        check(!LotusPlayerState.isHonchoMeetingPending(player), "флаг ожидания остался");
        check(honcho.getScene() == HonchoEntity.SCENE_HAPPY, "нет радости");

        helper.runAfterDelay(25, () -> check(honcho.getScene() == HonchoEntity.SCENE_PAT, "не гладит по голове"));
        helper.runAfterDelay(80, () -> {
            // Same cutscene goes on into his story: still held, the plea is pending.
            check(HonchoMeetingManager.isInMeeting(player) && honcho.isInMeeting(), "история не продолжила ту же сцену");
            check(LotusPlayerState.isHonchoAssistantPending(player), "история не началась");
            HonchoMeetingManager.onStoryQuestion(player);
            check(honcho.getScene() == HonchoEntity.SCENE_PRAY, "на просьбе не встал на колени");
            LotusPlayerState.setHonchoAssistantPending(player, false);
            HonchoMeetingManager.onAssistantAnswered(player);
            check(!HonchoMeetingManager.isInMeeting(player), "сессия не закрылась после ответа");
            check(!honcho.isInMeeting() && honcho.getScene() == HonchoEntity.SCENE_NONE, "Хончо застрял в сцене");
            check(!HonchoSavedData.get(helper.getLevel().getServer().overworld()).isGone(), "ушёл после согласия");
            check(!(honcho instanceof net.minecraft.world.entity.monster.Enemy), "Хончо всё ещё монстр");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_decline", timeoutTicks = 200)
    public static void decliningTwiceSendsHimAway(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);

        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_DECLINE);
        check(honcho.getScene() == HonchoEntity.SCENE_PRAY, "не молится после первого нет");
        check(HonchoMeetingManager.isInMeeting(player), "сессия закрылась после первого нет");
        check(!LotusPlayerState.hasMetHoncho(player), "засчитано слишком рано");

        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_DECLINE);
        check(LotusPlayerState.hasMetHoncho(player), "второе нет не засчитано");
        // A stray accept after the final no must not bring him back.
        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_ACCEPT);

        helper.runAfterDelay(60, () -> {
            HonchoSavedData data = HonchoSavedData.get(helper.getLevel().getServer().overworld());
            check(data.isGone(), "не ушёл навсегда (" + honcho.getRemovalReason() + ")");
            check(honcho.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED, "исчез не так: " + honcho.getRemovalReason());
            check(!honcho.isAlive(), "сущность осталась");
            check(HonchoMeetingManager.forceMeeting(player) != null, "встреча прошла с ушедшим Хончо");
            HonchoEntity copy = ModEntities.HONCHO.get().create(helper.getLevel());
            check(copy != null, "не создать копию");
            copy.moveTo(player.getX(), player.getY(), player.getZ());
            check(!helper.getLevel().addFreshEntity(copy), "ушедший Хончо вернулся через спавн");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_confirm", timeoutTicks = 200)
    public static void yesOnConfirmStillAccepts(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);
        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_DECLINE);
        HonchoMeetingManager.handleChoice(player, HonchoMeetingManager.CHOICE_ACCEPT);
        check(honcho.getScene() == HonchoEntity.SCENE_HAPPY, "«Да» на «Точно?» не приняло руку");
        check(LotusPlayerState.hasMetHoncho(player), "встреча не засчитана");
        helper.runAfterDelay(80, () -> {
            check(!HonchoSavedData.get(helper.getLevel().getServer().overworld()).isGone(), "ушёл после согласия");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_cancel", timeoutTicks = 200)
    public static void cancelLeavesNoTrace(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            start(player);
            HonchoEntity honcho = honcho(helper);
            float health = player.getHealth();
            player.hurt(helper.getLevel().damageSources().generic(), 4.0f);
            check(player.getHealth() == health, "игрок получил урон во время сцены");

            HonchoMeetingManager.cancel(player);
            check(!HonchoMeetingManager.isInMeeting(player), "сессия осталась");
            check(!honcho.isInMeeting(), "Хончо ждёт дальше");
            check(!LotusPlayerState.hasMetHoncho(player) && !LotusPlayerState.isHonchoMeetingPending(player), "остались флаги");
            player.invulnerableTime = 0;
            player.hurt(helper.getLevel().damageSources().generic(), 4.0f);
            check(player.getHealth() < health, "защита не снялась после сцены");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_abort", timeoutTicks = 200)
    public static void walkingAwayAbortsTheScene(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);
        player.teleportTo(helper.getLevel(), player.getX() + 40, player.getY(), player.getZ(), 0.0f, 0.0f);
        helper.runAfterDelay(3, () -> {
            check(!HonchoMeetingManager.isInMeeting(player), "сцена не прервалась");
            check(!honcho.isInMeeting(), "Хончо ждёт дальше");
            check(!LotusPlayerState.isHonchoMeetingPending(player) && !LotusPlayerState.hasMetHoncho(player), "флаги после прерывания");
            leave(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "honcho_single", timeoutTicks = 100)
    public static void onlyOneHonchoPerWorld(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        HonchoEntity first = HonchoMeetingManager.summonTo(player);
        check(first != null, "spawn не поставил Хончо");
        HonchoEntity again = HonchoMeetingManager.summonTo(player);
        check(again == first, "второй spawn создал нового вместо переноса");
        HonchoEntity copy = ModEntities.HONCHO.get().create(helper.getLevel());
        check(copy != null, "не создать копию");
        copy.moveTo(player.getX(), player.getY(), player.getZ());
        check(!helper.getLevel().addFreshEntity(copy), "в мире оказалось два Хончо");
        first.setBaby(true);
        check(!first.isBaby(), "Хончо стал маленьким");
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "honcho_commands", timeoutTicks = 100)
    public static void everyHonchoCommandRuns(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var commands = helper.getLevel().getServer().getCommands();
        String[] lines = {
                "lotus honcho spawn", "lotus honcho info", "lotus honcho info " + player.getGameProfile().getName(),
                "lotus honcho tp", "lotus honcho scene pat", "lotus honcho scene none",
                "lotus honcho meeting", "lotus honcho cancel", "lotus honcho assistant",
                "lotus honcho reset player " + player.getGameProfile().getName(), "lotus honcho reset world"};
        for (String line : lines) {
            int result = commands.performPrefixedCommand(source, line);
            check(result > 0, "команда не сработала: /" + line);
            if (line.endsWith("scene pat")) check(honcho(helper).getScene() == HonchoEntity.SCENE_PAT, "scene pat не включил анимацию");
            if (line.endsWith("meeting")) check(HonchoMeetingManager.isInMeeting(player), "meeting не начал встречу");
            if (line.endsWith("cancel")) check(!HonchoMeetingManager.isInMeeting(player), "cancel не прервал");
            if (line.endsWith("assistant")) check(LotusPlayerState.isHonchoAssistantPending(player), "assistant не задал вопрос");
        }
        check(!LotusPlayerState.isHonchoAssistantPending(player) && !LotusPlayerState.hasAskedHonchoAssistant(player), "reset player оставил флаги");
        check(HonchoMeetingManager.findLoadedHoncho(helper.getLevel().getServer()) == null, "reset world оставил Хончо");
        check(!HonchoSavedData.get(helper.getLevel().getServer().overworld()).isSpawned(), "reset world не сбросил мир");
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "honcho_summon", timeoutTicks = 100, attempts = 1)
    public static void summonedHonchoIsPlain(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        // /summon rolls Zombie's spawn randomness every time - repeat it so a rare roll can't slip by.
        for (int i = 0; i < 40; i++) {
            check(helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "summon lotusblight:honcho ~ ~ ~") > 0, "summon не сработал");
            HonchoEntity honcho = honcho(helper);
            check(!honcho.isBaby() && honcho.getVehicle() == null, "маленький или верхом");
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                check(honcho.getItemBySlot(slot).isEmpty(), "снаряжение в слоте " + slot);
            }
            check(honcho.getMaxHealth() == 50.0f && honcho.getHealth() == 50.0f, "здоровье " + honcho.getHealth() + "/" + honcho.getMaxHealth());
            honcho.discard();
            HonchoSavedData.get(helper.getLevel().getServer().overworld()).reset();
        }
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "honcho_health", timeoutTicks = 100)
    public static void oldSaveGetsTwentyFiveHearts(GameTestHelper helper) {
        HonchoEntity old = ModEntities.HONCHO.get().create(helper.getLevel());
        check(old != null, "не создать");
        check(old.getMaxHealth() == 50.0f, "новый Хончо не с 25 сердцами: " + old.getMaxHealth());
        // A Honcho saved before the change: 10 hearts max, half of them left.
        old.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20.0);
        old.setHealth(10.0f);
        net.minecraft.nbt.CompoundTag saved = old.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        HonchoEntity loaded = ModEntities.HONCHO.get().create(helper.getLevel());
        check(loaded != null, "не создать");
        loaded.load(saved);
        check(loaded.getMaxHealth() == 50.0f, "после загрузки максимум " + loaded.getMaxHealth());
        check(loaded.getHealth() == 25.0f, "после загрузки здоровье " + loaded.getHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "shoot_powder", timeoutTicks = 100)
    public static void powderLeavesShootButSlowsOutbreak(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        var level = helper.getLevel();
        var data = com.lotusblight.data.OutbreakSavedData.get(level);
        var anchor = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var outbreak = data.registerOutbreak(anchor.immutable(), level.getGameTime(), false);
        var shootPos = player.blockPosition().offset(1, 0, 0);
        level.setBlock(shootPos, com.lotusblight.registry.ModBlocks.LOTUS_SHOOT.get().defaultBlockState(), 3);
        check(level.getBlockState(shootPos).is(com.lotusblight.registry.ModBlocks.LOTUS_SHOOT.get()), "росток не встал");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.lotusblight.registry.ModItems.CLEANSING_POWDER.get()));
        net.minecraftforge.common.ForgeHooks.onItemRightClick(player, net.minecraft.world.InteractionHand.MAIN_HAND);

        check(level.getBlockState(shootPos).is(com.lotusblight.registry.ModBlocks.LOTUS_SHOOT.get()), "порошок убрал росток");
        var cleanRiver = level.getServer().getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation(LotusBlight.MODID, "clean_the_river"));
        check(cleanRiver != null, "достижение clean_the_river не загрузилось");
        check(player.getAdvancements().getOrStartProgress(cleanRiver).isDone(), "достижение за порошок не выдано");
        var after = data.allOutbreaks().stream().filter(o -> o.id().equals(outbreak.id())).findFirst().orElse(null);
        check(after != null, "очаг пропал");
        check(after.isSuppressed(level.getGameTime()), "очаг не замедлился");
        check(after.suppressedUntil() - level.getGameTime() >= 20 * 60 * 5 - 1, "замедление короче 5 минут");
        var reloaded = com.lotusblight.data.OutbreakRecord.load(after.save(new net.minecraft.nbt.CompoundTag()));
        check(reloaded.suppressedUntil() == after.suppressedUntil(), "замедление не пережило сохранение");
        check(reloaded.withPhase(3).suppressedUntil() == after.suppressedUntil(), "withPhase потерял замедление");

        data.removeOutbreak(outbreak.id());
        level.setBlock(shootPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "meteorite_stone", timeoutTicks = 200)
    public static void meteoriteStoneKillsOnlyAlliance(GameTestHelper helper) {
        ServerPlayer war = freshPlayer(helper);
        ServerPlayer alliance = mockPlayer(helper);
        ServerPlayer graced = mockPlayer(helper);
        for (ServerPlayer p : new ServerPlayer[]{alliance, graced}) {
            Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
            p.teleportTo(helper.getLevel(), spot.x, spot.y, spot.z, 0.0f, 0.0f);
            p.setGameMode(GameType.SURVIVAL);
        }
        LotusPlayerState.forceDialogueBranch(war, LotusPlayerState.BRANCH_RESISTANCE);
        LotusPlayerState.forceDialogueBranch(alliance, LotusPlayerState.BRANCH_ALLIANCE);
        LotusPlayerState.forceDialogueBranch(graced, LotusPlayerState.BRANCH_ALLIANCE);
        var stonePos = helper.absolutePos(new net.minecraft.core.BlockPos(4, 0, 4));
        helper.getLevel().setBlock(stonePos, com.lotusblight.registry.ModBlocks.METEORITE_STONE.get().defaultBlockState(), 3);
        var stone = helper.getLevel().getBlockState(stonePos);
        // Past spawn invulnerability, so a hit would really land.
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            com.lotusblight.world.MeteoriteStoneBlock.grantImpactGrace(helper.getLevel(), graced);
            for (ServerPlayer p : new ServerPlayer[]{war, alliance, graced}) {
                stone.getBlock().stepOn(helper.getLevel(), stonePos, stone, p);
            }
            stone.getBlock().attack(stone, helper.getLevel(), stonePos, war);
            check(war.isAlive() && war.getHealth() == war.getMaxHealth(), "камень ранил игрока войны");
            check(!alliance.isAlive(), "камень не убил игрока альянса");
            check(graced.isAlive(), "игрок альянса умер во время отсрочки после удара");
        });
        helper.runAfterDelay(SETTLE_TICKS + com.lotusblight.world.MeteoriteStoneBlock.IMPACT_GRACE_TICKS + 5, () -> {
            stone.getBlock().stepOn(helper.getLevel(), stonePos, stone, graced);
            check(!graced.isAlive(), "отсрочка не закончилась");
            for (ServerPlayer p : new ServerPlayer[]{war, alliance, graced}) leave(helper, p);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "spread_surface", timeoutTicks = 200)
    public static void phaseFiveKeepsWaterSurfaceClear(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        var level = helper.getLevel();
        // The GameTest world is an ordinary generated world and the arena may sit underground, while
        // the spread follows the real surface - so the test field is built on the surface above it:
        // a pool on the north half (stone bottom, three layers of water), grass on the south half.
        var corner = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.getX() + 4, corner.getZ() + 4) + 2;
        java.util.function.BiFunction<Integer, Integer, net.minecraft.core.BlockPos> at =
                (x, z) -> new net.minecraft.core.BlockPos(corner.getX() + x, ground, corner.getZ() + z);
        for (int x = -1; x < 10; x++) {
            for (int z = -1; z < 10; z++) {
                var base = at.apply(x, z);
                boolean pool = x >= 0 && x < 9 && z >= 0 && z <= 3;
                level.setBlock(base, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 12; y++) {
                    var state = pool && y <= 3 ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                    level.setBlock(base.above(y), state, 2);
                }
                if (!pool && z >= 5) level.setBlock(base, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (!pool && z < 5) {
                    for (int y = 1; y <= 3; y++) level.setBlock(base.above(y), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        var data = com.lotusblight.data.OutbreakSavedData.get(level);
        var anchor = at.apply(4, 4).above(4);
        var outbreak = data.registerOutbreak(anchor.immutable(), level.getGameTime(), false);
        data.updateOutbreak(outbreak.withPhase(5).withPeakPhase(5).withInfectedBlockCount(com.lotusblight.spread.InfectionPhases.minBlockCountForPhase(5)));
        com.lotusblight.spread.InfectionSpreadEngine.forceTicks(level, 400);

        int topRoots = 0, landRoots = 0, landInfected = 0, seabedInfected = 0;
        java.util.List<net.minecraft.core.BlockPos> shoots = new java.util.ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                var base = at.apply(x, z);
                if (z <= 3) {
                    if (level.getBlockState(base.above(3)).is(com.lotusblight.registry.ModBlocks.LOTUS_ROOTS.get())) topRoots++;
                    if (com.lotusblight.spread.SpreadTables.isInfectedGround(level.getBlockState(base))) seabedInfected++;
                    if (level.getBlockState(base.above(4)).is(com.lotusblight.registry.ModBlocks.LOTUS_SHOOT.get())) shoots.add(base.above(4));
                } else if (z >= 5) {
                    if (com.lotusblight.spread.SpreadTables.isInfectedGround(level.getBlockState(base))) landInfected++;
                    if (level.getBlockState(base.above()).is(com.lotusblight.registry.ModBlocks.LOTUS_ROOTS.get())) landRoots++;
                }
            }
        }
        String stats = "top=" + topRoots + " seabed=" + seabedInfected + " land=" + landInfected + " landRoots=" + landRoots + " shoots=" + shoots.size();
        com.mojang.logging.LogUtils.getLogger().info("spread test: " + stats);
        data.removeOutbreak(outbreak.id());
        leave(helper, player);
        check(topRoots == 0, "корни на поверхности воды: " + stats);
        check(seabedInfected > 0, "дно пруда не заразилось: " + stats);
        check(landInfected > 0, "суша не заразилась: " + stats);
        check(landRoots * 4 <= Math.max(4, landInfected), "корней на суше слишком много: " + stats);
        for (int i = 0; i < shoots.size(); i++) {
            for (int j = i + 1; j < shoots.size(); j++) {
                var a = shoots.get(i);
                var b = shoots.get(j);
                check(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())) > 4, "лотосы ближе 4 блоков: " + a + " " + b);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "lore_triggers", timeoutTicks = 100)
    public static void branchAgeAndPagesFeedTheLore(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        LotusPlayerState.forceDialogueBranch(player, LotusPlayerState.BRANCH_RESISTANCE);
        check(LotusPlayerState.ticksSinceBranchChosen(player) == 0, "время выбора ветки не записалось");
        // Pretend the branch was chosen long ago - the personal thresholds must be reachable.
        LotusPlayerState.setBranchChosenAt(player, helper.getLevel().getGameTime() - com.lotusblight.escape.StarFallEvent.STAR_FALL_PERSONAL_TICKS);
        check(LotusPlayerState.ticksSinceBranchChosen(player) >= com.lotusblight.escape.LotusChaseEvent.CHASE_PERSONAL_TICKS, "порог Побега недостижим");
        check(LotusPlayerState.ticksSinceBranchChosen(player) >= com.lotusblight.escape.StarFallEvent.STAR_FALL_PERSONAL_TICKS, "порог StarFall недостижим");
        LotusPlayerState.forceDialogueBranch(player, LotusPlayerState.BRANCH_UNDECIDED);
        check(LotusPlayerState.ticksSinceBranchChosen(player) == -1, "без ветки отсчёт идёт");

        int total = com.lotusblight.dialogue.ObjectZeroPages.VARIANTS.size();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < total; i++) {
            check(com.lotusblight.item.ScientistPageItem.giveMissingPage(player), "страница не выдалась, хотя не все найдены");
            var pages = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(2),
                    e -> e.getItem().is(com.lotusblight.registry.ModItems.SCIENTIST_PAGE.get()));
            check(pages.size() == 1, "страниц на земле: " + pages.size());
            int variant = com.lotusblight.item.ScientistPageItem.variantOf(pages.get(0).getItem());
            check(seen.add(variant), "выдан дубль страницы " + variant);
            LotusPlayerState.markScientistPageFound(player, variant);
            pages.get(0).discard();
        }
        check(!com.lotusblight.item.ScientistPageItem.giveMissingPage(player), "выдана лишняя страница, когда все найдены");
        leave(helper, player);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "biome_catch_up", timeoutTicks = 100)
    public static void oldPhaseFourOutbreakGetsItsBiome(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        var level = helper.getLevel();
        var data = com.lotusblight.data.OutbreakSavedData.get(level);
        var corner = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.getX() + 4, corner.getZ() + 4);
        var anchor = new net.minecraft.core.BlockPos(corner.getX() + 4, surface, corner.getZ() + 4);
        // An outbreak that reached phase 4 before the rewrite covered the full height.
        var outbreak = data.registerOutbreak(anchor, level.getGameTime(), false);
        data.updateOutbreak(outbreak.withPhase(4).withPeakPhase(4).withInfectedBlockCount(com.lotusblight.spread.InfectionPhases.minBlockCountForPhase(4)));
        check(!level.getBiome(anchor).is(com.lotusblight.registry.ModBiomes.LOTUS_BIOME), "биом уже lotus_marsh до теста");
        com.lotusblight.spread.InfectionSpreadEngine.forceTicks(level, 1);
        var after = data.allOutbreaks().stream().filter(o -> o.id().equals(outbreak.id())).findFirst().orElse(null);
        data.removeOutbreak(outbreak.id());
        leave(helper, player);
        check(after != null && after.biomeConverted(), "очаг не отмечен как переписанный");
        check(level.getBiome(anchor).is(com.lotusblight.registry.ModBiomes.LOTUS_BIOME), "биом на поверхности не стал lotus_marsh");
        check(level.getBiome(anchor.offset(20, 0, 20)).is(com.lotusblight.registry.ModBiomes.LOTUS_BIOME), "биом в радиусе не сменился");
        var reloaded = com.lotusblight.data.OutbreakRecord.load(after.save(new net.minecraft.nbt.CompoundTag()));
        check(reloaded.biomeConverted(), "флаг не пережил сохранение");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "honcho_logout", timeoutTicks = 100)
    public static void logoutMidSceneKeepsItPending(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        start(player);
        HonchoEntity honcho = honcho(helper);
        leave(helper, player);
        check(!HonchoMeetingManager.isInMeeting(player), "сессия пережила выход");
        check(!honcho.isInMeeting(), "Хончо ждёт вышедшего");
        check(LotusPlayerState.isHonchoMeetingPending(player), "неотвеченная сцена не сохранилась до входа");
        helper.succeed();
    }
}
