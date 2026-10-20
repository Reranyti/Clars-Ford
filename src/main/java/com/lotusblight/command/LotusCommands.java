package com.lotusblight.command;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.PlayerStateSyncPacket;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.spread.InfectionPhases;
import com.lotusblight.spread.InfectionSpreadEngine;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

/**
 * Admin/testing commands for the infection systems — spawning, forcing
 * phases/hearts, and overriding per-player dialogue state without having to
 * play through a real outbreak or dialogue every time. All require operator
 * permission (level 2), same as vanilla /gamerule.
 */
public final class LotusCommands {

    private LotusCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotus")
                .requires(source -> source.hasPermission(2))
                .then(outbreakCommands())
                .then(branchCommands())
                .then(normalBranchCommands())
                .then(eventCommands())
                .then(mapCommands())
                .then(glandCommands())
                .then(Commands.literal("timewarp")
                        .then(Commands.argument("passes", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> timewarp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "passes")))))
                .then(worldCommands())
                .then(meteoriteCommands())
                .then(blackHeartCommands())
                .then(borderCommands())
                .then(reputationCommands())
                .then(Commands.literal("story")
                        .executes(ctx -> storyInfo(ctx.getSource()))
                        .then(Commands.literal("beats").executes(ctx -> storyBeats(ctx.getSource())))
                        .then(Commands.literal("beat")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> storySetBeat(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("next").executes(ctx -> storyNext(ctx.getSource())))
                        .then(Commands.literal("dream").executes(ctx -> storyDream(ctx.getSource(), true)))
                        .then(Commands.literal("wake").executes(ctx -> storyDream(ctx.getSource(), false)))
                        .then(Commands.literal("diary")
                                .then(Commands.literal("list").executes(ctx -> storyDiary(ctx.getSource(), "list", null)))
                                .then(Commands.literal("clear").executes(ctx -> storyDiary(ctx.getSource(), "clear", null)))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> storyDiary(ctx.getSource(), "add", com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"))))))
                        .then(Commands.literal("quest")
                                .then(Commands.literal("list").executes(ctx -> storyQuest(ctx.getSource(), "list", null)))
                                .then(Commands.literal("clear").executes(ctx -> storyQuest(ctx.getSource(), "clear", null)))
                                .then(Commands.literal("start")
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> storyQuest(ctx.getSource(), "start", com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("done")
                                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> storyQuest(ctx.getSource(), "done", com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"))))))
                        .then(Commands.literal("dialogue")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> storyDialogue(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("lotus")
                                .then(Commands.literal("on").executes(ctx -> storyLotus(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> storyLotus(ctx.getSource(), false)))))
                .then(Commands.literal("compat").executes(ctx -> compatReport(ctx.getSource())))
                .then(Commands.literal("book").executes(ctx -> openCommandBook(ctx.getSource()))));
    }

    // ---- /lotus event <line> <event> [player] [force]: running the story events by hand --------------
    // One explicit branch per event on purpose (no registry, no generic lookup): a failing command points straight at its event.

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> eventCommands() {
        return Commands.literal("event")
                .then(Commands.literal("cursed").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))));
    }

    private static int reportEvent(CommandSourceStack source, String name, com.lotusblight.event.EventResult result) {
        switch (result) {
            case DONE -> source.sendSuccess(() -> Component.literal("Событие «" + name + "» выполнено."), true);
            case WRONG_LINE -> source.sendFailure(Component.literal("Событие «" + name + "»: игрок не на этой линии (добавь force, чтобы всё равно запустить)."));
            case NOTHING_TO_DO -> source.sendFailure(Component.literal("Событие «" + name + "»: нечего делать (например, пустой инвентарь)."));
        }
        return result == com.lotusblight.event.EventResult.DONE ? 1 : 0;
    }

    // ---- /lotus normalbranch ... (testing the entry) --------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> normalBranchCommands() {
        return Commands.literal("normalbranch")
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            "step " + com.lotusblight.data.LotusPlayerState.getNormalBranchStep(p)
                                    + "/" + com.lotusblight.data.LotusPlayerState.NORMAL_BRANCH_STEPS
                                    + ", closed=" + com.lotusblight.data.LotusPlayerState.isNormalBranchClosed(p)
                                    + ", entered=" + com.lotusblight.data.LotusPlayerState.isNormalBranchEntered(p)
                                    + ", arrived=" + com.lotusblight.data.LotusPlayerState.isNormalBranchArrived(p)
                                    + ", exitPending=" + com.lotusblight.data.LotusPlayerState.isNormalBranchExitPending(p)), false);
                    return 1;
                }))
                .then(Commands.literal("step")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, com.lotusblight.data.LotusPlayerState.NORMAL_BRANCH_STEPS))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    com.lotusblight.data.LotusPlayerState.setNormalBranchStep(p, IntegerArgumentType.getInteger(ctx, "n"));
                                    return 1;
                                })))
                .then(Commands.literal("enter").executes(ctx -> {
                    com.lotusblight.branch.NormalBranchEntry.begin(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("exit").executes(ctx -> {
                    com.lotusblight.branch.NormalBranchExit.start(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("lock").executes(ctx -> {
                    com.lotusblight.branch.NormalBranchLock.engage();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            "Lock written. The mod stands down on the next launch until it is reinstalled."), false);
                    return 1;
                }));
    }

    // ---- /lotus outbreak ... --------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> outbreakCommands() {
        return Commands.literal("outbreak")
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawnOutbreak(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition()), 1))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> spawnOutbreak(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), 1))
                                .then(Commands.argument("phase", IntegerArgumentType.integer(1, com.lotusblight.spread.InfectionPhases.MAX_PHASE))
                                        .executes(ctx -> spawnOutbreak(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                IntegerArgumentType.getInteger(ctx, "phase"))))))
                .then(Commands.literal("list").executes(ctx -> listOutbreaks(ctx.getSource())))
                .then(Commands.literal("setphase")
                        .then(Commands.argument("phase", IntegerArgumentType.integer(1, com.lotusblight.spread.InfectionPhases.MAX_PHASE))
                                .executes(ctx -> setNearestPhase(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "phase")))
                                .then(Commands.argument("progress", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> setNearestPhase(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "phase"),
                                                FloatArgumentType.getFloat(ctx, "progress"))))))
                .then(Commands.literal("heart").executes(ctx -> forceHeart(ctx.getSource())))
                .then(Commands.literal("remove").executes(ctx -> removeNearest(ctx.getSource())));
    }

    private static int spawnOutbreak(CommandSourceStack source, BlockPos pos, int phase) {
        ServerLevel level = source.getLevel();
        if (!level.getBlockState(pos).isAir()) {
            source.sendFailure(Component.literal("Позиция " + pos.toShortString() + " занята — очисти место для якоря."));
            return 0;
        }
        level.setBlock(pos, ModBlocks.INFECTED_LOTUS.get().defaultBlockState(), 3);
        OutbreakSavedData data = OutbreakSavedData.get(level);
        OutbreakRecord record = data.registerOutbreak(pos.immutable(), level.getGameTime(), false)
                .withPhase(phase)
                .withPeakPhase(phase)
                .withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(phase));
        data.updateOutbreak(record);
        // Setting/spawning phase 4 for testing used to unconditionally call promoteAnchorToHeart -
        // silently swapping the anchor for the one-and-only Heart in the world just because you
        // wanted to preview phase 4's numbers/density. That's a real, consequential side effect
        // (see OutbreakSavedData#claimHeart) that should only ever happen through the explicit
        // "heart" command below, never as a side effect of setting a number for a test.
        source.sendSuccess(() -> Component.literal("Очаг создан в " + pos.toShortString() + ", фаза " + phase + "."), true);
        return 1;
    }

    private static int listOutbreaks(CommandSourceStack source) {
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        List<OutbreakRecord> outbreaks = data.allOutbreaks().stream()
                .sorted(Comparator.comparingDouble(o -> o.pos().distSqr(BlockPos.containing(source.getPosition()))))
                .toList();
        if (outbreaks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Очагов нет."), false);
            return 0;
        }
        for (OutbreakRecord record : outbreaks) {
            long suppressedFor = record.suppressedUntil() - source.getLevel().getGameTime();
            source.sendSuccess(() -> Component.literal(String.format("%s фаза %d, блоков %d, %s%s%s",
                    record.pos().toShortString(), record.phase(), record.infectedBlockCount(),
                    record.hidden() ? "скрыт" : "виден",
                    source.getLevel().getBlockState(record.pos()).is(ModBlocks.LOTUS_HEART.get()) ? ", СЕРДЦЕ" : "",
                    suppressedFor > 0 ? ", замедлен ещё " + suppressedFor / 20 + " с" : "")), false);
        }
        return outbreaks.size();
    }

    private static int setNearestPhase(CommandSourceStack source, int phase) {
        return setNearestPhase(source, phase, 0.0f);
    }

    /**
     * progress 0.0 lands exactly on this phase's own minimum block count (the old, only, behaviour
     * - straight to the very start of the phase, never partway through it or near its end). 1.0
     * lands just short of the NEXT phase's threshold. Phase 4 has no upper threshold to interpolate
     * against, so progress there scales against InfectionPhases#progressWithinPhase's own 120-block
     * span instead.
     */
    private static int setNearestPhase(CommandSourceStack source, int phase, float progress) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        int lower = InfectionPhases.minBlockCountForPhase(phase);
        int upper = phase < InfectionPhases.MAX_PHASE ? InfectionPhases.minBlockCountForPhase(phase + 1) - 1 : lower + 120;
        int blockCount = lower + Math.round(net.minecraft.util.Mth.clamp(progress, 0f, 1f) * (upper - lower));
        // Setting a phase for testing is just numbers - it should never silently swap the anchor
        // for the world's one Heart as a side effect. Use the explicit "heart" command for that.
        data.updateOutbreak(nearest.withPhase(phase).withPeakPhase(phase).withInfectedBlockCount(blockCount));
        source.sendSuccess(() -> Component.literal("Очаг в " + nearest.pos().toShortString() + " переведён на фазу " + phase + " (" + Math.round(progress * 100) + "%)."), true);
        return 1;
    }

    private static int forceHeart(CommandSourceStack source) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        boolean placed = InfectionSpreadEngine.promoteAnchorToHeart(source.getLevel(), nearest.pos());
        if (!placed) {
            source.sendFailure(Component.literal("В " + nearest.pos().toShortString() + " нет обычного якоря для апгрейда (может, это уже Сердце)."));
            return 0;
        }
        OutbreakSavedData data = OutbreakSavedData.get(source.getLevel());
        // Testing override - it may add a heart even if one is already claimed, but it still has to
        // mark the slot taken, or the next natural phase-4 outbreak grows yet another one.
        data.claimHeart();
        data.updateOutbreak(nearest.withPhase(4).withPeakPhase(4).withInfectedBlockCount(InfectionPhases.minBlockCountForPhase(4)));
        source.sendSuccess(() -> Component.literal("Сердце выращено в " + nearest.pos().toShortString() + "."), true);
        return 1;
    }

    private static int removeNearest(CommandSourceStack source) {
        OutbreakRecord nearest = nearest(source);
        if (nearest == null) {
            source.sendFailure(Component.literal("Рядом нет очагов."));
            return 0;
        }
        OutbreakSavedData.get(source.getLevel()).removeOutbreak(nearest.id());
        source.sendSuccess(() -> Component.literal("Очаг в " + nearest.pos().toShortString() + " удалён из реестра (блоки остались — сноси вручную)."), true);
        return 1;
    }

    // ---- /lotus gland ... (Mossy Glands - see MossyGlandSpreadEngine) ----------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> glandCommands() {
        return Commands.literal("gland")
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawnGland(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> spawnGland(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                .then(Commands.literal("list").executes(ctx -> listGlands(ctx.getSource())));
    }

    private static int spawnGland(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        com.lotusblight.data.MossyGlandSavedData.get(level).registerGland(pos, level.getGameTime());
        source.sendSuccess(() -> Component.literal("Мшистая железа посажена в " + pos.toShortString() + "."), true);
        return 1;
    }

    private static int listGlands(CommandSourceStack source) {
        var glands = com.lotusblight.data.MossyGlandSavedData.get(source.getLevel()).asList();
        if (glands.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Мшистых желёз нет."), false);
            return 0;
        }
        for (var gland : glands) {
            source.sendSuccess(() -> Component.literal(gland.pos().toShortString() + " — обращено блоков: " + gland.convertedBlockCount()), false);
        }
        return glands.size();
    }

    /**
     * A SAFE stand-in for "speed up the whole game" (bug #17) - real tick-warp means re-entering
     * MinecraftServer's own tick loop from inside itself (risks corrupting whatever internal
     * iteration is already mid-flight), which isn't worth the risk for a testing convenience. This
     * just runs extra passes of our own spread engines back-to-back and advances the day/night
     * clock, instead of touching the server's actual tick loop.
     */
    private static int timewarp(CommandSourceStack source, int passes) {
        ServerLevel level = source.getLevel();
        level.setDayTime(level.getDayTime() + passes * 24000L / 500);
        InfectionSpreadEngine.forceTicks(level, passes);
        com.lotusblight.spread.MossyGlandSpreadEngine.forceTicks(level, passes);
        source.sendSuccess(() -> Component.literal("Прогнано " + passes + " проходов заражения (без реального ускорения тика сервера)."), true);
        return passes;
    }

    private static OutbreakRecord nearest(CommandSourceStack source) {
        return OutbreakSavedData.get(source.getLevel()).nearestOutbreak(BlockPos.containing(source.getPosition()), 4096.0, false);
    }

    // ---- /lotus branch ... -----------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> branchCommands() {
        return Commands.literal("branch")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> getBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("undecided").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_UNDECIDED)))
                                .then(Commands.literal("alliance").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_ALLIANCE)))
                                .then(Commands.literal("resistance").executes(ctx -> setBranch(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), LotusPlayerState.BRANCH_RESISTANCE)))));
    }

    private static int getBranch(CommandSourceStack source, ServerPlayer player) {
        int branch = LotusPlayerState.getDialogueBranch(player);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": " + branchName(branch)), false);
        return branch;
    }

    private static int setBranch(CommandSourceStack source, ServerPlayer player, int branch) {
        LotusPlayerState.forceDialogueBranch(player, branch);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerStateSyncPacket(branch, LotusPlayerState.hasFullMapVisibility(player), LotusPlayerState.hasHeardInnerVoice(player), LotusPlayerState.hasSeenGuardian(player), LotusPlayerState.getAllianceCleanseUses(player)));
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " -> " + branchName(branch) + " (это админ-оверрайд, обходит блокировку одноразового выбора)."), true);
        return 1;
    }

    private static String branchName(int branch) {
        return switch (branch) {
            case LotusPlayerState.BRANCH_ALLIANCE -> "Альянс";
            case LotusPlayerState.BRANCH_RESISTANCE -> "Война";
            default -> "Не выбрано";
        };
    }

    // ---- /lotus map ... ----------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mapCommands() {
        return Commands.literal("map")
                .then(Commands.literal("reveal")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("true").executes(ctx -> setReveal(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true)))
                                .then(Commands.literal("false").executes(ctx -> setReveal(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false)))));
    }

    private static int setReveal(CommandSourceStack source, ServerPlayer player, boolean reveal) {
        LotusPlayerState.setFullMapVisibility(player, reveal);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PlayerStateSyncPacket(LotusPlayerState.getDialogueBranch(player), reveal, LotusPlayerState.hasHeardInnerVoice(player), LotusPlayerState.hasSeenGuardian(player), LotusPlayerState.getAllianceCleanseUses(player)));
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": полная видимость карты = " + reveal), true);
        return 1;
    }

    // ---- /lotus world ... (world-wide infection totals) --------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> worldCommands() {
        return Commands.literal("world")
                .then(Commands.literal("status").executes(ctx -> worldStatus(ctx.getSource())))
                .then(Commands.literal("version").executes(ctx -> {
                    String report = com.lotusblight.world.WorldMigrations.status(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(report), false);
                    return 1;
                }))
                .then(Commands.literal("migrate").executes(ctx -> {
                    int ran = com.lotusblight.world.WorldMigrations.migrateNow(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(ran == 0 ? "Мир уже на актуальной версии (или мод в нём ещё ничего не создал)." : "Применено шагов: " + ran), true);
                    return 1;
                }));
    }

    private static int worldStatus(CommandSourceStack source) {
        long totalInfected = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (OutbreakRecord outbreak : OutbreakSavedData.get(level).allOutbreaks()) {
                totalInfected += outbreak.infectedBlockCount();
            }
        }
        long finalTotal = totalInfected;
        int reference = com.lotusblight.LotusConfig.WORLD_INFECTION_REFERENCE.get();
        float percent = reference == 0 ? 0f : (float) totalInfected / reference * 100f;
        source.sendSuccess(() -> Component.literal(String.format(
                "Всего заражено: %d / %d (%.2f%%).",
                finalTotal, reference, percent)), false);
        return 1;
    }

    // ---- /lotus meteorite ... (meteorite spread testing) --------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> meteoriteCommands() {
        return Commands.literal("meteorite")
                .then(Commands.literal("seed")
                        .executes(ctx -> meteoriteSeed(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> meteoriteSeed(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    private static int meteoriteSeed(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        level.setBlock(pos, ModBlocks.METEORITE_STONE.get().defaultBlockState(), 3);
        com.lotusblight.spread.MeteoriteSpreadEngine.seed(level, pos);
        source.sendSuccess(() -> Component.literal("Метеоритный спред посажен в " + pos.toShortString() + "."), true);
        return 1;
    }

    // ---- /lotus blackheart ... (BlackHeartManager testing) -------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> blackHeartCommands() {
        return Commands.literal("blackheart")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> blackHeartGet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 20))
                                        .executes(ctx -> blackHeartSet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static int blackHeartGet(CommandSourceStack source, ServerPlayer player) {
        int count = LotusPlayerState.getBlackHeartCount(player);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": чёрных сердец " + count), false);
        return count;
    }

    private static int blackHeartSet(CommandSourceStack source, ServerPlayer player, int count) {
        com.lotusblight.effect.BlackHeartManager.forceCount(player, count);
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " -> чёрных сердец " + count), true);
        return 1;
    }

    // ---- /lotus border ... (quarantine world border status) ------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> borderCommands() {
        return Commands.literal("border")
                .then(Commands.literal("status").executes(ctx -> borderStatus(ctx.getSource())));
    }

    private static int borderStatus(CommandSourceStack source) {
        var border = source.getServer().overworld().getWorldBorder();
        source.sendSuccess(() -> Component.literal(String.format(
                "Граница: размер %.0f, центр (%.0f, %.0f)",
                border.getSize(), border.getCenterX(), border.getCenterZ())), false);
        return 1;
    }

    // ---- /lotus reputation ... (per-faction standing, see LotusPlayerState#addReputation) -------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reputationCommands() {
        var builder = Commands.literal("reputation")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reputationGetAll(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
        for (com.lotusblight.data.Faction faction : com.lotusblight.data.Faction.values()) {
            builder.then(Commands.literal("set")
                    .then(Commands.literal(faction.name().toLowerCase(java.util.Locale.ROOT))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                            .executes(ctx -> reputationSet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), faction, IntegerArgumentType.getInteger(ctx, "value")))))));
        }
        return builder;
    }

    private static int reputationGetAll(CommandSourceStack source, ServerPlayer player) {
        StringBuilder sb = new StringBuilder(player.getGameProfile().getName()).append(": ");
        for (com.lotusblight.data.Faction faction : com.lotusblight.data.Faction.values()) {
            sb.append(faction.displayName()).append('=').append(LotusPlayerState.getReputation(player, faction)).append(' ');
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int reputationSet(CommandSourceStack source, ServerPlayer player, com.lotusblight.data.Faction faction, int value) {
        int newValue = LotusPlayerState.addReputation(player, faction, value - LotusPlayerState.getReputation(player, faction));
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " -> " + faction.displayName() + " = " + newValue), true);
        return newValue;
    }

    // ---- /lotus compat (what this mod changes in / uses from other installed mods) --------------

    private static int compatReport(CommandSourceStack source) {
        for (String line : com.lotusblight.compat.CompatHooks.report()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return com.lotusblight.compat.CompatHooks.ALL.size();
    }

    // ---- /lotus story: where the world is in the plot (see StoryState) ------------------------

    private static int storyInfo(CommandSourceStack source) {
        var st = com.lotusblight.story.StoryState.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Эпизод " + st.episode() + ", бит «" + st.beat() + "»; Лотос в мире: "
                + (com.lotusblight.story.StoryState.lotusShown(source.getServer()) ? "показан" : "скрыт")), false);
        return 1;
    }

    private static int storyBeats(CommandSourceStack source) {
        var cur = com.lotusblight.story.StoryState.get(source.getServer()).beat();
        StringBuilder sb = new StringBuilder();
        for (var b : com.lotusblight.story.Beats.all()) {
            sb.append("\n").append(b.id().equals(cur) ? "> " : "  ").append(b.episode()).append(": ").append(b.id()).append(" - ").append(b.title());
        }
        source.sendSuccess(() -> Component.literal("Биты сюжета:" + sb), false);
        return 1;
    }

    private static int storySetBeat(CommandSourceStack source, String id) {
        var beat = com.lotusblight.story.Beats.find(id);
        if (beat.isEmpty()) {
            source.sendFailure(Component.literal("Такого бита нет, список: /lotus story beats"));
            return 0;
        }
        com.lotusblight.story.StoryState.get(source.getServer()).setBeat(beat.get().episode(), id);
        source.sendSuccess(() -> Component.literal("Бит: " + beat.get().title()), true);
        return 1;
    }

    private static int storyNext(CommandSourceStack source) {
        var st = com.lotusblight.story.StoryState.get(source.getServer());
        var next = com.lotusblight.story.Beats.next(st.beat());
        if (next.isEmpty()) {
            source.sendFailure(Component.literal("Дальше сюжета нет: это последний написанный бит."));
            return 0;
        }
        st.setBeat(next.get().episode(), next.get().id());
        source.sendSuccess(() -> Component.literal("Бит: " + next.get().title()), true);
        return 1;
    }

    private static int storyDream(CommandSourceStack source, boolean enter) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean ok = enter ? com.lotusblight.story.DreamWorld.enter(player) : com.lotusblight.story.DreamWorld.wake(player);
        if (!ok) {
            source.sendFailure(Component.literal("Измерение снов не найдено."));
            return 0;
        }
        return 1;
    }

    private static int storyDiary(CommandSourceStack source, String action, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var st = com.lotusblight.story.StoryState.get(source.getServer());
        switch (action) {
            case "add" -> {
                if (!id.matches("[a-z0-9_]{1,48}")) {
                    source.sendFailure(Component.literal("Имя записи: только a-z, 0-9 и _."));
                    return 0;
                }
                boolean added = com.lotusblight.story.Diary.add(player, id);
                source.sendSuccess(() -> Component.literal(added ? "Запись добавлена: " + id : "Такая запись уже есть."), true);
            }
            case "clear" -> {
                st.clearDiary();
                com.lotusblight.story.Diary.sync(player);
                source.sendSuccess(() -> Component.literal("Дневник очищен."), true);
            }
            default -> source.sendSuccess(() -> Component.literal("Записи дневника: " + st.diary()), false);
        }
        return 1;
    }

    private static int storyQuest(CommandSourceStack source, String action, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var st = com.lotusblight.story.StoryState.get(source.getServer());
        if (id != null && !id.matches("[a-z0-9_]{1,48}")) {
            source.sendFailure(Component.literal("Имя задания: только a-z, 0-9 и _."));
            return 0;
        }
        switch (action) {
            case "start" -> {
                boolean ok = com.lotusblight.story.Quests.start(player, id);
                source.sendSuccess(() -> Component.literal(ok ? "Задание выдано: " + id : "Такое задание уже есть."), true);
            }
            case "done" -> {
                boolean ok = com.lotusblight.story.Quests.complete(player, id);
                source.sendSuccess(() -> Component.literal(ok ? "Задание выполнено: " + id : "Задание не выдано или уже выполнено."), true);
            }
            case "clear" -> {
                st.clearQuests();
                com.lotusblight.story.Quests.sync(player);
                source.sendSuccess(() -> Component.literal("Задания очищены."), true);
            }
            default -> source.sendSuccess(() -> Component.literal("Задания: " + st.quests()), false);
        }
        return 1;
    }

    private static int storyDialogue(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new com.lotusblight.map.OpenDialoguePacket(id));
        return 1;
    }

    private static int storyLotus(CommandSourceStack source, boolean shown) {
        com.lotusblight.story.StoryState.get(source.getServer()).showLotus(shown);
        source.sendSuccess(() -> Component.literal(shown ? "Лотос теперь появляется в мире." : "Лотос скрыт: новые очаги не появляются и не растут."), true);
        return 1;
    }

    // ---- /lotus book (opens the command reference GUI, see LotusCommandBookScreen) -------------

    private static int openCommandBook(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.lotusblight.map.OpenCommandBookPacket());
        return 1;
    }
}
