package com.lotusblight.entity;

import com.lotusblight.LotusBlight;
import com.lotusblight.advancement.HonchoTripPleasedTrigger;
import com.lotusblight.advancement.HonchoTripRudeTrigger;
import com.lotusblight.data.HonchoSavedData;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.ShowHonchoAssistantPacket;
import com.lotusblight.map.ShowHonchoMeetingPacket;
import com.lotusblight.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "у нас нет норм встречи с хончо...на войне" - the rare trip-meeting: while walking, war-branch
 * players who've lived through StarFall have a tiny chance to stumble. The HUD goes away, the player
 * pushes up off the ground, sees feet next to them and looks up - Honcho is already there, offering
 * a hand (see HonchoMeetingCutscene on the client).
 *
 * "Принять руку?" - yes: he's glad, pats the player's head and from then on sticks with them, and -
 * still inside the same cutscene, not as a second scene - tells his story and, on his knees in
 * prayer, asks to become the player's assistant. No: he prays and asks "Точно?" - yes there still
 * takes his hand, no again and he lights up yellow and leaves this world for good. Fires at most
 * once per player.
 *
 * Every step is driven from here; the client only shows it and sends the two answers, which are only
 * accepted at the matching stage.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HonchoMeetingManager {
    /** "мизерным шансом" - roughly once every 10 minutes of qualifying walking on average at 20 tps. */
    private static final int TRIP_CHANCE = 12000;
    private static final double MIN_HORIZONTAL_SPEED_SQ = 0.005 * 0.005;
    private static final double STAND_DISTANCE = 1.4;
    private static final double ABORT_DISTANCE = 24.0;

    private static final int HAPPY_TICKS = 20;
    private static final int PAT_TICKS = 50;
    private static final int LEAVE_TICKS = 50;

    public static final int CHOICE_ACCEPT = 0;
    public static final int CHOICE_DECLINE = 1;

    private static final DustParticleOptions YELLOW_LIGHT = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.3f), 1.4f);

    private enum Stage { OFFERED, CONFIRMING, ACCEPTED, STORY, LEAVING }

    private static final class Session {
        final UUID playerId;
        final UUID honchoId;
        Stage stage = Stage.OFFERED;
        int stageTicks;

        Session(UUID playerId, UUID honchoId) {
            this.playerId = playerId;
            this.honchoId = honchoId;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HonchoMeetingManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!player.onGround()) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_RESISTANCE) return;
        // With StarFall switched off Honcho is no longer tied to it and can be met anywhere.
        if (com.lotusblight.LotusConfig.STARFALL_ENABLED.get() && !LotusPlayerState.hasSeenStarFall(player)) return;
        if (LotusPlayerState.hasMetHoncho(player) || LotusPlayerState.isHonchoMeetingPending(player)) return;
        if (isBusyElsewhere(player)) return;

        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        if (dx * dx + dz * dz < MIN_HORIZONTAL_SPEED_SQ) return;

        if (player.getRandom().nextInt(TRIP_CHANCE) != 0) return;
        startMeeting(player);
    }

    /** In the middle of the chase or the traitor fight - tripping there would pull the player out of it. */
    private static boolean isBusyElsewhere(ServerPlayer player) {
        var chase = com.lotusblight.escape.LotusChaseEvent.get();
        return (chase != null && chase.isRunner(player.getUUID()))
                || com.lotusblight.boss.TraitorBossFight.isInFight(player.getUUID());
    }

    /**
     * /lotus honcho meeting - plays the trip right now, skipping the chance roll and the branch /
     * StarFall checks, and forgets an earlier meeting so it can be replayed. Returns why it couldn't
     * start, or null if it did.
     */
    public static String forceMeeting(ServerPlayer player) {
        HonchoSavedData data = HonchoSavedData.get(player.server.overworld());
        if (data.isGone()) return "Хончо ушёл из этого мира (/lotus honcho reset world вернёт его).";
        cancel(player);
        if (data.honchoId() != null && isHonchoBusy(data.honchoId())) return "Хончо сейчас в сцене с другим игроком.";
        LotusPlayerState.resetHonchoMeeting(player);
        return startMeeting(player) ? null : "Не удалось поставить Хончо рядом.";
    }

    /** Drops this player's scene without any outcome - nothing is marked, Honcho just stops waiting. */
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        HonchoEntity honcho = findHoncho(player, session);
        if (honcho != null) honcho.endMeeting();
        LotusPlayerState.clearHonchoMeetingPending(player);
        stopClientScene(player);
    }

    private static void stopClientScene(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoMeetingPacket(ShowHonchoMeetingPacket.STOP));
    }

    public static boolean isInMeeting(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** The tracked Honcho if he's loaded anywhere right now, otherwise null. */
    public static HonchoEntity findLoadedHoncho(MinecraftServer server) {
        UUID id = HonchoSavedData.get(server.overworld()).honchoId();
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof HonchoEntity honcho && honcho.isAlive()) return honcho;
        }
        return null;
    }

    /** /lotus honcho spawn - the same "move the real one here or take over his identity" the meeting uses. */
    public static HonchoEntity summonTo(ServerPlayer player) {
        HonchoSavedData data = HonchoSavedData.get(player.server.overworld());
        if (data.isGone() || (data.honchoId() != null && isHonchoBusy(data.honchoId()))) return null;
        return bringHoncho(player, data);
    }

    /** Brings Honcho over and starts the scene. Does nothing (and leaves the player free to trip again later) if he's gone or already busy with someone else. */
    private static boolean startMeeting(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        HonchoSavedData data = HonchoSavedData.get(player.server.overworld());
        if (data.isGone()) return false;
        if (data.honchoId() != null && isHonchoBusy(data.honchoId())) return false;

        HonchoEntity honcho = bringHoncho(player, data);
        if (honcho == null) return false;

        LotusPlayerState.markHonchoMeetingPending(player);
        honcho.beginMeeting(player);
        honcho.setScene(HonchoEntity.SCENE_OFFER_HAND);
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), honcho.getUUID()));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoMeetingPacket(honcho.getId()));
        return true;
    }

    private static boolean isHonchoBusy(UUID honchoId) {
        for (Session session : SESSIONS.values()) {
            if (session.honchoId.equals(honchoId)) return true;
        }
        return false;
    }

    /**
     * Honcho is one per world and can be anywhere - the real one is moved in front of the player if
     * he's loaded in the same dimension, otherwise a fresh one takes over his identity (the old copy
     * is dropped whenever its chunk loads again, see onEntityJoin).
     */
    private static HonchoEntity bringHoncho(ServerPlayer player, HonchoSavedData data) {
        ServerLevel level = player.serverLevel();
        HonchoEntity honcho = null;
        if (data.honchoId() != null) {
            for (ServerLevel candidate : player.server.getAllLevels()) {
                if (candidate.getEntity(data.honchoId()) instanceof HonchoEntity found && found.isAlive()) {
                    honcho = found;
                    break;
                }
            }
        } else {
            // Worlds from before the id was tracked - adopt a Honcho that's already around.
            var nearby = level.getEntities(ModEntities.HONCHO.get(), player.getBoundingBox().inflate(256.0), HonchoEntity::isAlive);
            if (!nearby.isEmpty()) {
                honcho = nearby.get(0);
                data.setHonchoId(honcho.getUUID());
            }
        }
        if (honcho != null && honcho.level() != level) {
            honcho.discard();
            honcho = null;
        }

        boolean fresh = honcho == null;
        if (fresh) {
            honcho = ModEntities.HONCHO.get().create(level);
            if (honcho == null) return null;
            data.setHonchoId(honcho.getUUID());
        }

        Vec3 spot = standingSpot(player, honcho);
        float yaw = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * (180.0 / Math.PI)) - 90.0f;
        honcho.moveTo(spot.x, spot.y, spot.z, yaw, 0.0f);
        honcho.setYHeadRot(yaw);
        honcho.setYBodyRot(yaw);
        honcho.getNavigation().stop();
        if (fresh && !level.addFreshEntity(honcho)) return null;
        return honcho;
    }

    /** Right in front of the player if there's room, otherwise to a side or behind, otherwise on the player's own spot. */
    private static Vec3 standingSpot(ServerPlayer player, HonchoEntity honcho) {
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        Vec3[] directions = {forward, forward.yRot(Mth.HALF_PI), forward.yRot(-Mth.HALF_PI), forward.scale(-1)};
        for (Vec3 dir : directions) {
            Vec3 candidate = player.position().add(dir.scale(STAND_DISTANCE));
            honcho.setPos(candidate.x, candidate.y, candidate.z);
            if (player.serverLevel().hasChunkAt(honcho.blockPosition()) && player.serverLevel().noCollision(honcho)) {
                return candidate;
            }
        }
        return player.position();
    }

    public static void handleChoice(ServerPlayer player, int choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        HonchoEntity honcho = findHoncho(player, session);
        if (honcho == null) return;

        if (choice == CHOICE_ACCEPT && (session.stage == Stage.OFFERED || session.stage == Stage.CONFIRMING)) {
            setStage(session, Stage.ACCEPTED);
            LotusPlayerState.markMetHoncho(player);
            HonchoTripPleasedTrigger.INSTANCE.trigger(player);
            honcho.setScene(HonchoEntity.SCENE_HAPPY);
            HonchoSpeech.say(player, "— Вот так. Осторожнее под ногами.");
        } else if (choice == CHOICE_DECLINE && session.stage == Stage.OFFERED) {
            setStage(session, Stage.CONFIRMING);
            honcho.setScene(HonchoEntity.SCENE_PRAY);
        } else if (choice == CHOICE_DECLINE && session.stage == Stage.CONFIRMING) {
            setStage(session, Stage.LEAVING);
            LotusPlayerState.markMetHoncho(player);
            HonchoTripRudeTrigger.INSTANCE.trigger(player);
            HonchoSpeech.say(player, "— ...Ладно. Сам, так сам.");
            honcho.addEffect(new MobEffectInstance(MobEffects.GLOWING, LEAVE_TICKS + 20, 0, false, false));
            honcho.level().playSound(null, honcho.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0f, 1.3f);
        }
    }

    private static void setStage(Session session, Stage stage) {
        session.stage = stage;
        session.stageTicks = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            tickSession(server, session);
        }
    }

    private static void tickSession(MinecraftServer server, Session session) {
        ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
        HonchoEntity honcho = player == null ? null : findHoncho(player, session);
        if (player == null || !player.isAlive() || honcho == null
                || honcho.distanceTo(player) > ABORT_DISTANCE) {
            abort(session, honcho, player);
            return;
        }
        session.stageTicks++;

        if (session.stage == Stage.ACCEPTED) {
            if (session.stageTicks == HAPPY_TICKS) {
                honcho.setScene(HonchoEntity.SCENE_PAT);
            } else if (session.stageTicks >= HAPPY_TICKS + PAT_TICKS) {
                if (!LotusPlayerState.hasAskedHonchoAssistant(player)) {
                    // "тот самый диалог" - his story, in the same cutscene: he stays put facing the
                    // player until the plea is answered (see onAssistantAnswered).
                    setStage(session, Stage.STORY);
                    honcho.setScene(HonchoEntity.SCENE_NONE);
                    LotusPlayerState.markAskedHonchoAssistant(player);
                    LotusPlayerState.setHonchoAssistantPending(player, true);
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoAssistantPacket());
                } else {
                    finishWithHim(session, honcho, player);
                    stopClientScene(player);
                }
            }
        } else if (session.stage == Stage.LEAVING) {
            if (honcho.level() instanceof ServerLevel level) {
                level.sendParticles(YELLOW_LIGHT, honcho.getX(), honcho.getY() + 1.0, honcho.getZ(), 6, 0.35, 0.8, 0.35, 0.02);
                level.sendParticles(ParticleTypes.END_ROD, honcho.getX(), honcho.getY() + 1.0, honcho.getZ(), 2, 0.3, 0.7, 0.3, 0.01);
            }
            if (session.stageTicks >= LEAVE_TICKS) {
                SESSIONS.remove(session.playerId);
                HonchoSavedData.get(server.overworld()).markGone();
                honcho.discard();
            }
        }
    }

    /** The story reached his plea - he goes down on his knees and prays for the answer. */
    public static void onStoryQuestion(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.stage != Stage.STORY) return;
        HonchoEntity honcho = findHoncho(player, session);
        if (honcho != null) honcho.setScene(HonchoEntity.SCENE_PRAY);
    }

    /** Called by HonchoAssistantChoicePacket once the plea is answered - closes a meeting still in its story stage. */
    public static void onAssistantAnswered(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.stage != Stage.STORY) return;
        finishWithHim(session, findHoncho(player, session), player);
    }

    /** Accepted meeting over: he stops holding still and from now on sticks with this player. */
    private static void finishWithHim(Session session, HonchoEntity honcho, ServerPlayer player) {
        SESSIONS.remove(session.playerId);
        if (honcho != null) {
            honcho.endMeeting();
            honcho.followPlayer(player);
        }
    }

    private static HonchoEntity findHoncho(ServerPlayer player, Session session) {
        return player.serverLevel().getEntity(session.honchoId) instanceof HonchoEntity honcho && honcho.isAlive() ? honcho : null;
    }

    /** The player left, died or wandered off before answering - drop the scene and let the trip happen again another time. */
    private static void abort(Session session, HonchoEntity honcho, ServerPlayer player) {
        SESSIONS.remove(session.playerId);
        if (honcho != null && session.stage != Stage.LEAVING) honcho.endMeeting();
        if (honcho != null && player != null && (session.stage == Stage.ACCEPTED || session.stage == Stage.STORY)) honcho.followPlayer(player);
        if (player != null && session.stage == Stage.STORY) {
            // The cutscene can't go on without him - the plea is still asked, just outside it.
            stopClientScene(player);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoAssistantPacket());
        }
        if (session.stage == Stage.LEAVING && honcho != null && player != null) {
            // Already refused twice - finish leaving even if the player didn't stay to watch.
            HonchoSavedData.get(player.server.overworld()).markGone();
            honcho.discard();
        }
        if (player != null && session.stage != Stage.ACCEPTED && session.stage != Stage.LEAVING) {
            LotusPlayerState.clearHonchoMeetingPending(player);
            // Otherwise the client sits on "Принять руку?" forever - Esc is off and its answers
            // no longer match any session.
            stopClientScene(player);
        }
    }

    /**
     * The player can't move or fight back during the scene and the choice window doesn't pause the
     * game - a zombie wandering by could kill them mid-answer. Nothing hurts them until it's over.
     */
    @SubscribeEvent
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        HonchoEntity honcho = findHoncho(player, session);
        if (session.stage == Stage.LEAVING) {
            HonchoSavedData.get(player.server.overworld()).markGone();
            if (honcho != null) honcho.discard();
        } else if (honcho != null) {
            honcho.endMeeting();
            if (session.stage == Stage.ACCEPTED || session.stage == Stage.STORY) honcho.followPlayer(player);
        }
        // An unanswered scene keeps its pending flag, so it plays again on the next login (the
        // story's plea included - HonchoAssistantPending re-sends it as the ordinary overlay).
    }

    /**
     * The assistant question waits for an answer that only comes from the client; so does an
     * unfinished meeting. Logging out with either still open used to leave them stuck forever.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (LotusPlayerState.isHonchoMeetingPending(player) && !LotusPlayerState.hasMetHoncho(player)) {
            LotusPlayerState.clearHonchoMeetingPending(player);
            startMeeting(player);
        }
        if (LotusPlayerState.isHonchoAssistantPending(player)) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowHonchoAssistantPacket());
        }
    }

    /** Only the tracked Honcho may exist: an older copy loading back in, or any Honcho after he left for good, is dropped. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof HonchoEntity honcho)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        HonchoSavedData data = HonchoSavedData.get(level.getServer().overworld());
        if (data.isGone()) {
            event.setCanceled(true);
        } else if (data.honchoId() == null) {
            data.setHonchoId(honcho.getUUID());
        } else if (!data.honchoId().equals(honcho.getUUID())) {
            event.setCanceled(true);
        }
    }
}
