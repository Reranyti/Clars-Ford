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
                .then(chaseCommands())
                .then(labProtoCommands())
                .then(starFallCommands())
                .then(worldCommands())
                .then(meteoriteCommands())
                .then(blackHeartCommands())
                .then(borderCommands())
                .then(reputationCommands())
                .then(honchoCommands())
                .then(Commands.literal("compat").executes(ctx -> compatReport(ctx.getSource())))
                .then(Commands.literal("book").executes(ctx -> openCommandBook(ctx.getSource()))));
    }

    // ---- /lotus event <line> <event> [player] [force]: running the story events by hand --------------
    // One explicit branch per event on purpose (no registry, no generic lookup): a failing command points straight at its event.

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> eventCommands() {
        return Commands.literal("event")
                .then(Commands.literal("war").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Война / заготовка", com.lotusblight.event.WarEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Война / заготовка", com.lotusblight.event.WarEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Война / заготовка", com.lotusblight.event.WarEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))))
                .then(Commands.literal("alliance").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Альянс / заготовка", com.lotusblight.event.AllianceEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Альянс / заготовка", com.lotusblight.event.AllianceEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Альянс / заготовка", com.lotusblight.event.AllianceEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))))
                .then(Commands.literal("neutral").then(Commands.literal("leafiles")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Нейтральная / Лейфайлс", com.lotusblight.event.NeutralEvents.leafiles(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Нейтральная / Лейфайлс", com.lotusblight.event.NeutralEvents.leafiles(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Нейтральная / Лейфайлс", com.lotusblight.event.NeutralEvents.leafiles(EntityArgument.getPlayer(ctx, "player"), true)))))))
                .then(Commands.literal("guiding").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Путеводная / заготовка", com.lotusblight.event.GuidingEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Путеводная / заготовка", com.lotusblight.event.GuidingEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Путеводная / заготовка", com.lotusblight.event.GuidingEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))))
                .then(Commands.literal("cursed").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Проклятая / заготовка", com.lotusblight.event.CursedEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))))
                .then(Commands.literal("honcho").then(Commands.literal("skeleton")
                        .executes(ctx -> reportEvent(ctx.getSource(), "Хончо / заготовка", com.lotusblight.event.HonchoEvents.skeleton(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reportEvent(ctx.getSource(), "Хончо / заготовка", com.lotusblight.event.HonchoEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), false)))
                                .then(Commands.literal("force")
                                        .executes(ctx -> reportEvent(ctx.getSource(), "Хончо / заготовка", com.lotusblight.event.HonchoEvents.skeleton(EntityArgument.getPlayer(ctx, "player"), true)))))));
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
                .then(Commands.literal("return").executes(ctx -> {
                    // The scene after the fight: the portal, Honcho thrown out of it, the hand, going along.
                    com.lotusblight.entity.HonchoReturnScene.begin(ctx.getSource().getPlayerOrException());
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

    // ---- /lotus chase ... ("Побег от лотоса" testing) -----------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> chaseCommands() {
        return Commands.literal("chase")
                .then(Commands.literal("unlock").executes(ctx -> chaseUnlock(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> chaseStatus(ctx.getSource())))
                .then(Commands.literal("tp").executes(ctx -> chaseTeleport(ctx.getSource())));
    }

    private static int chaseUnlock(CommandSourceStack source) {
        com.lotusblight.escape.LotusChaseEvent.get().forceUnlock(source.getServer().overworld());
        source.sendSuccess(() -> Component.literal("Лаборатория побега разблокирована (обходит порог заражения)."), true);
        return 1;
    }

    private static int chaseStatus(CommandSourceStack source) {
        String report = com.lotusblight.escape.LotusChaseEvent.get().statusReport(source.getServer().overworld());
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    private static int chaseTeleport(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos entrance = com.lotusblight.escape.LotusChaseEvent.get().labEntrance(source.getServer().overworld());
        if (entrance == null) {
            source.sendFailure(Component.literal("Чанк лаборатории ещё не загружен - попробуй снова, когда кто-нибудь окажется рядом."));
            return 0;
        }
        source.getPlayerOrException().teleportTo(entrance.getX() - 6 + 0.5, entrance.getY() + 1, entrance.getZ() + 0.5);
        source.sendSuccess(() -> Component.literal("Телепортирован ко входу в лабораторию: " + entrance.toShortString()), true);
        return 1;
    }

    // ---- /lotus labproto ... (prototype of the redesigned escape lab, see escape/LabProto) -------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> labProtoCommands() {
        return Commands.literal("labproto")
                .then(Commands.literal("build").executes(ctx -> labProto(ctx.getSource(), "build")))
                .then(Commands.literal("start").executes(ctx -> labProto(ctx.getSource(), "start")))
                .then(Commands.literal("stop").executes(ctx -> labProto(ctx.getSource(), "stop")))
                .then(Commands.literal("status").executes(ctx -> labProto(ctx.getSource(), "status")));
    }

    private static int labProto(CommandSourceStack source, String action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.lotusblight.escape.LabProto proto = com.lotusblight.escape.LabProto.get();
        if (proto == null) {
            source.sendFailure(Component.literal("Прототип не подключён (только на сервере мира)."));
            return 0;
        }
        String result = switch (action) {
            case "build" -> proto.build(source.getPlayerOrException());
            case "start" -> proto.start(source.getPlayerOrException());
            case "stop" -> proto.stop();
            default -> proto.status();
        };
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    // ---- /lotus starfall ... (testing - real trigger condition not decided yet) ---------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> starFallCommands() {
        return Commands.literal("starfall")
                .then(Commands.argument("player", EntityArgument.player())
                        // Same mapping as StarFallEvent: a war-branch player gets Star Light's friendly
                        // script (ShowStarFallPacket's allianceBranch=true), an alliance player the hostile one.
                        .then(Commands.literal("war").executes(ctx -> starFallTrigger(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true)))
                        .then(Commands.literal("alliance").executes(ctx -> starFallTrigger(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))));
    }

    private static int starFallTrigger(CommandSourceStack source, ServerPlayer player, boolean allianceBranch) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new com.lotusblight.map.ShowStarFallPacket(allianceBranch));
        if (!allianceBranch) com.lotusblight.escape.StarFallEvent.beginWarScene(player);
        // Real comets from Ex Meteor Shower, in the same spread-out waves the real trigger uses.
        com.lotusblight.escape.StarFallEvent.startShower((net.minecraft.server.level.ServerLevel) player.level());
        source.sendSuccess(() -> Component.literal("StarFall (" + (allianceBranch ? "война" : "альянс") + ") запущен для " + player.getGameProfile().getName()), true);
        return 1;
    }

    // ---- /lotus world ... (world-wide infection totals) --------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> worldCommands() {
        return Commands.literal("world")
                .then(Commands.literal("status").executes(ctx -> worldStatus(ctx.getSource())));
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
        float chaseAt = com.lotusblight.escape.LotusChaseEvent.TRIGGER_FRACTION * 100f;
        float starFallAt = com.lotusblight.escape.StarFallEvent.TRIGGER_FRACTION * 100f;
        source.sendSuccess(() -> Component.literal(String.format(
                "Всего заражено: %d / %d (%.2f%%). Побег открывается на %.0f%%, StarFall на %.0f%%.",
                finalTotal, reference, percent, chaseAt, starFallAt)), false);
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

    // ---- /lotus honcho ... (Honcho testing - see HonchoMeetingManager) -------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> honchoCommands() {
        var scene = Commands.literal("scene");
        String[] sceneNames = {"none", "offer", "happy", "pat", "pray"};
        int[] sceneIds = {com.lotusblight.entity.HonchoEntity.SCENE_NONE, com.lotusblight.entity.HonchoEntity.SCENE_OFFER_HAND,
                com.lotusblight.entity.HonchoEntity.SCENE_HAPPY, com.lotusblight.entity.HonchoEntity.SCENE_PAT,
                com.lotusblight.entity.HonchoEntity.SCENE_PRAY};
        for (int i = 0; i < sceneNames.length; i++) {
            String name = sceneNames[i];
            int id = sceneIds[i];
            scene.then(Commands.literal(name).executes(ctx -> honchoScene(ctx.getSource(), name, id)));
        }
        return Commands.literal("honcho")
                .then(Commands.literal("info")
                        .executes(ctx -> honchoInfo(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> honchoInfo(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("spawn").executes(ctx -> honchoSpawn(ctx.getSource())))
                .then(Commands.literal("tp").executes(ctx -> honchoTeleport(ctx.getSource())))
                .then(Commands.literal("meeting")
                        .executes(ctx -> honchoMeeting(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> honchoMeeting(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("cancel")
                        .executes(ctx -> honchoCancel(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> honchoCancel(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("assistant")
                        .executes(ctx -> honchoAssistant(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> honchoAssistant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(scene)
                .then(Commands.literal("reset")
                        .then(Commands.literal("world").executes(ctx -> honchoResetWorld(ctx.getSource())))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> honchoResetPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int honchoInfo(CommandSourceStack source, ServerPlayer player) {
        var data = com.lotusblight.data.HonchoSavedData.get(source.getServer().overworld());
        var honcho = com.lotusblight.entity.HonchoMeetingManager.findLoadedHoncho(source.getServer());
        String where = honcho == null
                ? (data.honchoId() == null ? "нет" : "не загружен")
                : honcho.blockPosition().toShortString() + " в " + honcho.level().dimension().location()
                        + ", здоровье " + Math.round(honcho.getHealth()) + "/" + Math.round(honcho.getMaxHealth());
        source.sendSuccess(() -> Component.literal(String.format("Мир: появлялся=%b, ушёл навсегда=%b, Хончо: %s",
                data.isSpawned(), data.isGone(), where)), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "%s: ветка=%s, StarFall=%b, встреча=%b (ждёт=%b, идёт сейчас=%b), вопрос помощника=%b (ждёт ответа=%b), флаконов=%d, ближе=%b",
                player.getGameProfile().getName(), branchName(LotusPlayerState.getDialogueBranch(player)),
                LotusPlayerState.hasSeenStarFall(player), LotusPlayerState.hasMetHoncho(player),
                LotusPlayerState.isHonchoMeetingPending(player), com.lotusblight.entity.HonchoMeetingManager.isInMeeting(player),
                LotusPlayerState.hasAskedHonchoAssistant(player), LotusPlayerState.isHonchoAssistantPending(player),
                LotusPlayerState.getHonchoDependencyCount(player), LotusPlayerState.isHonchoCloser(player))), false);
        return 1;
    }

    private static int honchoSpawn(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var honcho = com.lotusblight.entity.HonchoMeetingManager.summonTo(player);
        if (honcho == null) {
            source.sendFailure(Component.literal("Не вышло: Хончо ушёл навсегда или сейчас в сцене (см. /lotus honcho info)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Хончо стоит рядом: " + honcho.blockPosition().toShortString()), true);
        return 1;
    }

    private static int honchoTeleport(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var honcho = com.lotusblight.entity.HonchoMeetingManager.findLoadedHoncho(source.getServer());
        if (honcho == null) {
            source.sendFailure(Component.literal("Хончо сейчас не загружен ни в одном измерении."));
            return 0;
        }
        player.teleportTo((ServerLevel) honcho.level(), honcho.getX(), honcho.getY(), honcho.getZ(), player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Телепортирован к Хончо: " + honcho.blockPosition().toShortString()), true);
        return 1;
    }

    private static int honchoMeeting(CommandSourceStack source, ServerPlayer player) {
        String error = com.lotusblight.entity.HonchoMeetingManager.forceMeeting(player);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Встреча с Хончо запущена для " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int honchoCancel(CommandSourceStack source, ServerPlayer player) {
        if (!com.lotusblight.entity.HonchoMeetingManager.isInMeeting(player)) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + " сейчас не во встрече."));
            return 0;
        }
        com.lotusblight.entity.HonchoMeetingManager.cancel(player);
        source.sendSuccess(() -> Component.literal("Встреча прервана для " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int honchoAssistant(CommandSourceStack source, ServerPlayer player) {
        LotusPlayerState.markAskedHonchoAssistant(player);
        LotusPlayerState.setHonchoAssistantPending(player, true);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new com.lotusblight.map.ShowHonchoAssistantPacket());
        source.sendSuccess(() -> Component.literal("Сцена помощника показана " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int honchoScene(CommandSourceStack source, String name, int scene) {
        var honcho = com.lotusblight.entity.HonchoMeetingManager.findLoadedHoncho(source.getServer());
        if (honcho == null) {
            source.sendFailure(Component.literal("Хончо сейчас не загружен (/lotus honcho spawn)."));
            return 0;
        }
        if (honcho.isInMeeting()) {
            source.sendFailure(Component.literal("Хончо сейчас во встрече - анимацию ведёт сцена."));
            return 0;
        }
        honcho.setScene(scene);
        source.sendSuccess(() -> Component.literal("Анимация Хончо: " + name), false);
        return 1;
    }

    private static int honchoResetWorld(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            com.lotusblight.entity.HonchoMeetingManager.cancel(player);
        }
        var honcho = com.lotusblight.entity.HonchoMeetingManager.findLoadedHoncho(source.getServer());
        if (honcho != null) honcho.discard();
        com.lotusblight.entity.HonchoReturnScene.cancelAll(source.getServer());
        com.lotusblight.data.HonchoSavedData.get(source.getServer().overworld()).reset();
        source.sendSuccess(() -> Component.literal("Хончо забыт миром: следующий StarFall или встреча создадут его заново."), true);
        return 1;
    }

    private static int honchoResetPlayer(CommandSourceStack source, ServerPlayer player) {
        com.lotusblight.entity.HonchoMeetingManager.cancel(player);
        LotusPlayerState.resetHonchoProgress(player);
        com.lotusblight.entity.HonchoRewardManager.refresh(player);
        source.sendSuccess(() -> Component.literal("Прогресс с Хончо сброшен для " + player.getGameProfile().getName()), true);
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
